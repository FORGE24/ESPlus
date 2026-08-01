package com.esplus.panel;

import java.io.IOException;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * After password login, require TOTP when panel_mfa.enabled for that user.
 */
@Component
public class PanelMfaFilter extends OncePerRequestFilter {
    public static final String MFA_PENDING = "SEM_MFA_PENDING_USER";
    public static final String MFA_OK = "SEM_MFA_OK";

    private final PanelMfaService mfa;
    private final JdbcTemplate jdbc;

    public PanelMfaFilter(PanelMfaService mfa, JdbcTemplate jdbc) {
        this.mfa = mfa;
        this.jdbc = jdbc;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        if (path.startsWith("/css/") || path.startsWith("/api/ops") || path.equals("/login")
                || path.equals("/login/mfa") || path.equals("/error")) {
            filterChain.doFilter(request, response);
            return;
        }

        // Setup wizard gate for ADMIN after MFA
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean authed = auth != null && auth.isAuthenticated() && auth.getName() != null
                && !"anonymousUser".equals(auth.getName());

        if (authed) {
            HttpSession session = request.getSession(false);
            String user = auth.getName();
            if (mfa.isEnabled(user)) {
                boolean ok = session != null && user.equals(session.getAttribute(MFA_OK));
                if (!ok && !path.equals("/login/mfa")) {
                    if (session != null) {
                        session.setAttribute(MFA_PENDING, user);
                    }
                    response.sendRedirect("/login/mfa");
                    return;
                }
            }
            if (!path.startsWith("/setup") && isSetupIncomplete() && hasAdmin(auth)) {
                response.sendRedirect("/setup");
                return;
            }
        }
        filterChain.doFilter(request, response);
    }

    private boolean hasAdmin(Authentication auth) {
        return auth.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
    }

    private boolean isSetupIncomplete() {
        try {
            Long v = jdbc.queryForObject(
                    "SELECT setup_complete FROM server_runtime WHERE id = 1", Long.class);
            return v == null || v == 0L;
        } catch (Exception ex) {
            return false;
        }
    }
}
