package com.example.seats.exception;

public class ApiException extends RuntimeException {
    private final int status;
    private final String code;

    public ApiException(int status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public int status() {
        return status;
    }

    public String code() {
        return code;
    }

    public static ApiException idempotencyKeyReuse() {
        return new ApiException(409, "idempotency_key_reuse", "This idempotency key was already used with a different request.");
    }

    public static ApiException perUserLimit(int limit) {
        return new ApiException(409, "per_user_limit_exceeded", "You can reserve at most " + limit + " seats for this show.");
    }
}
