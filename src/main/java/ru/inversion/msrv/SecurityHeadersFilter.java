package ru.inversion.msrv;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

public final class SecurityHeadersFilter implements Filter {

    private final boolean noStore;

    public SecurityHeadersFilter(boolean noStore) {
        this.noStore = noStore;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletResponse resp = (HttpServletResponse) response;
        resp.setHeader("X-Content-Type-Options", "nosniff");
        // clickjacking защита (простая)
        resp.setHeader("X-Frame-Options", "DENY");
        // не раскрывать лишнее в referer
        resp.setHeader("Referrer-Policy", "no-referrer");
        // “отруби всё” политика разрешений
        resp.setHeader("Permissions-Policy","camera=(), microphone=(), geolocation=(), usb=(), payment=(), interest-cohort=()");

        // CSP: для чистого API можно очень жёстко
        // (если потом будет HTML с inline-скриптами — придётся ослаблять)
        resp.setHeader("Content-Security-Policy","default-src 'none'; frame-ancestors 'none'; base-uri 'none'; form-action 'none'");

        if (noStore) {
            resp.setHeader("Cache-Control", "no-store");
            resp.setHeader("Pragma", "no-cache");
        }
        chain.doFilter(request, response);
    }
}
