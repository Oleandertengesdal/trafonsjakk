package no.trafonsjakk.common;

import jakarta.servlet.http.HttpServletRequest;
import java.sql.SQLException;
import java.sql.SQLTransientConnectionException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/** Turns exceptions into RFC 7807 problem responses without leaking internals. */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ProblemDetail> handleApi(ApiException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(ex.status(), ex.getMessage());
        pd.setProperty("code", ex.code());
        if (!ex.params().isEmpty()) {
            pd.setProperty("params", ex.params());
        }
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(ex.status());
        if (ex.retryAfter() != null) {
            builder.header(HttpHeaders.RETRY_AFTER, String.valueOf(Math.max(1, ex.retryAfter().toSeconds())));
        }
        return builder.body(pd);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ProblemDetail> handleIntegrity(DataIntegrityViolationException ex) {
        log.info("Data integrity violation: {}", ex.getMostSpecificCause().getMessage());
        return problem(HttpStatus.CONFLICT, "conflict", "That conflicts with existing data. Refresh and try again.");
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    ResponseEntity<ProblemDetail> handleOptimisticLock(OptimisticLockingFailureException ex) {
        return problem(HttpStatus.CONFLICT, "concurrent_change", "Someone else changed this at the same time. Refresh and try again.");
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ProblemDetail> handleAccessDenied(AccessDeniedException ex) {
        return problem(HttpStatus.FORBIDDEN, "forbidden", "You don't have permission to do that.");
    }

    /** The database can't be reached (Neon waking up, a network blip): worth retrying in a moment. */
    @ExceptionHandler({DataAccessResourceFailureException.class, CannotCreateTransactionException.class})
    ResponseEntity<ProblemDetail> handleDatabaseDown(Exception ex, HttpServletRequest request) {
        return databaseDown(ex, request.getMethod() + " " + request.getRequestURI());
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> handleUnexpected(Exception ex, HttpServletRequest request) {
        // An optimistic-lock failure can surface at commit time wrapped in JPA/Hibernate exceptions
        for (Throwable t = ex; t != null; t = t.getCause()) {
            if (t instanceof jakarta.persistence.OptimisticLockException || t instanceof org.hibernate.StaleStateException
                    || t instanceof OptimisticLockingFailureException) {
                return problem(HttpStatus.CONFLICT, "concurrent_change",
                        "Someone else changed this at the same time. Refresh and try again.");
            }
            if (t.getCause() == t) {
                break;
            }
        }
        if (isDatabaseDown(ex)) {
            return databaseDown(ex, request.getMethod() + " " + request.getRequestURI());
        }
        return serverError(ex, request.getMethod() + " " + request.getRequestURI());
    }

    /**
     * 500 with a reference that's both in the response and in the log line with the stack trace. Nothing
     * else about the failure is sent.
     */
    static ResponseEntity<ProblemDetail> serverError(Throwable ex, String where) {
        String ref = ErrorRef.next();
        log.error("Unhandled error [ref {}] {}", ref, where, ex);
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
                "Something went wrong on our side. Reference: " + ref);
        pd.setProperty("code", "server_error");
        pd.setProperty("ref", ref);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(pd);
    }

    static ResponseEntity<ProblemDetail> databaseDown(Throwable ex, String where) {
        String ref = ErrorRef.next();
        log.warn("Database unavailable [ref {}] {}: {}", ref, where, rootMessage(ex));
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
                "The database isn't answering right now. Try again in a few seconds.");
        pd.setProperty("code", "server.unavailable");
        pd.setProperty("ref", ref);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).header(HttpHeaders.RETRY_AFTER, "10").body(pd);
    }

    /** True if somewhere in the chain the database couldn't be reached (SQL state class 08, or a shutdown). */
    static boolean isDatabaseDown(Throwable ex) {
        for (Throwable t = ex; t != null; t = t.getCause() == t ? null : t.getCause()) {
            if (t instanceof DataAccessResourceFailureException || t instanceof CannotCreateTransactionException
                    || t instanceof SQLTransientConnectionException) {
                return true;
            }
            if (t instanceof SQLException sql && sql.getSQLState() != null
                    && (sql.getSQLState().startsWith("08") || sql.getSQLState().startsWith("57P"))) {
                return true;
            }
        }
        return false;
    }

    private static String rootMessage(Throwable ex) {
        Throwable root = ex;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root.getClass().getSimpleName() + ": " + root.getMessage();
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                  HttpHeaders headers, HttpStatusCode status,
                                                                  WebRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        Map<String, String> errorCodes = new LinkedHashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            errors.putIfAbsent(fe.getField(), fe.getDefaultMessage());
            // Constraint name (NotBlank, Size, Pattern, NotNull) so the client can translate the message
            errorCodes.putIfAbsent(fe.getField(), fe.getCode());
        }
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Validation failed");
        pd.setProperty("code", "validation");
        pd.setProperty("errors", errors);
        pd.setProperty("errorCodes", errorCodes);
        return ResponseEntity.badRequest().body(pd);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
                                                                  HttpHeaders headers, HttpStatusCode status,
                                                                  WebRequest request) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "The request body is missing or isn't valid JSON for this endpoint.");
        pd.setProperty("code", "request.malformed");
        return ResponseEntity.badRequest().body(pd);
    }

    /**
     * Errors Spring MVC raises itself (bad path variable, wrong method or content type, unknown
     * path, …) get a stable code too, like everything else the API returns.
     */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
                                                             HttpStatusCode statusCode, WebRequest request) {
        if (statusCode.is5xxServerError()) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(serverError(ex, request.getDescription(false)).getBody());
        }
        ResponseEntity<Object> response = super.handleExceptionInternal(ex, body, headers, statusCode, request);
        if (response != null && response.getBody() instanceof ProblemDetail pd
                && (pd.getProperties() == null || !pd.getProperties().containsKey("code"))) {
            pd.setProperty("code", codeForStatus(statusCode.value()));
        }
        return response;
    }

    static String codeForStatus(int status) {
        return switch (status) {
            case 400 -> "request.malformed";
            case 404 -> "not_found";
            case 405 -> "method_not_allowed";
            case 406 -> "not_acceptable";
            case 413 -> "request.too_large";
            case 415 -> "unsupported_media_type";
            default -> status >= 500 ? "server_error" : "request.rejected";
        };
    }

    private static ResponseEntity<ProblemDetail> problem(HttpStatus status, String code, String detail) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setProperty("code", code);
        return ResponseEntity.status(status).body(pd);
    }
}
