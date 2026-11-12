package no.trafonsjakk.common;

import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletRequestWrapper;
import jakarta.servlet.http.HttpServletRequest;
import java.util.regex.Pattern;

/**
 * Who sent a request, for rate limits.
 * <p>
 * {@code edge} is the address that connected to Render's edge. Render overwrites {@code True-Client-IP}
 * with it, so it can't be faked. Behind the Vercel rewrite it's one of Vercel's servers, shared by
 * everyone who uses the site.
 * <p>
 * {@code client} is the visitor: the X-Forwarded-For entry just before the edge address. Through
 * Vercel that's the visitor's address as Vercel saw it (Vercel replaces any X-Forwarded-For the
 * visitor sends). Someone talking to Render directly can put anything there, which is why the
 * edge address gets a limit of its own too.
 * <p>
 * Never use the leftmost X-Forwarded-For entry: whoever sends the request chooses it. That rules out
 * {@code request.getRemoteAddr()} too: with {@code forward-headers-strategy: framework} Spring sets it from that
 * very entry (and hides the X-Forwarded-* headers), so this reads the request as Tomcat received it instead.
 * Without {@code True-Client-IP} (local development) both are the socket address.
 */
public record ClientAddress(String client, String edge) {

    public static final String EDGE_HEADER = "True-Client-IP";
    private static final Pattern IP = Pattern.compile("[0-9A-Fa-f:.]{2,45}");

    public static ClientAddress of(HttpServletRequest request) {
        ServletRequest received = request;
        while (received instanceof ServletRequestWrapper wrapper) {
            received = wrapper.getRequest();
        }
        HttpServletRequest raw = received instanceof HttpServletRequest http ? http : request;
        return from(raw.getHeader(EDGE_HEADER), raw.getHeader("X-Forwarded-For"), raw.getRemoteAddr());
    }

    static ClientAddress from(String edgeHeader, String forwardedFor, String remoteAddr) {
        String edge = valid(edgeHeader);
        if (edge == null) {
            return new ClientAddress(remoteAddr, remoteAddr);
        }
        String client = edge;
        if (forwardedFor != null) {
            String[] hops = forwardedFor.split(",");
            for (int i = hops.length - 1; i > 0; i--) {
                if (edge.equals(hops[i].trim())) {
                    String before = valid(hops[i - 1]);
                    if (before != null) {
                        client = before;
                    }
                    break;
                }
            }
        }
        return new ClientAddress(client, edge);
    }

    private static String valid(String value) {
        if (value == null) {
            return null;
        }
        String v = value.trim();
        return IP.matcher(v).matches() ? v : null;
    }
}
