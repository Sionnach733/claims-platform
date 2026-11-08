package com.example.claims.api.error;

import com.example.claims.service.BusinessRuleViolationException;
import com.example.claims.service.InvalidRequestException;
import com.example.claims.service.ResourceNotFoundException;
import java.net.URI;
import java.util.Comparator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.core.PropertyReferenceException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Every error leaves the API as an RFC 7807 {@code application/problem+json} body.
 * Framework errors (malformed JSON, type mismatches, 404/405) are handled by the base class.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String TYPE_BASE = "https://claims.example.com/problems/";

    public record FieldViolation(String field, String message) {
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                  HttpHeaders headers, HttpStatusCode status,
                                                                  WebRequest request) {
        List<FieldViolation> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new FieldViolation(fe.getField(), fe.getDefaultMessage()))
                .sorted(Comparator.comparing(FieldViolation::field))
                .toList();
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "validation-error", "Validation failed",
                "The request contains %d invalid field(s)".formatted(errors.size()));
        problem.setProperty("errors", errors);
        return handleExceptionInternal(ex, problem, headers, HttpStatus.BAD_REQUEST, request);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    ProblemDetail notFound(ResourceNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "not-found", "Resource not found", ex.getMessage());
    }

    @ExceptionHandler(BusinessRuleViolationException.class)
    ProblemDetail businessRule(BusinessRuleViolationException ex) {
        ProblemDetail problem = problem(HttpStatus.UNPROCESSABLE_CONTENT, "business-rule-violation",
                "Business rule violated", ex.getMessage());
        problem.setProperty("code", ex.getCode());
        return problem;
    }

    @ExceptionHandler(InvalidRequestException.class)
    ProblemDetail invalidRequest(InvalidRequestException ex) {
        return problem(HttpStatus.BAD_REQUEST, "invalid-request", "Invalid request", ex.getMessage());
    }

    /** e.g. ?sort=doesNotExist: a client error, not a 500. */
    @ExceptionHandler(PropertyReferenceException.class)
    ProblemDetail badSortProperty(PropertyReferenceException ex) {
        return problem(HttpStatus.BAD_REQUEST, "invalid-request", "Invalid request",
                "Unknown property '%s'".formatted(ex.getPropertyName()));
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    ProblemDetail concurrentUpdate(ObjectOptimisticLockingFailureException ex) {
        return problem(HttpStatus.CONFLICT, "concurrent-modification", "Concurrent modification",
                "The resource was modified by another request; reload and retry");
    }

    /** Last resort: log the detail server-side and never leak internals to the client. */
    @ExceptionHandler(Exception.class)
    ProblemDetail unexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "internal-error", "Internal server error",
                "An unexpected error occurred");
    }

    private static ProblemDetail problem(HttpStatus status, String type, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create(TYPE_BASE + type));
        problem.setTitle(title);
        return problem;
    }
}
