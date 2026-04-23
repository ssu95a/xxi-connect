package ru.inversion.msrv.config.db;

import com.zaxxer.hikari.HikariConfig;

public interface IVendorDBSpecificInitializer {
    void initialize(HikariConfig hc);
}
