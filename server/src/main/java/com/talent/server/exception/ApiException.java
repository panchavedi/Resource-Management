package com.talent.server.exception;

import java.util.Map;

/**
 * Single exception type for expected, client-facing failures. The {@link ErrorCode} determines the
 * HTTP status; {@code details} carries structured context (e.g. current load, requested percent)
 * so the UI can explain the failure without parsing the message.
 */
public class ApiException extends RuntimeException {

    private final ErrorCode code;
    private final transient Map<String, Object> details;

    public ApiException(ErrorCode code, String message) {
        this(code, message, Map.of());
    }

    public ApiException(ErrorCode code, String message, Map<String, Object> details) {
        super(message);
        this.code = code;
        this.details = details == null ? Map.of() : Map.copyOf(details);
    }

    public static ApiException notFound(String resource, Object id) {
        return new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "%s %s was not found".formatted(resource, id),
                Map.of("resource", resource, "id", String.valueOf(id)));
    }

    public static ApiException duplicate(String message) {
        return new ApiException(ErrorCode.DUPLICATE_RESOURCE, message);
    }

    public ErrorCode getCode() {
        return code;
    }

    public Map<String, Object> getDetails() {
        return details;
    }
}
