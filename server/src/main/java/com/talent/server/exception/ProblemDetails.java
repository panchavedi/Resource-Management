package com.talent.server.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ProblemDetail;

import java.net.URI;
import java.time.Instant;
import java.util.Locale;

/**
 * Builds the uniform error body used by controllers and by the security entry points.
 * <pre>
 * { "type": "urn:talent:error:capacity-exceeded", "title": "Unprocessable Content", "status": 422,
 *   "detail": "...", "instance": "/api/allocations", "code": "CAPACITY_EXCEEDED",
 *   "timestamp": "...", "errors": [...], "details": {...} }
 * </pre>
 */
public final class ProblemDetails {

    private ProblemDetails() {
    }

    public record FieldViolation(String field, String message) {
    }

    public static ProblemDetail of(ErrorCode code, String detail, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(code.status(), detail);
        problem.setType(URI.create("urn:talent:error:" + code.name().toLowerCase(Locale.ROOT).replace('_', '-')));
        problem.setTitle(code.status().getReasonPhrase());
        if (request != null) {
            problem.setInstance(URI.create(request.getRequestURI()));
        }
        problem.setProperty("code", code.name());
        problem.setProperty("timestamp", Instant.now().toString());
        return problem;
    }
}
