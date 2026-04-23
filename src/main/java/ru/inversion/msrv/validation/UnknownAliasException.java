package ru.inversion.msrv.validation;


public final class UnknownAliasException extends XXIConnectException {

    private final String rawAlias;
    private final String normalizedAlias;

    public UnknownAliasException(String rawAlias, String normalizedAlias) {
        super(Errors.ErrorCode.TARGET_ALIAS_UNKNOWN, Errors.ErrorCode.TARGET_ALIAS_UNKNOWN.externalMessage() );
        this.rawAlias        = rawAlias;
        this.normalizedAlias = normalizedAlias;
    }

    public String rawAlias() {
        return rawAlias;
    }

    public String normalizedAlias() {
        return normalizedAlias;
    }
}