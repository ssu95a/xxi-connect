package ru.inversion.msrv.tech_cred;

import ru.inversion.db.session.xxi.ConnectorBase;

import java.net.PasswordAuthentication;

public class XXIPseudoCredentialsProvider implements TechCredentialsProvider{

    @Override
    public PasswordAuthentication get() {
        return ConnectorBase.getTechUserAuth();
    }
}
