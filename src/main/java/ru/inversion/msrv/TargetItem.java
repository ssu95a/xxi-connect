package ru.inversion.msrv;

import ru.inversion.utils.S;

import java.net.PasswordAuthentication;

/**
 * TargetItem — метаданные target-а из targets_url.xml.
 * Ответственность:
 *  - хранить alias + vendor + jdbcUrl (trusted config) + login + pswd
 *  DTO
 */
public class TargetItem {

    final private String rawAlias;
    final private String nrmAlias;

    final private String jdbcUrl;

    final private VendorDbEnum vendorDb;

    private final PasswordAuthentication auth;

    /** */
    TargetItem(String rawAlias, String jdbcUrl, VendorDbEnum vendorDb, PasswordAuthentication auth ) {
        this.rawAlias = rawAlias;
        this.nrmAlias = normalizeAlias(rawAlias);
        this.jdbcUrl  = jdbcUrl;
        this.vendorDb = vendorDb;
        this.auth     = auth;
    }

    public String jdbcUrl() {
        return jdbcUrl;
    }

    public String rawAlias(){ return rawAlias; }

    public String nrmAlias(){ return nrmAlias; }

    public VendorDbEnum vendorDb() { return vendorDb; }

    /** */
    PasswordAuthentication auth( ) {
        return auth;
    }

    /** */
    public static String normalizeAlias(String a) {

        if( S.isNullOrEmpty(a) )
            return null;

        a = a.trim();

        if( a.isEmpty() )
            return null;

        return a.toUpperCase( java.util.Locale.ROOT );
    }

}
