package ru.inversion.msrv.tech_cred;

import ru.inversion.msrv.config.Config;

import java.net.PasswordAuthentication;

import static ru.inversion.msrv.config.Config.Namespace.AUTH;

public final class EnvTechCredentialsProvider implements TechCredentialsProvider {

    private final Config config;

    public EnvTechCredentialsProvider(Config config)
    {
        this.config = config;
    }

    @Override
    public PasswordAuthentication get() {
        String user = config.getString( AUTH.resolve("tech.user"), true);
        char[] pass = config.get      ( AUTH.resolve("tech.password"), char[].class, true );
        assert pass != null;
        return new PasswordAuthentication( user, pass );
    }

    @Override
    public void close() {
    }
}
