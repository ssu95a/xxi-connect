package ru.inversion.msrv.validation;

import ru.inversion.db.session.xxi.XXIConnectorException;

import java.util.Locale;

public final class Errors {

    public enum LogPolicy {
        WARN_NO_STACK,
        ERROR_WITH_STACK
    }

    /** */
    public enum Namespace {
        REQUEST, TARGET, AUTH, ADMIN, STATE, CONFIG, INTERNAL;

        public String code() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** */
    public enum ErrorCode {

        REQUEST_VALUE_MISSING(
                Namespace.REQUEST,
                "value",
                "request.value.missing",
                400,
                LogPolicy.WARN_NO_STACK,
                "Missing value"
        ),

        REQUEST_VALUE_TOO_LONG(
                Namespace.REQUEST,
                "value",
                "request.value.too.long",
                400,
                LogPolicy.WARN_NO_STACK,
                "Value too long"
        ),

        REQUEST_VALUE_INVALID(
                Namespace.REQUEST,
                "value",
                "request.value.invalid",
                400,
                LogPolicy.WARN_NO_STACK,
                "Invalid value"
        ),

        REQUEST_INVALID(
                Namespace.REQUEST,
                "request",
                "request.invalid",
                400,
                LogPolicy.WARN_NO_STACK,
                "Invalid request"
        ),

        REQUEST_XML_MALFORMED(
                Namespace.REQUEST,
                "xml",
                "request.xml.malformed",
                400,
                LogPolicy.WARN_NO_STACK,
                "Malformed XML"
        ),

        REQUEST_CONTENT_TYPE_INVALID (
            Namespace.REQUEST,
            "contentType",
            "request.content_type.invalid",
            400,
            LogPolicy.WARN_NO_STACK,
            "Invalid Content-Type"
        ),

        REQUEST_PAYLOAD_TOO_LARGE (
                Namespace.REQUEST,
                "payload",
                "request.payload.too.large",
                413,
                LogPolicy.WARN_NO_STACK,
                "Request payload is too large"
        ),

        REQUEST_TARGET_ALIAS_MISSING (
                Namespace.REQUEST,
                "alias",
                "request.target.alias.missing",
                400,
                LogPolicy.WARN_NO_STACK,
                "'alias' is missing"
        ),

        REQUEST_TARGET_ALIAS_INVALID(
                Namespace.REQUEST,
                "alias",
                "request.target.alias.invalid",
                400,
                LogPolicy.WARN_NO_STACK,
                "Invalid target alias"
        ),


        REQUEST_USER_LOGIN_MISSING(
                Namespace.REQUEST,
                "login",
                "request.user.login.missing",
                400,
                LogPolicy.WARN_NO_STACK,
                "'login' is missing"
        ),

        REQUEST_USER_LOGIN_INVALID(
                Namespace.REQUEST,
                "login",
                "request.user.login.invalid",
                400,
                LogPolicy.WARN_NO_STACK,
                "Invalid login"
        ),


        REQUEST_USER_PASSWORD_MISSING(
                Namespace.REQUEST,
                "password",
                "request.user.password.missing",
                400,
                LogPolicy.WARN_NO_STACK,
                "'password' is missing"
        ),

        REQUEST_USER_PASSWORD_INVALID(
                Namespace.REQUEST,
                "password",
                "request.user.password.invalid",
                400,
                LogPolicy.WARN_NO_STACK,
                "Invalid password"
        ),


        TARGET_ALIAS_UNKNOWN(
                Namespace.TARGET,
                "alias",
                "target.alias.unknown",
                400,
                LogPolicy.WARN_NO_STACK,
                "Unknown target alias"
        ),

        TARGET_ALIAS_DISABLED(
                Namespace.TARGET,
                "alias",
                "target.alias.disabled",
                403,
                LogPolicy.WARN_NO_STACK,
                "Target alias is disabled"
        ),

        TARGET_RUNTIME_UNAVAILABLE(
                Namespace.TARGET,
                "runtime",
                "target.runtime.unavailable",
                503,
                LogPolicy.ERROR_WITH_STACK,
                "Target runtime is unavailable"
        ),

