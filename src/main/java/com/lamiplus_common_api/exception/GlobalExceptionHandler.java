package com.lamiplus_common_api.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Converts every exception thrown by a controller (core or plugin) into a user-facing response.
 *
 * <p>Response body: a JSON array of {@link ErrorModel} {@code {code, message, field}} — the first
 * entry's message is the headline, entries with a {@code field} belong to that form field.
 * Messages are plain language; technical details (SQL, class names, internal state) are only
 * logged. Every error carries an {@value ErrorReference#HEADER} header, and server errors also
 * mention it in the message, so a user report can be matched to the exact log line.
 *
 * <p>All mapping rules live in {@link ApiErrorResolver}; this class only adds logging and HTTP
 * plumbing. Errors raised outside controllers (filters, Spring Security) are handled with the
 * same rules by {@link ApiErrorController}.
 */
@Slf4j
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private final ApiErrorResolver resolver;

    /**
     * Unknown API path → 404 JSON. Anything else is a client-side (SPA) route: serve index.html
     * so deep links and page refreshes work.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<?> handleNoResource(NoResourceFoundException ex, HttpServletRequest request,
                                              HttpServletResponse response) {
        if (isApiRequest(request)) {
            return respond(ex, request, response);
        }
        try {
            Resource index = new ClassPathResource("static/index.html");
            if (index.exists()) {
                String content = StreamUtils.copyToString(index.getInputStream(), StandardCharsets.UTF_8);
                return ResponseEntity.ok()
                        .contentType(MediaType.TEXT_HTML)
                        .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate")
                        .body(content);
            }
        } catch (IOException e) {
            log.error("Failed to load index.html", e);
        }
        return ResponseEntity.status(HttpStatus.FOUND).header(HttpHeaders.LOCATION, "/").build();
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<List<ErrorModel>> handleAll(Exception ex, HttpServletRequest request,
                                                      HttpServletResponse response) {
        if (isClientDisconnect(ex)) {
            // The client went away (closed tab, cancelled request) — nothing can be sent back.
            log.debug("Client disconnected during {} {}: {}", request.getMethod(), request.getRequestURI(), ex.toString());
            return null;
        }
        return respond(ex, request, response);
    }

    private ResponseEntity<List<ErrorModel>> respond(Throwable ex, HttpServletRequest request,
                                                     HttpServletResponse response) {
        ApiError error = resolver.resolve(ex);
        String ref = ErrorReference.of(request);
        log(error, ex, request, ref);
        return toResponse(error, ref, response);
    }

    /** Shared with {@link ApiErrorController} so both produce identical responses. */
    static ResponseEntity<List<ErrorModel>> toResponse(ApiError error, String ref, HttpServletResponse response) {
        List<ErrorModel> body = error.errors();
        if (error.isServerError()) {
            // Give the user something to quote to support; the log line carries the same ref.
            body = new ArrayList<>(body.size());
            for (ErrorModel e : error.errors()) {
                body.add(new ErrorModel(e.getCode(), e.getMessage() + " (Ref: " + ref + ")", e.getField()));
            }
        }
        return ResponseEntity.status(error.status())
                .header(ErrorReference.HEADER, ref)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body);
    }

    /**
     * 5xx → ERROR with stack trace (something is broken). 4xx → one WARN line, no stack trace
     * (the request was wrong; the stack would only add noise). The original exception message is
     * always logged, since the response deliberately doesn't contain it.
     */
    static void log(ApiError error, Throwable ex, HttpServletRequest request, String ref) {
        String where = request.getMethod() + " " + request.getRequestURI();
        if (error.isServerError()) {
            log.error("[ref={}] {} {} at {} — {}", ref, error.status().value(), error.primaryCode(), where,
                    describe(ex), ex);
        } else {
            log.warn("[ref={}] {} {} at {} — {}", ref, error.status().value(), error.primaryCode(), where,
                    describe(ex));
        }
    }

    private static String describe(Throwable ex) {
        Throwable root = ex;
        while (root.getCause() != null && root.getCause() != root) root = root.getCause();
        String outer = ex.getClass().getSimpleName() + ": " + ex.getMessage();
        return root == ex ? outer : outer + " | root cause " + root.getClass().getSimpleName() + ": " + root.getMessage();
    }

    static boolean isApiRequest(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path != null && (path.startsWith("/api/") || path.contains("/api/"))) return true;
        String accept = request.getHeader(HttpHeaders.ACCEPT);
        return accept != null && accept.contains(MediaType.APPLICATION_JSON_VALUE) && !accept.contains(MediaType.TEXT_HTML_VALUE);
    }

    private static boolean isClientDisconnect(Throwable ex) {
        for (Throwable t = ex; t != null; t = t.getCause() == t ? null : t.getCause()) {
            String name = t.getClass().getSimpleName();
            if (name.equals("ClientAbortException") || name.equals("AsyncRequestNotUsableException")) return true;
            if (t instanceof IOException && t.getMessage() != null) {
                String msg = t.getMessage().toLowerCase();
                if (msg.contains("broken pipe") || msg.contains("connection reset by peer")) return true;
            }
        }
        return false;
    }
}
