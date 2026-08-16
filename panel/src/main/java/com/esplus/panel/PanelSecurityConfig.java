package com.esplus.panel;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class PanelSecurityConfig {

    @Bean
    SecurityFilterChain filterChain(
            HttpSecurity http,
            AuthenticationSuccessHandler successHandler,
            AuthenticationFailureHandler failureHandler,
            PanelMfaFilter mfaFilter
    ) throws Exception {
        http.authorizeHttpRequests(auth -> auth
                        .requestMatchers("/css/**", "/js/**", "/assets/**", "/login", "/login/mfa",
                                "/api/auth/login", "/api/auth/mfa", "/index.html", "/").permitAll()
                        .requestMatchers("/api/ops/**").permitAll()
                        .requestMatchers("/api/auth/**").authenticated()
                        // SPA shell routes — any authenticated user can load the page;
                        // fine-grained authorization is enforced at the API layer.
                        .requestMatchers(HttpMethod.GET,
                                "/players", "/players/**", "/bans", "/whitelist",
                                "/messages", "/messages/**", "/world/**", "/gamerules",
                                "/entities", "/entities/**", "/items", "/items/**",
                                "/search", "/admins", "/admins/**",
                                "/security/**", "/center", "/access/**",
                                "/scoreboard", "/scoreboard/**",
                                "/console", "/remote", "/system/**",
                                "/automation", "/automation/**",
                                "/diag/**", "/trace/**", "/incident/**",
                                "/status", "/status/**").authenticated()
                        .requestMatchers("/setup/**").hasRole("ADMIN")
                        .requestMatchers("/console/**", "/api/console").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/maintenance/**", "/api/broadcast").hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/automation/**").hasAnyRole("ADMIN", "MODERATOR")
                .requestMatchers(HttpMethod.GET, "/api/automation/**").hasAnyRole("ADMIN", "MODERATOR", "VIEWER")
                        .requestMatchers(HttpMethod.POST, "/api/players/**").hasAnyRole("ADMIN", "MODERATOR")
                        .requestMatchers(HttpMethod.POST, "/api/admins/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/players/**", "/messages/**", "/whitelist/**", "/bans/**")
                        .hasAnyRole("ADMIN", "MODERATOR")
                        .requestMatchers(HttpMethod.POST, "/access/ops/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/world/**", "/entities/**", "/scoreboard/**", "/access/**")
                        .hasAnyRole("ADMIN", "MODERATOR")
                        .requestMatchers(HttpMethod.POST, "/gamerules/**", "/items/**", "/system/**")
                        .hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/security/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/audit/cleanup").hasRole("ADMIN")
                        .requestMatchers("/security/approvals/**", "/security/snapshots/**", "/security/config-history/**")
                        .hasRole("ADMIN")
                        .requestMatchers("/remote/**", "/system/**").hasAnyRole("ADMIN", "MODERATOR")
                        .requestMatchers("/gamerules/**", "/items/**").hasAnyRole("ADMIN", "MODERATOR", "VIEWER")
                        .anyRequest().authenticated())
                .formLogin(form -> form
                        .loginPage("/login")
                        .successHandler(successHandler)
                        .failureHandler(failureHandler)
                        .permitAll())
                .logout(Customizer.withDefaults())
                // CSRF: Cookie-to-Header 模式，默认只对 state-changing 方法（POST/PUT/DELETE/PATCH）
                // 生效；ops API 走独立 token 认证、auth 走表单登录，均为无 cookie 状态变更，
                // 其他 /api/** 必须由 JS 读取 XSRF-TOKEN cookie 后放入 X-XSRF-TOKEN 头。
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .ignoringRequestMatchers("/api/ops/**", "/api/auth/**"))
                .addFilterAfter(mfaFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    AuthenticationSuccessHandler successHandler(LoginAttemptService attempts, PanelMfaService mfa) {
        return (request, response, authentication) -> {
            attempts.onSuccess(authentication.getName());
            if (mfa.isEnabled(authentication.getName())) {
                request.getSession(true).setAttribute(PanelMfaFilter.MFA_PENDING, authentication.getName());
                request.getSession().removeAttribute(PanelMfaFilter.MFA_OK);
                response.sendRedirect("/login/mfa");
                return;
            }
            response.sendRedirect("/");
        };
    }

    @Bean
    AuthenticationFailureHandler failureHandler(LoginAttemptService attempts) {
        return (request, response, exception) -> {
            String username = request.getParameter("username");
            String ip = request.getRemoteAddr();
            if (!(exception instanceof DisabledException)) {
                attempts.onFailure(username, ip);
            }
            if (attempts.isLocked(username, ip) || exception instanceof DisabledException) {
                response.sendRedirect("/login?error=locked");
            } else {
                response.sendRedirect("/login?error");
            }
        };
    }

    @Bean
    UserDetailsService users(
            @Value("${esplus.username:admin}") String username,
            @Value("${esplus.password:esplus}") String password,
            @Value("${esplus.modUsername:}") String modUsername,
            @Value("${esplus.modPassword:}") String modPassword,
            @Value("${esplus.viewerUsername:}") String viewerUsername,
            @Value("${esplus.viewerPassword:}") String viewerPassword,
            PasswordEncoder encoder,
            LoginAttemptService attempts
    ) {
        List<UserDetails> list = new ArrayList<>();
        list.add(User.withUsername(username).password(encoder.encode(password)).roles("ADMIN").build());
        if (modUsername != null && !modUsername.isBlank() && modPassword != null && !modPassword.isBlank()) {
            list.add(User.withUsername(modUsername).password(encoder.encode(modPassword)).roles("MODERATOR").build());
        }
        if (viewerUsername != null && !viewerUsername.isBlank() && viewerPassword != null && !viewerPassword.isBlank()) {
            list.add(User.withUsername(viewerUsername).password(encoder.encode(viewerPassword)).roles("VIEWER").build());
        }
        InMemoryUserDetailsManager manager = new InMemoryUserDetailsManager(list);
        return username1 -> {
            if (attempts.isLocked(username1)) {
                throw new DisabledException("account locked");
            }
            return manager.loadUserByUsername(username1);
        };
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
