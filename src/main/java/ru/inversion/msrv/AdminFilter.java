package ru.inversion.msrv;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import ru.inversion.msrv.config.Config;
import ru.inversion.msrv.validation.Errors;

import java.io.IOException;

import static ru.inversion.msrv.config.Config.Namespace.ADMIN;

public final class AdminFilter implements Filter {

    public static final String ADMIN_TOKEN_HEADER = "X-Admin-Token";

    private final String adminToken;

    public AdminFilter(Config config) {
        this.adminToken = config.get( ADMIN.resolve("token"), String.class, null );
    }

    @Override
    public void doFilter( ServletRequest request, ServletResponse response, FilterChain chain ) throws IOException, ServletException {

        final HttpServletRequest  req = (HttpServletRequest ) request;

        if( !isAdmin(req) ) {
            chain.doFilter(request, response);
            return;
        }

        if( adminToken == null || adminToken.isBlank() )
            throw Errors.forbidden( "Admin is disabled" );

        final String token = req.getHeader( ADMIN_TOKEN_HEADER );

        if( !secureEquals( adminToken, token) )
            throw Errors.forbidden( "Admin access denied");

        chain.doFilter( request, response );
    }

    /** */
    private static boolean isAdmin(HttpServletRequest req ) {

        String p = req.getServletPath();

        if( p == null || p.isBlank() )
            p = req.getRequestURI();

        return p != null && ( p.equals("/admin") || p.startsWith("/admin/") );
    }

    /** */
    private static boolean secureEquals( String a, String b )
    {
        if( a == null || b == null  ) return false;
        if( a.length() != b.length()) return false;

        int r = 0;

        for( int i = 0; i < a.length(); i++ )
             r |= a.charAt(i) ^ b.charAt(i);

        return r == 0;
    }
}