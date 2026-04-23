package ru.inversion.msrv.validation;

import ru.inversion.utils.IExceptionInfo;

import java.util.Objects;

/** */
public class XXIConnectException extends RuntimeException implements IExceptionInfo {

    private final Errors.ErrorCode error;
    private final String objectField;

    public XXIConnectException(Errors.ErrorCode error, String message) {
        this(error, null, message, null);
    }

    public XXIConnectException(Errors.ErrorCode error, String message, Throwable cause) {
        this(error, null, message, cause);
    }

    public XXIConnectException(Errors.ErrorCode error, String objectField, String message) {
        this(error, objectField, message, null);
    }

    public XXIConnectException( Errors.ErrorCode error, String objectField, String message, Throwable cause) {
        super(message, cause);
        this.error = Objects.requireNonNull(error, "error");
        this.objectField = objectField;
    }

    @Override
    public String getCategory() {
        return "XXIConnect";
    }

    @Override
    public boolean isResolved() {
        return true;
    }

    public Errors.ErrorCode error( ) {
        return error;
    }

    public int getHttpStatus() {
        return error.httpStatus();
    }

    public String getObjectAlias() {
        return error.objectAlias();
    }

    public String getObjectField() {
        return (objectField == null || objectField.isBlank()) ? error.objectField() : objectField;
    }

    public String getErrorCode() {
        return error.code();
    }

    public Errors.LogPolicy getLogPolicy() {
        return error.logPolicy();
    }
}