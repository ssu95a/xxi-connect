package ru.inversion.msrv.tech_cred;

import ru.inversion.msrv.config.Config;

import java.net.PasswordAuthentication;
import java.util.function.Supplier;

/** */
public interface TechCredentialsProvider extends Supplier<PasswordAuthentication>, AutoCloseable {
    /** По умолчанию ничего не делаем. */
    @Override
    default void close() { }

    /** */
    static TechCredentialsProvider createDefault( Config config ) {

        String mode = config.getString("tech.cred.auth.mode","xxi");

        return switch (mode) {
            case "xxi"     -> new XXIPseudoCredentialsProvider();
            case "env"     -> new EnvTechCredentialsProvider(config);
            case "machine" -> new MachineSecretCredentialsProvider();
            default -> throw new IllegalArgumentException("Unknown get tech creds mode: " + mode);
        };
    }
}
