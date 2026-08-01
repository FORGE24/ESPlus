package com.esplus.panel;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
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
                        .requestMatchers("/css/**", "/login", "/login/mfa").permitAll()
                        .requestMatchers("/api/ops/**").permitAll()
                        .requestMatchers("/setup/**").hasRole("ADMIN")
                        .requestMatchers("/console/**", "/api/console").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/maintenance/**", "/api/broadcast").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/players/**").hasAnyRole("ADMIN", "MODERATOR")
                        .requestMatchers("/admins/**").hasRole("ADMIN")
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
                .csrf(csrf -> csrf.ignoringRequestMatchers("/api/**"))
                .addFilterAfter(mfaFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
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
            if (!(exception instanceof DisabledException)) {
                attempts.onFailure(username);
            }
            if (attempts.isLocked(username) || exception instanceof DisabledException) {
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
