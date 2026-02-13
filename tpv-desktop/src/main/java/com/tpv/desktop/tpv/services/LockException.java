package com.tpv.desktop.tpv.services;

public class LockException extends RuntimeException {
    public enum Reason {
        OWNED_BY_OTHER,
        EXPIRED_OR_MISSING,
        AUTH,
        BACKEND
    }

    private final Reason reason;
    private final Integer httpStatus;

    public LockException(Reason reason, String message) {
        this(reason, message, null, null);
    }

    public LockException(Reason reason, String message, Integer httpStatus, Throwable cause) {
        super(message, cause);
        this.reason = reason == null ? Reason.BACKEND : reason;
        this.httpStatus = httpStatus;
    }

    public Reason reason() {
        return reason;
    }

    public Integer httpStatus() {
        return httpStatus;
    }

    public boolean isOwnershipConflict() {
        return reason == Reason.OWNED_BY_OTHER;
    }

    public boolean isRecoverableWithReacquire() {
        return reason == Reason.EXPIRED_OR_MISSING;
    }

    public boolean isAuthIssue() {
        return reason == Reason.AUTH;
    }
}
