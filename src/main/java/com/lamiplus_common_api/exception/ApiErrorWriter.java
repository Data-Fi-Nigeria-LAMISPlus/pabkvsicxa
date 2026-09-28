package com.lamiplus_common_api.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Writes an {@link ApiError} straight to the servlet response, for code that runs before Spring
 * MVC (servlet filters) and so can't throw into {@link GlobalExceptionHandler}. Produces exactly
 * the same body and headers as the handler: a JSON array of {code, message, field} plus
 * {@value ErrorReference#HEADER}.
 */
public final class ApiErrorWriter {

    private ApiErrorWriter() {
    }

    public static void write(HttpServletRequest request, HttpServletResponse response, ApiError error)
            throws IOException {
        if (response.isCommitted()) return;
        String ref = ErrorReference.of(request);

        response.resetBuffer();
        response.setStatus(error.status().value());
        response.setHeader(ErrorReference.HEADER, ref);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(toJson(error.errors(), error.isServerError() ? ref : null));
        response.flushBuffer();
    }

    /** Serialized by hand: three string fields don't justify a dependency on a JSON mapper. */
    static String toJson(List<ErrorModel> errors, String ref) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < errors.size(); i++) {
            ErrorModel e = errors.get(i);
            String message = ref == null ? e.getMessage() : e.getMessage() + " (Ref: " + ref + ")";
            if (i > 0) sb.append(',');
            sb.append("{\"code\":").append(quote(e.getCode()))
                    .append(",\"message\":").append(quote(message))
                    .append(",\"field\":").append(quote(e.getField()))
                    .append('}');
        }
        return sb.append(']').toString();
    }

    private static String quote(String value) {
        if (value == null) return "null";
        StringBuilder sb = new StringBuilder(value.length() + 2).append('"');
        for (char c : value.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        return sb.append('"').toString();
    }
}
