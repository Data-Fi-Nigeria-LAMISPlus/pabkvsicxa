package com.lamiplus_common_api.exception;

import org.springframework.http.HttpStatus;

import java.util.List;

/**
 * A resolved error: the HTTP status and the {@link ErrorModel} list returned to the client.
 * The body stays a JSON array of {code, message, field} — the shape every client already parses.
 */
public record ApiError(HttpStatus status, List<ErrorModel> errors) {

    public ApiError {
        errors = List.copyOf(errors);
    }

    public static ApiError of(HttpStatus status, String code, String message) {
        return new ApiError(status, List.of(new ErrorModel(code, message, null)));
    }

    public static ApiError of(HttpStatus status, String code, String message, String field) {
        return new ApiError(status, List.of(new ErrorModel(code, message, field)));
    }

    public static ApiError of(ErrorCode code) {
        return of(code.getHttpStatus(), code.getCode(), code.getMessage());
    }

    public static ApiError of(ErrorCode code, String message) {
        return of(code.getHttpStatus(), code.getCode(), message);
    }

    /** Generic error for a status: status-based code and wording, no details. */
    public static ApiError forStatus(HttpStatus status) {
        return of(status, UserMessages.codeForStatus(status.value()), UserMessages.forStatus(status));
    }

    public boolean isServerError() {
        return status.is5xxServerError();
    }

    public String primaryCode() {
        return errors.isEmpty() ? null : errors.get(0).getCode();
    }
}
