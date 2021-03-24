package ru.inurgalimov.di.exception;

public class InstanceRetrievalException extends RuntimeException {

    public InstanceRetrievalException() {
        super();
    }

    public InstanceRetrievalException(String message) {
        super(message);
    }

    public InstanceRetrievalException(String message, Throwable cause) {
        super(message, cause);
    }

    public InstanceRetrievalException(Throwable cause) {
        super(cause);
    }

    protected InstanceRetrievalException(String message, Throwable cause, boolean enableSuppression,
                                         boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
