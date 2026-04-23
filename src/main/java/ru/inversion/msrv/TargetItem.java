package ru.inversion.msrv;

import ru.inversion.utils.S;

/**
 * TargetItem — метаданные target-а из targets_url.xml.
 * Ответственность:
 *  - хранить alias + vendor + jdbcUrl (trusted config)
 *  - НЕ лезть в БД и НЕ держать pool
 *  DTO
 */
public class TargetItem {

    final private String rawAlias;
    final private String nrmAlias;

    final private String jdbcUrl;

    final private VendorDbEnum vendorDb;

    /** */
    TargetItem(String rawAlias, String jdbcUrl, VendorDbEnum vendorDb ) {
        this.rawAlias = rawAlias;
        this.nrmAlias = normalizeAlias(rawAlias);
        this.jdbcUrl  = jdbcUrl;
        this.vendorDb = vendorDb;
    }

    public String jdbcUrl() {
        return jdbcUrl;
    }

    public String rawAlias() {
        return rawAlias;
    }
    public String nrmAlias() {
        return nrmAlias;
    }

    public VendorDbEnum vendorDb() { return vendorDb; }

    /** */
    public static String normalizeAlias(String a) {

        if( S.isNullOrEmpty(a) )
            return null;

        a = a.trim();

        if( a.isEmpty() )
            return null;

        return a.toUpperCase(java.util.Locale.ROOT);
    }

}
