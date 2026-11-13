package no.trafonsjakk.common;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.RequestDispatcher;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.transaction.CannotCreateTransactionException;

class ErrorResponsesTest {

    private static final String REF = "[23456789ABCDEFGHJKMNPQRSTUVWXYZ]{6}";

    @Test
    void anUnexpectedErrorGetsAReferenceAndNothingElse() {
        ResponseEntity<ProblemDetail> r = GlobalExceptionHandler.serverError(
                new IllegalStateException("password_hash column is null for kari"), "GET /api/x");
        assertThat(r.getStatusCode().value()).isEqualTo(500);
        ProblemDetail pd = r.getBody();
        assertThat(pd.getProperties()).containsEntry("code", "server_error");
        assertThat((String) pd.getProperties().get("ref")).matches(REF);
        assertThat(pd.getDetail()).endsWith((String) pd.getProperties().get("ref")).doesNotContain("password", "kari");
    }

    @Test
    void referencesDontRepeat() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            seen.add(ErrorRef.next());
        }
        assertThat(seen).hasSizeGreaterThan(995).allMatch(ref -> ref.matches(REF));
    }

    @Test
    void aDatabaseThatDoesNotAnswerIsTemporary() {
        assertThat(GlobalExceptionHandler.isDatabaseDown(new CannotCreateTransactionException("no connection"))).isTrue();
        assertThat(GlobalExceptionHandler.isDatabaseDown(
                new RuntimeException(new SQLException("Connection refused", "08001")))).isTrue();
        assertThat(GlobalExceptionHandler.isDatabaseDown(new SQLException("terminating connection", "57P01"))).isTrue();
        assertThat(GlobalExceptionHandler.isDatabaseDown(new SQLException("duplicate key", "23505"))).isFalse();
        assertThat(GlobalExceptionHandler.isDatabaseDown(new IllegalStateException())).isFalse();

        ResponseEntity<ProblemDetail> r = GlobalExceptionHandler.databaseDown(new CannotCreateTransactionException("x"), "GET /api/x");
        assertThat(r.getStatusCode().value()).isEqualTo(503);
        assertThat(r.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("10");
        assertThat(r.getBody().getProperties()).containsEntry("code", "server.unavailable");
    }

    private static MockHttpServletRequest forwarded(Integer status, Throwable ex) {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/error");
        req.setAttribute(RequestDispatcher.ERROR_REQUEST_URI, "/api/games");
        if (status != null) {
            req.setAttribute(RequestDispatcher.ERROR_STATUS_CODE, status);
        }
        if (ex != null) {
            req.setAttribute(RequestDispatcher.ERROR_EXCEPTION, ex);
        }
        return req;
    }

    @Test
    void errorsOutsideSpringMvcGetTheSameShape() {
        ProblemErrorController controller = new ProblemErrorController();

        ResponseEntity<ProblemDetail> crash = controller.error(forwarded(500, new NullPointerException()));
        assertThat(crash.getStatusCode().value()).isEqualTo(500);
        assertThat(crash.getBody().getProperties()).containsEntry("code", "server_error").containsKey("ref");

        ResponseEntity<ProblemDetail> dbDown = controller.error(forwarded(500,
                new IllegalStateException(new SQLException("Connection attempt timed out", "08001"))));
        assertThat(dbDown.getStatusCode().value()).isEqualTo(503);
        assertThat(dbDown.getBody().getProperties()).containsEntry("code", "server.unavailable");

        ResponseEntity<ProblemDetail> badRequest = controller.error(forwarded(400, null));
        assertThat(badRequest.getStatusCode().value()).isEqualTo(400);
        assertThat(badRequest.getBody().getProperties()).containsEntry("code", "request.malformed");

        ResponseEntity<ProblemDetail> direct = controller.error(new MockHttpServletRequest("GET", "/error"));
        assertThat(direct.getStatusCode().value()).isEqualTo(404);
        assertThat(direct.getBody().getProperties()).containsEntry("code", "not_found");
    }
}
