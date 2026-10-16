package com.talent.server.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.data.core.PropertyReferenceException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Translates every failure into an RFC 9457 problem document with a stable {@code code}.
 * Internal details (SQL, stack traces) are logged, never returned to the client.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ProblemDetail> handleApi(ApiException ex, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetails.of(ex.getCode(), ex.getMessage(), request);
        if (!ex.getDetails().isEmpty()) {
            problem.setProperty("details", ex.getDetails());
        }
        return respond(problem);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ProblemDetail> handleBodyValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<ProblemDetails.FieldViolation> violations = new ArrayList<>();
        ex.getBindingResult().getFieldErrors().forEach(error -> violations.add(
                new ProblemDetails.FieldViolation(error.getField(), error.getDefaultMessage())));
        ex.getBindingResult().getGlobalErrors().forEach(error -> violations.add(
                new ProblemDetails.FieldViolation(error.getObjectName(), error.getDefaultMessage())));
        return validationProblem(violations, request);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    ResponseEntity<ProblemDetail> handleParameterValidation(HandlerMethodValidationException ex, HttpServletRequest request) {
        List<ProblemDetails.FieldViolation> violations = ex.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream().map(error -> new ProblemDetails.FieldViolation(
                        result.getMethodParameter().getParameterName(), error.getDefaultMessage())))
                .toList();
        return validationProblem(violations, request);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ProblemDetail> handleConstraintViolation(ConstraintViolationException ex, HttpServletRequest request) {
        List<ProblemDetails.FieldViolation> violations = ex.getConstraintViolations().stream()
                .map(v -> new ProblemDetails.FieldViolation(lastPathNode(v.getPropertyPath().toString()), v.getMessage()))
                .toList();
        return validationProblem(violations, request);
    }

    @ExceptionHandler({MethodArgumentTypeMismatchException.class, MissingServletRequestParameterException.class,
            MissingRequestHeaderException.class})
    ResponseEntity<ProblemDetail> handleBadParameter(Exception ex, HttpServletRequest request) {
        String field = switch (ex) {
            case MethodArgumentTypeMismatchException e -> e.getName();
            case MissingServletRequestParameterException e -> e.getParameterName();
            case MissingRequestHeaderException e -> e.getHeaderName();
            default -> "request";
        };
        String message = ex instanceof MethodArgumentTypeMismatchException
                ? "has an invalid value" : "is required";
        return validationProblem(List.of(new ProblemDetails.FieldViolation(field, message)), request);
    }

    @ExceptionHandler(PropertyReferenceException.class)
    ResponseEntity<ProblemDetail> handleBadSort(PropertyReferenceException ex, HttpServletRequest request) {
        return validationProblem(List.of(new ProblemDetails.FieldViolation("sort",
                "unknown property '%s'".formatted(ex.getPropertyName()))), request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ProblemDetail> handleUnreadable(HttpMessageNotReadableException ex, HttpServletRequest request) {
        log.debug("Unreadable request body: {}", ex.getMessage());
        return respond(ProblemDetails.of(ErrorCode.MALFORMED_REQUEST,
                "Request body is missing or malformed. Check JSON syntax, enum values and ISO-8601 dates (yyyy-MM-dd).",
                request));
    }

    @ExceptionHandler({OptimisticLockingFailureException.class, PessimisticLockingFailureException.class})
    ResponseEntity<ProblemDetail> handleConcurrency(Exception ex, HttpServletRequest request) {
        log.info("Concurrent modification detected on {}: {}", request.getRequestURI(), ex.getMessage());
        return respond(ProblemDetails.of(ErrorCode.CONCURRENT_MODIFICATION,
                "The record was changed by another request. Reload and try again.", request));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ProblemDetail> handleIntegrity(DataIntegrityViolationException ex, HttpServletRequest request) {
        String cause = String.valueOf(ex.getMostSpecificCause().getMessage()).toLowerCase(Locale.ROOT);
        log.info("Integrity violation on {}: {}", request.getRequestURI(), cause);
        if (cause.contains("uq_allocation_active_employee_demand")) {
            return respond(ProblemDetails.of(ErrorCode.ALLOCATION_ALREADY_EXISTS,
                    "This employee already has an active allocation for the demand.", request));
        }
        return respond(ProblemDetails.of(ErrorCode.DUPLICATE_RESOURCE,
                "The request conflicts with existing data (duplicate or referenced record).", request));
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ProblemDetail> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        return respond(ProblemDetails.of(ErrorCode.ACCESS_DENIED,
                "Your role is not permitted to perform this action.", request));
    }

    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<ProblemDetail> handleAuthentication(AuthenticationException ex, HttpServletRequest request) {
        return respond(ProblemDetails.of(ErrorCode.INVALID_CREDENTIALS, "Invalid username or password.", request));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ProblemDetail> handleNoResource(NoResourceFoundException ex, HttpServletRequest request) {
        return respond(ProblemDetails.of(ErrorCode.RESOURCE_NOT_FOUND, "No endpoint at " + request.getRequestURI(), request));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ProblemDetail> handleMethod(HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetails.of(ErrorCode.MALFORMED_REQUEST, ex.getMessage(), request);
        problem.setStatus(HttpStatus.METHOD_NOT_ALLOWED.value());
        return respond(problem);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<ProblemDetail> handleMediaType(HttpMediaTypeNotSupportedException ex, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetails.of(ErrorCode.MALFORMED_REQUEST, "Content-Type must be application/json", request);
        problem.setStatus(HttpStatus.UNSUPPORTED_MEDIA_TYPE.value());
        return respond(problem);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled error on {} {}", request.getMethod(), request.getRequestURI(), ex);
        return respond(ProblemDetails.of(ErrorCode.INTERNAL_ERROR,
                "An unexpected error occurred. The incident has been logged.", request));
    }

    private ResponseEntity<ProblemDetail> validationProblem(List<ProblemDetails.FieldViolation> violations,
                                                            HttpServletRequest request) {
        ProblemDetail problem = ProblemDetails.of(ErrorCode.VALIDATION_FAILED,
                "One or more fields are invalid.", request);
        problem.setProperty("errors", violations);
        return respond(problem);
    }

    private static ResponseEntity<ProblemDetail> respond(ProblemDetail problem) {
        return ResponseEntity.status(problem.getStatus()).body(problem);
    }

    private static String lastPathNode(String path) {
        int dot = path.lastIndexOf('.');
        return dot >= 0 ? path.substring(dot + 1) : path;
    }
}
