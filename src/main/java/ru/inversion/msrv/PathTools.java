package ru.inversion.msrv;

import jakarta.servlet.http.HttpServletRequest;
import ru.inversion.utils.S;

public final class PathTools {

    public static final String AUTH_ROOT = "/auth";

    private PathTools( ) {
    }

    /**
     * Универсальная проверка, относится ли запрос к auth-zone.
     *
     * Поддерживает:
     * - /auth
     * - /auth/
     * - /auth/{something}
     *
     * Не матчится на:
     * - /state/auth
     * - /my/authx
     * - /authorization
     */
    public static boolean isAuth( HttpServletRequest req) {

        if( req == null )
            return false;

        final String path = normalizeRequestPath(req);

        return isAuthPath(path);
    }

    /** */
    public static boolean isAuthPath(String path) {

        if( S.isNullOrEmpty(path) )
            return false;

        String p = path.strip();

        if( p.isEmpty() )
            return false;

        if( !p.startsWith("/") )
            p = "/" + p;

        return AUTH_ROOT.equals(p) || (AUTH_ROOT + "/").equals(p) || p.startsWith(AUTH_ROOT + "/");
    }

    /**
     * Возвращает path без contextPath.
     * Примеры:
     *   contextPath=""
     *   requestURI="/auth/pg01"     -> "/auth/pg01"
     *
     *   contextPath="/msrv"
     *   requestURI="/msrv/auth"     -> "/auth"
     */
    public static String normalizeRequestPath(HttpServletRequest req) {
        String servletPath = req.getServletPath();
        String pathInfo    = req.getPathInfo();

        // servletPath + pathInfo
        String combined = joinPath(servletPath, pathInfo);
        if (!S.isNullOrEmpty(combined))
            return combined;

        // Fallback на requestURI minus contextPath
        String uri = req.getRequestURI();
        if (S.isNullOrEmpty(uri))
            return null;

        String ctx = req.getContextPath();
        if (!S.isNullOrEmpty(ctx) && uri.startsWith(ctx))
            uri = uri.substring(ctx.length());

        return uri;
    }

    private static String joinPath(String servletPath, String pathInfo) {
        boolean hasServletPath = !S.isNullOrEmpty(servletPath);
        boolean hasPathInfo    = !S.isNullOrEmpty(pathInfo);

        if (!hasServletPath && !hasPathInfo)
            return null;

        String sp = hasServletPath ? servletPath : "";
        String pi = hasPathInfo ? pathInfo : "";

        if (sp.endsWith("/") && pi.startsWith("/"))
            return sp + pi.substring(1);

        return sp + pi;
    }

    /** */
    public static String normalizePath(String pathInfo) {
        if( pathInfo == null || pathInfo.isBlank() )
            return "/";
        return pathInfo.startsWith("/") ? pathInfo : "/" + pathInfo;
    }

    /** */
    static String[] splitPath(String pi) {
        String s = pi;
        if( s == null || s.isBlank() || "/".equals(s) )
            return new String[0];

        if( s.startsWith("/") )
            s = s.substring(1);

        return s.split("/");
    }
}