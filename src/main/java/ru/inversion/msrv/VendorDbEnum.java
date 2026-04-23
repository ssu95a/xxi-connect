package ru.inversion.msrv;

import ru.inversion.db.session.xxi.PGPseudoConnector;
import ru.inversion.db.session.xxi.XxiConnector;
import ru.inversion.msrv.config.db.IVendorDBSpecificInitializer;
import ru.inversion.msrv.config.db.OraInitializer;
import ru.inversion.msrv.config.db.PGInitializer;
import ru.inversion.utils.S;
import ru.inversion.utils.TriFunction;

import java.net.PasswordAuthentication;
import java.sql.Connection;
import java.util.NoSuchElementException;
import java.util.Properties;

/** */
public enum VendorDbEnum {

    POSTGRES( new PGInitializer() ),

    ORACLE  ( new OraInitializer() );

    final private IVendorDBSpecificInitializer initializer;

    VendorDbEnum( IVendorDBSpecificInitializer initializer) {
        this.initializer = initializer;
    }

    public IVendorDBSpecificInitializer initializer()
    {
        return initializer;
    }

    public TriFunction<Connection,PasswordAuthentication,Properties,PasswordAuthentication> serverSideLogin()
    {
        if( this == POSTGRES )
            return PGPseudoConnector::serverSideLogin;;
        if( this == ORACLE )
            return XxiConnector::serverSideLogin;

        throw new NoSuchElementException("No serverSideLogin for vendor:" + this );
    }


    /** */
    public String postfix()
    {
        if( this == POSTGRES )
            return "pg";
        if( this == ORACLE )
            return "ora";

        throw new NoSuchElementException("No 'postfix' for vendor:" + this );
    }

    /** */
    public static VendorDbEnum of( String s) {

        if( S.isNullOrEmpty(s) )
            return null;

        String v = s.trim().toLowerCase();

        return switch (v) {
            case "oracle" -> ORACLE;
            case "postgres", "postgresql" -> POSTGRES;
            default -> throw new NoSuchElementException( "Unsupported vendor: " + v );
        };
    }

    public static VendorDbEnum fromJdbcUrl( String jdbcUrl ) {

        if( S.isNullOrEmpty(jdbcUrl) )
            return null;

        final String v = jdbcUrl.trim().toLowerCase();

        if( v.startsWith("jdbc:postgresql:") )
            return POSTGRES;

        if( v.startsWith("jdbc:oracle:"))
            return ORACLE;

        throw new NoSuchElementException( "No vendor for jdbcUrl:" + jdbcUrl );
    }
}
