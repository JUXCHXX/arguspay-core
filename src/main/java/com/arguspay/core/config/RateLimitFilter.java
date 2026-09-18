package com.arguspay.core.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

public class RateLimitFilter extends OncePerRequestFilter {

    private static final long WINDOW_MS = 60_000;
    private static final int CLEANUP_THRESHOLD = 10_000;

    private record Window(long start, int count) {}

    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
    private final int authLimit;
    private final int apiLimit;

    public RateLimitFilter(int authLimit, int apiLimit) {
        this.authLimit = authLimit;
        this.apiLimit = apiLimit;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        boolean isAuth = request.getRequestURI().startsWith("/api/auth/");
        String key = isAuth ? "auth:" + request.getRemoteAddr() : "api:" + identify(request);
        int limit = isAuth ? authLimit : apiLimit;

        long now = System.currentTimeMillis();
        cleanupIfNeeded(now);

        Window window = windows.compute(key, (k, current) -> {
            if (current == null || now - current.start() >= WINDOW_MS) {
                return new Window(now, 1);
            }
            return new Window(current.start(), current.count() + 1);
        });

        if (window.count() > limit) {
            long retryAfter = Math.max(1, (window.start() + WINDOW_MS - now) / 1000 + 1);
            response.setStatus(429);
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            response.setHeader("Retry-After", String.valueOf(retryAfter));
            response.getWriter().write("{\"timestamp\":\"" + Instant.now()
                    + "\",\"status\":429,\"error\":\"Too Many Requests\","
                    + "\"message\":\"Rate limit exceeded. Try again in " + retryAfter + " seconds\"}");
            return;
        }

        chain.doFilter(request, response);
    }

    private String identify(HttpServletRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getName())) {
            return auth.getName();
        }
        return request.getRemoteAddr();
    }

    private void cleanupIfNeeded(long now) {
        if (windows.size() > CLEANUP_THRESHOLD) {
            windows.entrySet().removeIf(e -> now - e.getValue().start() >= WINDOW_MS);
        }
    }
}