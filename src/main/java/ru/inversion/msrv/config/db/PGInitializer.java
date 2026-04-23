package ru.inversion.msrv.config.db;

import com.zaxxer.hikari.HikariConfig;
import ru.inversion.utils.S;

/** */
public class PGInitializer implements IVendorDBSpecificInitializer {

    @Override
    public void initialize(HikariConfig hc)
    {
//        hc.addDataSourceProperty( "useUnicode",           "true" );
//        hc.addDataSourceProperty( "characterEncoding",    "utf8" );
        hc.addDataSourceProperty( "ApplicationName",      "xxi-connect" );
        hc.addDataSourceProperty( "escapeSyntaxCallMode", "callIfNoReturn" );

        String poolName = hc.getPoolName();
        if( !S.isNullOrEmpty(poolName) ) {
            poolName += "-pg";
            hc.setPoolName( poolName );
        }

//        hc.addDataSourceProperty( "preferQueryMode",      "extendedForPrepared" );
//        hc.addDataSourceProperty( "defaultRowFetchSize",  "60"   );
//        hc.addDataSourceProperty( "prepareThreshold",     "0"    );
    }
}
