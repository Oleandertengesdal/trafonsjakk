package no.trafonsjakk.common;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Errors that happen outside Spring MVC (in a servlet filter, for example the session check when the
 * database is down) are forwarded here by the server. Replaces Spring Boot's default error JSON so
 * every error the API returns has the same shape and a {@code code}, and 5xx ones a {@code ref}.
 */
@RestController
public class ProblemErrorController implements ErrorController {

    @RequestMapping("/error")
    ResponseEntity<ProblemDetail> error(HttpServletRequest request) {
        Object uri = request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI);
        String where = request.getMethod() + " " + (uri != null ? uri : "?");
        if (request.getAttribute(RequestDispatcher.ERROR_EXCEPTION) instanceof Throwable ex) {
            return GlobalExceptionHandler.isDatabaseDown(ex)
                    ? GlobalExceptionHandler.databaseDown(ex, where)
                    : GlobalExceptionHandler.serverError(ex, where);
        }
        // Nothing was forwarded: someone opened /error directly
        HttpStatus status = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE) instanceof Integer code
                && HttpStatus.resolve(code) != null ? HttpStatus.resolve(code) : HttpStatus.NOT_FOUND;
        if (status.is5xxServerError()) {
            return GlobalExceptionHandler.serverError(new IllegalStateException("HTTP " + status.value()), where);
        }
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, status.getReasonPhrase());
        pd.setProperty("code", GlobalExceptionHandler.codeForStatus(status.value()));
        return ResponseEntity.status(status).body(pd);
    }
}
