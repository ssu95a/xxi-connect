package ru.inversion.msrv.config.db;

import com.zaxxer.hikari.HikariConfig;
import ru.inversion.utils.S;

import java.util.Locale;

/** */
public class OraInitializer implements IVendorDBSpecificInitializer {

    @Override
    public void initialize(HikariConfig hc)
    {
        String domain = System.getenv("USERDOMAIN");
        String host   = System.getenv("COMPUTERNAME");

        if( !S.isNullOrEmpty(domain) && !S.isNullOrEmpty(host) )
            hc.addDataSourceProperty("v$session.machine", (domain + "\\" + host).toUpperCase(Locale.ROOT));
        else if (!S.isNullOrEmpty(host))
            hc.addDataSourceProperty("v$session.machine", host.toUpperCase(Locale.ROOT));

        hc.addDataSourceProperty( "v$session.program", "xxi-connect" );
    }
}