        TARGET_POOL_UNAVAILABLE(
                Namespace.TARGET,
                "pool",
                "target.pool.unavailable",
                503,
                LogPolicy.ERROR_WITH_STACK,
                "Target pool is unavailable"
        ),

        TARGET_POOL_TIMEOUT(
                Namespace.TARGET,
                "pool",
                "target.pool.timeout",
                503,
                LogPolicy.ERROR_WITH_STACK,
                "Target pool timeout"
        ),

        TARGET_DB_UNAVAILABLE(
                Namespace.TARGET,
                "db",
                "target.db.unavailable",
                503,
                LogPolicy.ERROR_WITH_STACK,
                "Target database is unavailable"
        ),

        TARGET_DB_TECHNICAL_BREAK (
                Namespace.TARGET,
                "db",
                "target.db.technical.break",
                503,
                LogPolicy.ERROR_WITH_STACK,
                "Target database is temporarily unavailable"
        ),

        AUTH_CREDENTIALS_INVALID(
                Namespace.AUTH,
                "credentials",
                "auth.credentials.invalid",
                401,
                LogPolicy.WARN_NO_STACK,
                "Invalid credentials"
        ),

        AUTH_PASSWORD_EXPIRED (
                Namespace.AUTH,
                "credentials",
                "auth.password.expired",
                409,
                LogPolicy.WARN_NO_STACK,
                "Password expired"
        ),

        AUTH_PASSWORD_CHANGE_REQUIRED (
                Namespace.AUTH,
                "credentials",
                "auth.password.change.required",
                409,
                LogPolicy.WARN_NO_STACK,
                "Password change is required"
        ),

        AUTH_METHOD_MISMATCH (
                Namespace.AUTH,
                "auth",
                "auth.method.mismatch",
                400,
                LogPolicy.WARN_NO_STACK,
                "Authentication method mismatch"
        ),

        AUTH_DENIED(
                Namespace.AUTH,
                "credentials",
                "auth.denied",
                403,
                LogPolicy.WARN_NO_STACK,
                "Authentication denied"
        ),

        FORBIDDEN(
                Namespace.AUTH,
                "access",
                "auth.forbidden",
                403,
                LogPolicy.WARN_NO_STACK,
                "Access denied"
        ),

        CONFLICT(
                Namespace.INTERNAL,
                "conflict",
                "internal.conflict",
                409,
                LogPolicy.WARN_NO_STACK,
                "Conflict"
        ),

        SERVICE_UNAVAILABLE(
                Namespace.INTERNAL,
                "service",
                "internal.service.unavailable",
                503,
                LogPolicy.ERROR_WITH_STACK,
                "Service is unavailable"
        ),

        ADMIN_TOKEN_MISSING(
                Namespace.ADMIN,
                "token",
                "admin.token.missing",
                403,
                LogPolicy.WARN_NO_STACK,
                "Admin token is missing"
        ),

        ADMIN_TOKEN_INVALID(
                Namespace.ADMIN,
                "token",
                "admin.token.invalid",
                403,
                LogPolicy.WARN_NO_STACK,
                "Admin token is invalid"
        ),

        ADMIN_COMMAND_INVALID(
                Namespace.ADMIN,
                "command",
                "admin.command.invalid",
                400,
                LogPolicy.WARN_NO_STACK,
                "Invalid admin command"
        ),

        ADMIN_COMMAND_UNSUPPORTED(
                Namespace.ADMIN,
                "command",
                "admin.command.unsupported",
                400,
                LogPolicy.WARN_NO_STACK,
                "Unsupported admin command"
        ),

        ADMIN_TARGET_ALIAS_MISSING(
                Namespace.ADMIN,
                "alias",
                "admin.target.alias.missing",
                400,
                LogPolicy.WARN_NO_STACK,
                "Admin target alias is missing"
        ),

        ADMIN_TARGET_ALIAS_UNKNOWN(
                Namespace.ADMIN,
                "alias",
                "admin.target.alias.unknown",
                400,
                LogPolicy.WARN_NO_STACK,
                "Unknown admin target alias"
        ),

        STATE_NOT_READY(
                Namespace.STATE,
                "ready",
                "state.not.ready",
                503,
                LogPolicy.WARN_NO_STACK,
                "Service is not ready"
        ),

