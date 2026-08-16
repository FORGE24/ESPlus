package com.esplus.panel;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST auth endpoints for the React SPA.
 * Works alongside Spring Security's form login — these endpoints
 * perform the same authentication but return JSON instead of redirects.
 */
@RestController
@RequestMapping("/api/auth")
public class PanelAuthController {

    private final AuthenticationManager authManager;
    private final LoginAttemptService attempts;
    private final PanelMfaService mfa;

    public PanelAuthController(AuthenticationManager authManager,
                               LoginAttemptService attempts,
                               PanelMfaService mfa) {
        this.authManager = authManager;
        this.attempts = attempts;
        this.mfa = mfa;
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(
            @RequestParam String username,
            @RequestParam String password,
            HttpServletRequest request
    ) {
        Map<String, Object> resp = new HashMap<>();
        String ip = request.getRemoteAddr();
        try {
            Authentication auth = authManager.authenticate(
                    new UsernamePasswordAuthenticationToken(username, password));
            attempts.onSuccess(username);
            SecurityContextHolder.getContext().setAuthentication(auth);
            // Store in session
            HttpSession session = request.getSession(true);
            session.setAttribute("SPRING_SECURITY_CONTEXT", SecurityContextHolder.getContext());

            if (mfa.isEnabled(username)) {
                session.setAttribute(PanelMfaFilter.MFA_PENDING, username);
                session.removeAttribute(PanelMfaFilter.MFA_OK);
                resp.put("ok", true);
                resp.put("mfaRequired", true);
                return ResponseEntity.ok(resp);
            }
            session.setAttribute(PanelMfaFilter.MFA_OK, username);
            resp.put("ok", true);
            resp.put("name", username);
            resp.put("role", auth.getAuthorities().iterator().next().getAuthority().replace("ROLE_", ""));
            return ResponseEntity.ok(resp);
        } catch (Exception ex) {
            attempts.onFailure(username, ip);
            if (attempts.isLocked(username, ip)) {
                resp.put("ok", false);
                resp.put("error", "账户已锁定，请稍后再试");
                return ResponseEntity.ok(resp);
            }
            resp.put("ok", false);
            resp.put("error", "用户名或密码错误");
            return ResponseEntity.ok(resp);
        }
    }

    @PostMapping("/mfa")
    public ResponseEntity<Map<String, Object>> mfa(
            @RequestParam String code,
            HttpServletRequest request
    ) {
        Map<String, Object> resp = new HashMap<>();
        String ip = request.getRemoteAddr();
        HttpSession session = request.getSession(false);
        String sessionId = session != null ? session.getId() : "";

        if (attempts.isMfaLocked(ip, sessionId)) {
            resp.put("ok", false);
            resp.put("error", "MFA 尝试次数过多，已锁定");
            return ResponseEntity.ok(resp);
        }

        if (session == null) {
            resp.put("ok", false);
            resp.put("error", "会话已过期，请重新登录");
            return ResponseEntity.ok(resp);
        }

        Object pending = session.getAttribute(PanelMfaFilter.MFA_PENDING);
        String user = pending == null ? null : String.valueOf(pending);
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (user == null && auth != null && auth.isAuthenticated()) {
            user = auth.getName();
        }

        if (user == null || !mfa.verify(user, code)) {
            attempts.onMfaFailure(ip, sessionId);
            resp.put("ok", false);
            resp.put("error", "验证码错误");
            return ResponseEntity.ok(resp);
        }

        attempts.onMfaSuccess(ip, sessionId);
        session.setAttribute(PanelMfaFilter.MFA_OK, user);
        session.removeAttribute(PanelMfaFilter.MFA_PENDING);

        resp.put("ok", true);
        resp.put("name", user);
        if (auth != null && auth.getAuthorities() != null && !auth.getAuthorities().isEmpty()) {
            resp.put("role", auth.getAuthorities().iterator().next().getAuthority().replace("ROLE_", ""));
        } else {
            resp.put("role", "ADMIN");
        }
        return ResponseEntity.ok(resp);
    }

    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> me() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Map<String, Object> resp = new HashMap<>();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return ResponseEntity.status(401).body(Map.of("error", "not authenticated"));
        }
        resp.put("name", auth.getName());
        if (auth.getAuthorities() != null && !auth.getAuthorities().isEmpty()) {
            String role = auth.getAuthorities().iterator().next().getAuthority();
            resp.put("role", role.replace("ROLE_", ""));
        }
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, Object>> logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
        return ResponseEntity.ok(Map.of("ok", true));
    }
}
