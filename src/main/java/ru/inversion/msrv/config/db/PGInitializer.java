package ru.inversion.msrv.config.db;

import com.zaxxer.hikari.HikariConfig;

/** */
public class PGInitializer implements IVendorDBSpecificInitializer {

    @Override
    public void initialize(HikariConfig hc)
    {
        hc.addDataSourceProperty( "ApplicationName",      "xxi-connect" );
        hc.addDataSourceProperty( "escapeSyntaxCallMode", "callIfNoReturn" );
    }
}
