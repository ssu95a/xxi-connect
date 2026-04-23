package ru.inversion.msrv.tech_cred;

import java.net.PasswordAuthentication;

public final class MachineSecretCredentialsProvider implements TechCredentialsProvider {

    @Override
    public PasswordAuthentication get() {
        // TODO: позже: прочитать секрет (DPAPI/Keychain/Vault agent/local service/etc.)
        throw new UnsupportedOperationException("Machine secret provider is not implemented yet");
    }
}
