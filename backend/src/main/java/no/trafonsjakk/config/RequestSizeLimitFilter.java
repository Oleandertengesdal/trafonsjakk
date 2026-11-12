package no.trafonsjakk.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Caps request bodies. Spring parses JSON bodies into memory and Tomcat sets no limit for them, so one
 * request to a public endpoint could otherwise fill the small server's memory. The largest real
 * request (20 000 characters of moves) is well under the limit.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
class RequestSizeLimitFilter extends OncePerRequestFilter {

    static final int MAX_BYTES = 128 * 1024;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (request.getContentLengthLong() > MAX_BYTES) {
            tooLarge(response);
            return;
        }
        // No (honest) Content-Length, e.g. chunked: count while reading
        LimitedRequest limited = new LimitedRequest(request);
        try {
            chain.doFilter(limited, response);
        } catch (Exception e) {
            if (limited.exceeded && !response.isCommitted()) {
                response.reset();
                tooLarge(response);
                return;
            }
            throw e;
        }
        if (limited.exceeded && !response.isCommitted()) {
            response.reset();
            tooLarge(response);
        }
    }

    private static void tooLarge(HttpServletResponse response) throws IOException {
        response.setStatus(413);
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.getWriter().write("{\"type\":\"about:blank\",\"title\":\"Content Too Large\",\"status\":413,"
                + "\"detail\":\"The request is too large.\",\"code\":\"request.too_large\"}");
    }

    private static final class LimitedRequest extends HttpServletRequestWrapper {
        boolean exceeded;
        private ServletInputStream stream;

        LimitedRequest(HttpServletRequest request) {
            super(request);
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            if (stream == null) {
                ServletInputStream in = super.getInputStream();
                stream = new ServletInputStream() {
                    private long read;

                    @Override
                    public int read() throws IOException {
                        int b = in.read();
                        if (b >= 0) {
                            count(1);
                        }
                        return b;
                    }

                    @Override
                    public int read(byte[] buf, int off, int len) throws IOException {
                        int n = in.read(buf, off, len);
                        if (n > 0) {
                            count(n);
                        }
                        return n;
                    }

                    private void count(int n) throws IOException {
                        read += n;
                        if (read > MAX_BYTES) {
                            exceeded = true;
                            throw new IOException("Request body too large");
                        }
                    }

                    @Override
                    public boolean isFinished() {
                        return in.isFinished();
                    }

                    @Override
                    public boolean isReady() {
                        return in.isReady();
                    }

                    @Override
                    public void setReadListener(ReadListener listener) {
                        in.setReadListener(listener);
                    }
                };
            }
            return stream;
        }
    }
}
