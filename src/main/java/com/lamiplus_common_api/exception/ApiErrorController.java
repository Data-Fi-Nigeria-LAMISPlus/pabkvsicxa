package com.lamiplus_common_api.exception;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Handles the servlet container's error dispatch ({@code /error}) — errors that never reached a
 * controller, so {@link GlobalExceptionHandler} can't see them: {@code response.sendError(...)}
 * from filters (tenant resolution, JWT), Spring Security's 401/403, and exceptions escaping a
 * filter. Uses the same {@link ApiErrorResolver} rules and the same {code, message, field} array
 * body, so clients get one consistent error format.
 *
 * <p>Registering an {@link ErrorController} makes Spring Boot's BasicErrorController back off.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("${server.error.path:${error.path:/error}}")
public class ApiErrorController implements ErrorController {

    /** Where Spring Boot's DefaultErrorAttributes stores the exception it saw. */
    private static final String BOOT_ERROR_ATTRIBUTE =
            "org.springframework.boot.webmvc.error.DefaultErrorAttributes.ERROR";

    private final ApiErrorResolver resolver;

    @RequestMapping
    public ResponseEntity<List<ErrorModel>> error(HttpServletRequest request, HttpServletResponse response) {
        HttpStatus status = statusOf(request);
        Throwable ex = exceptionOf(request);
        String ref = ErrorReference.of(request);
        String originalUri = String.valueOf(request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI));

        ApiError error = ex != null ? resolver.resolve(ex) : ApiError.forStatus(status);

        // The reason given to sendError() is for developers ("Tenant mismatch", "Invalid tenant
        // format", ...) — logged here, never returned.
        Object reason = request.getAttribute(RequestDispatcher.ERROR_MESSAGE);
        if (ex != null) {
            GlobalExceptionHandler.log(error, ex, request, ref);
        } else if (error.isServerError()) {
            log.error("[ref={}] {} at {} {} — {}", ref, status.value(), request.getMethod(), originalUri, reason);
        } else {
            log.warn("[ref={}] {} at {} {} — {}", ref, status.value(), request.getMethod(), originalUri, reason);
        }
        return GlobalExceptionHandler.toResponse(error, ref, response);
    }

    private static HttpStatus statusOf(HttpServletRequest request) {
        Object code = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        if (code instanceof Integer value) {
            HttpStatus status = HttpStatus.resolve(value);
            if (status != null) return status;
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }

    private static Throwable exceptionOf(HttpServletRequest request) {
        Object ex = request.getAttribute(BOOT_ERROR_ATTRIBUTE);
        if (ex instanceof Throwable t) return t;
        ex = request.getAttribute(RequestDispatcher.ERROR_EXCEPTION);
        return ex instanceof Throwable t ? t : null;
    }
}