        CONFIG_TARGETS_MISSING(
                Namespace.CONFIG,
                "targets",
                "config.targets.missing",
                500,
                LogPolicy.ERROR_WITH_STACK,
                "Targets configuration is missing"
        ),

        CONFIG_TARGETS_INVALID(
                Namespace.CONFIG,
                "targets",
                "config.targets.invalid",
                500,
                LogPolicy.ERROR_WITH_STACK,
                "Targets configuration is invalid"
        ),

        CONFIG_ALIAS_PATTERN_INVALID(
                Namespace.CONFIG,
                "aliasPattern",
                "config.alias.pattern.invalid",
                500,
                LogPolicy.ERROR_WITH_STACK,
                "Alias pattern configuration is invalid"
        ),

        CONFIG_RUNTIME_INVALID(
                Namespace.CONFIG,
                "runtime",
                "config.runtime.invalid",
                500,
                LogPolicy.ERROR_WITH_STACK,
                "Runtime configuration is invalid"
        ),

        CRYPTO_ENCRYPTION_ERROR(
                Namespace.INTERNAL,
                "crypto",
                "crypto.encryption.error",
                500,
                LogPolicy.ERROR_WITH_STACK,
                "Encryption error"
        ),

        CRYPTO_DECRYPTION_ERROR(
                Namespace.INTERNAL,
                "crypto",
                "crypto.decryption.error",
                500,
                LogPolicy.ERROR_WITH_STACK,
                "Decryption error"
        ),

        INTERNAL_ERROR(
                Namespace.INTERNAL,
                "error",
                "internal.error",
                500,
                LogPolicy.ERROR_WITH_STACK,
                "Internal server error"
        ),

        INTERNAL_UNEXPECTED(
                Namespace.INTERNAL,
                "error",
                "internal.unexpected",
                500,
                LogPolicy.ERROR_WITH_STACK,
                "Unexpected internal error"
        );

        private final Namespace namespace;
        private final String objectField;
        private final String code;
        private final int httpStatus;
        private final LogPolicy logPolicy;
        private final String externalMessage;

        ErrorCode(
                Namespace namespace,
                String objectField,
                String code,
                int httpStatus,
                LogPolicy logPolicy,
                String externalMessage
        ) {
            this.namespace = namespace;
            this.objectField = objectField;
            this.code = code;
            this.httpStatus = httpStatus;
            this.logPolicy = logPolicy;
            this.externalMessage = externalMessage;
        }

        public Namespace namespace() {
            return namespace;
        }

        public String objectAlias() {
            return namespace.code();
        }

        public String objectField() {
            return objectField;
        }

        public String code() {
            return code;
        }

        public int httpStatus() {
            return httpStatus;
        }

        public LogPolicy logPolicy() {
            return logPolicy;
        }

        public String externalMessage() {
            return externalMessage;
        }
    }

    private Errors() {
    }

    public static XXIConnectException of(ErrorCode error) {
        return of( error, error.externalMessage(), null );
    }

    public static XXIConnectException of(ErrorCode error, Throwable cause) {
        return of( error, error.externalMessage(), cause);
    }

    public static XXIConnectException of(ErrorCode error, String message) {
        return of( error, message, null );
    }

    public static XXIConnectException of(ErrorCode error, String message, Throwable cause ) {
        return new XXIConnectException(error, message, cause);
    }

    private static XXIConnectException badRequest( ErrorCode error, String message, Throwable cause) {
        return new XXIConnectException( error, message, cause );
    }

    private static XXIConnectException forbidden( ErrorCode error, String message, Throwable cause ) {
        return new XXIConnectException( error, message, cause );
    }

    private static XXIConnectException conflict(ErrorCode error, String message, Throwable cause) {
        return new XXIConnectException( error, message, cause );
    }

    private static XXIConnectException tooLarge(ErrorCode error, String message, Throwable cause) {
        return new XXIConnectException( error, message, cause );
    }

    private static XXIConnectException unavailable(ErrorCode error, String message, Throwable cause) {
        return new XXIConnectException( error, message, cause );
    }

    private static XXIConnectException internal(ErrorCode error, String message, Throwable cause) {
        return new XXIConnectException( error, message, cause );
    }

