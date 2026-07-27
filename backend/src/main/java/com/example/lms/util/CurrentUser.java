package com.example.lms.util;

import com.example.lms.repository.AccountRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Component
public class CurrentUser {

    private final AccountRepository accounts;

    public CurrentUser(AccountRepository accounts) {
        this.accounts = accounts;
    }

    public record Actor(Long id, String username, String role, String ipAddress, String userAgent) {}

    public Actor get() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return new Actor(null, "system", "SYSTEM", null, null);
        }
        String username = auth.getName();
        String role = auth.getAuthorities().stream()
                .findFirst()
                .map(g -> g.getAuthority().replace("ROLE_", ""))
                .orElse("UNKNOWN");
        Long id = accounts.findByUsername(username).map(a -> a.getId()).orElse(null);
        String ip = null;
        String ua = null;
        try {
            var attrs = RequestContextHolder.currentRequestAttributes();
            if (attrs instanceof ServletRequestAttributes sra) {
                ip = sra.getRequest().getRemoteAddr();
                ua = sra.getRequest().getHeader("User-Agent");
            }
        } catch (IllegalStateException e) {
            // Not in a request context (e.g., during seeding)
        }
        return new Actor(id, username, role, ip, ua);
    }
}
