package com.lamiplus_common_api.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;

import java.util.concurrent.ThreadLocalRandom;

/**
 * A short reference that ties what the user sees to the server log line for the same failure.
 * Returned in the {@value #HEADER} header on every error and, for server errors, appended to the
 * message ("... (Ref: 7K3QX9PA)") so a user can quote it to support.
 */
public final class ErrorReference {

    public static final String HEADER = "X-Error-Ref";

    private static final String ATTRIBUTE = ErrorReference.class.getName();
    private static final char[] ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray(); // no 0/O/1/I

    private ErrorReference() {
    }

    /** Reference for this request: the tracing id if one is present, else a new random one. */
    public static String of(HttpServletRequest request) {
        Object existing = request.getAttribute(ATTRIBUTE);
        if (existing instanceof String ref) return ref;

        String ref = fromTracing();
        if (ref == null) ref = random();
        request.setAttribute(ATTRIBUTE, ref);
        return ref;
    }

    private static String fromTracing() {
        String traceId = MDC.get("traceId");
        if (traceId == null || traceId.isBlank()) return null;
        // Full trace ids are 32 hex chars — the first 12 are unique enough to search logs by.
        return traceId.length() > 12 ? traceId.substring(0, 12) : traceId;
    }

    private static String random() {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        char[] out = new char[8];
        for (int i = 0; i < out.length; i++) {
            out[i] = ALPHABET[rnd.nextInt(ALPHABET.length)];
        }
        return new String(out);
    }
}