    public static XXIConnectException badXmlElemValue( ErrorCode error, String field, String message ) {
        return badXmlElemValue(error, field, message, null);
    }

    public static XXIConnectException badXmlElemValue(ErrorCode error, String field, String message, Throwable cause) {
        switch( error ) {
            case REQUEST_VALUE_MISSING, REQUEST_VALUE_TOO_LONG, REQUEST_VALUE_INVALID -> {
                final String objectField =
                        (field == null || field.isBlank())
                                ? error.objectField()
                                : field;

                final String errorMessage =
                        (message == null || message.isBlank())
                                ? error.externalMessage()
                                : message;

                return new XXIConnectException( error, objectField, errorMessage, cause );
            }
            default -> throw new IllegalArgumentException(
                    "Unsupported ErrorCode for badXmlElemValue: " + error
            );
        }
    }

    public static XXIConnectException badRequest(String errorMessage) {
        return badRequest( ErrorCode.REQUEST_INVALID, errorMessage, null);
    }

    public static XXIConnectException badRequest(Throwable cause, String errorMessage) {
        return badRequest(ErrorCode.REQUEST_INVALID, errorMessage, cause);
    }

    public static XXIConnectException forbidden(String errorMessage) {
        return forbidden(ErrorCode.FORBIDDEN, errorMessage, null);
    }

    public static XXIConnectException forbidden(Throwable cause, String errorMessage) {
        return forbidden(ErrorCode.FORBIDDEN, errorMessage, cause);
    }

    public static XXIConnectException conflict(String errorMessage) {
        return conflict(ErrorCode.CONFLICT, errorMessage, null);
    }

    public static XXIConnectException conflict(Throwable cause, String errorMessage) {
        return conflict(ErrorCode.CONFLICT, errorMessage, cause);
    }

    public static XXIConnectException tooLarge(String errorMessage) {
        return tooLarge(ErrorCode.REQUEST_PAYLOAD_TOO_LARGE, errorMessage, null);
    }

    public static XXIConnectException tooLarge( Throwable cause, String errorMessage) {
        return tooLarge(ErrorCode.REQUEST_PAYLOAD_TOO_LARGE, errorMessage, cause);
    }

    public static XXIConnectException unavailable(String errorMessage) {
        return unavailable(ErrorCode.SERVICE_UNAVAILABLE, errorMessage, null);
    }

    public static XXIConnectException unavailable(Throwable cause, String errorMessage) {
        return unavailable(ErrorCode.SERVICE_UNAVAILABLE, errorMessage, cause);
    }

    public static XXIConnectException internal(String errorMessage) {
        return internal(ErrorCode.INTERNAL_ERROR, errorMessage, null);
    }

    public static XXIConnectException internal(Throwable cause, String errorMessage) {
        return internal(ErrorCode.INTERNAL_ERROR, errorMessage, cause);
    }

    public static XXIConnectException fromConnector(XXIConnectorException e) {
        return switch (e.getReason()) {
            case DB_UNAVAILABLE ->
                    of(ErrorCode.TARGET_DB_UNAVAILABLE, e.getMessage(), e);

            case TECHNICAL_BREAK ->
                    of(ErrorCode.TARGET_DB_TECHNICAL_BREAK, e.getMessage(), e);

            case PASSWORD_EXPIRED ->
                    of(ErrorCode.AUTH_PASSWORD_EXPIRED, e.getMessage(), e);

            case PASSWORD_CHANGE_REQUIRED ->
                    of(ErrorCode.AUTH_PASSWORD_CHANGE_REQUIRED, e.getMessage(), e);

            case AUTH_METHOD_MISMATCH ->
                    of(ErrorCode.AUTH_METHOD_MISMATCH, e.getMessage(), e);

            case ENCRYPTION_ERROR ->
                    of(ErrorCode.CRYPTO_ENCRYPTION_ERROR, e.getMessage(), e);

            case DECRYPTION_ERROR ->
                    of(ErrorCode.CRYPTO_DECRYPTION_ERROR, e.getMessage(), e);

            case INTERNAL_ERROR ->
                    of(ErrorCode.INTERNAL_ERROR, e.getMessage(), e);

            default ->
                    of(ErrorCode.AUTH_DENIED, e.getMessage(), e);
        };
    }
}