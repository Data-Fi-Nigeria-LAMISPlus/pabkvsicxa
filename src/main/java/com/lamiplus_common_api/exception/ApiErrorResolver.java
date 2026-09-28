package com.lamiplus_common_api.exception;

import com.lamiplus_common_api.api.PluginException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.dao.InvalidDataAccessResourceUsageException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AccountExpiredException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.CredentialsExpiredException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingPathVariableException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.UndeclaredThrowableException;
import java.time.temporal.Temporal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Turns any exception into the {@link ApiError} returned to the client. Single source of truth
 * for status codes and wording — used by {@link GlobalExceptionHandler} for controller errors and
 * by {@link ApiErrorController} for errors raised outside controllers (filters, security).
 *
 * <p>The whole cause chain is inspected, not just the outermost exception: plugin calls run on an
 * executor (CompletionException/ExecutionException), transactions wrap validation errors, and
 * services wrap database errors in RuntimeException. Precise mappings (business, validation,
 * security, database, web) are looked for first anywhere in the chain; only if none is found are
 * the weaker, message-based ones (IllegalArgument/IllegalState, naming conventions) used.
 */
@Component
public class ApiErrorResolver {

    private static final int MAX_ENUM_VALUES_SHOWN = 10;
    private static final Pattern ENUM_CONSTANT = Pattern.compile("No enum constant [\\w.$]+\\.(\\w+)");
    private static final Pattern INVALID_UUID = Pattern.compile("Invalid UUID string: (.{0,60})");

    /** Resolve an exception to status + user-facing errors. Never returns null. */
    public ApiError resolve(Throwable ex) {
        List<Throwable> chain = causeChain(ex);

        for (Throwable t : chain) {
            ApiError precise = precise(t);
            if (precise != null) return precise;
        }
        for (Throwable t : chain) {
            ApiError byConvention = byConvention(t);
            if (byConvention != null) return byConvention;
        }
        return ApiError.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    // =====================================================================================
    // Precise mappings — the exception type fully determines the response
    // =====================================================================================

    private ApiError precise(Throwable t) {
        if (t instanceof BusinessException be) return business(be);
        if (t instanceof PluginServiceUnavailableException) {
            return ApiError.of(HttpStatus.SERVICE_UNAVAILABLE, StandardErrorCodes.SERVICE_UNAVAILABLE.getCode(),
                    "This module is temporarily unavailable. Please try again shortly.");
        }

        // ---- validation / request input ----
        if (t instanceof BindException be) return validation(be.getFieldErrors(), be.getGlobalErrors());
        if (t instanceof HandlerMethodValidationException hmv) return methodValidation(hmv);
        if (t instanceof ConstraintViolationException cve) return constraintViolations(cve.getConstraintViolations());
        if (t instanceof HttpMessageNotReadableException nr) return unreadableBody(nr);
        if (t instanceof MethodArgumentTypeMismatchException tm) {
            return ApiError.of(HttpStatus.BAD_REQUEST, StandardErrorCodes.INVALID_INPUT_FORMAT.getCode(),
                    invalidValue(tm.getName(), tm.getValue(), tm.getRequiredType()), tm.getName());
        }
        if (t instanceof MissingServletRequestParameterException mp) {
            return ApiError.of(HttpStatus.BAD_REQUEST, StandardErrorCodes.MISSING_REQUIRED_FIELD.getCode(),
                    UserMessages.label(mp.getParameterName()) + " is required.", mp.getParameterName());
        }
        if (t instanceof MissingServletRequestPartException mp) {
            return ApiError.of(HttpStatus.BAD_REQUEST, StandardErrorCodes.MISSING_REQUIRED_FIELD.getCode(),
                    "Please attach the " + UserMessages.label(mp.getRequestPartName()).toLowerCase(Locale.ROOT)
                            + " file.", mp.getRequestPartName());
        }
        if (t instanceof MissingRequestHeaderException) {
            return ApiError.of(HttpStatus.BAD_REQUEST, StandardErrorCodes.INVALID_REQUEST.getCode(),
                    "The request is missing required information. Please refresh the page and try again.");
        }
        if (t instanceof MissingPathVariableException) {
            return ApiError.forStatus(HttpStatus.BAD_REQUEST);
        }
        if (t instanceof MaxUploadSizeExceededException mu) return fileTooLarge(mu.getMaxUploadSize());
        if (t instanceof MultipartException) {
            return ApiError.of(HttpStatus.BAD_REQUEST, StandardErrorCodes.FILE_UPLOAD_FAILED.getCode(),
                    "The file could not be uploaded. Please check the file and try again.");
        }

        // ---- routing / HTTP protocol ----
        if (t instanceof NoResourceFoundException || t instanceof NoHandlerFoundException) {
            return ApiError.of(HttpStatus.NOT_FOUND, StandardErrorCodes.ENDPOINT_NOT_FOUND.getCode(),
                    "The requested service was not found.");
        }
        if (t instanceof HttpRequestMethodNotSupportedException) {
            return ApiError.of(HttpStatus.METHOD_NOT_ALLOWED, StandardErrorCodes.METHOD_NOT_ALLOWED.getCode(),
                    UserMessages.forStatus(405));
        }
        if (t instanceof HttpMediaTypeNotSupportedException) {
            return ApiError.of(HttpStatus.UNSUPPORTED_MEDIA_TYPE, StandardErrorCodes.INVALID_REQUEST.getCode(),
                    UserMessages.forStatus(415));
        }
        if (t instanceof HttpMediaTypeNotAcceptableException) {
            return ApiError.of(HttpStatus.NOT_ACCEPTABLE, StandardErrorCodes.INVALID_REQUEST.getCode(),
                    UserMessages.forStatus(406));
        }
        if (t instanceof AsyncRequestTimeoutException) {
            return ApiError.of(HttpStatus.SERVICE_UNAVAILABLE, StandardErrorCodes.CONNECTION_TIMEOUT.getCode(),
                    UserMessages.forStatus(408));
        }
        if (t instanceof ResponseStatusException rse) {
            HttpStatus status = toStatus(rse.getStatusCode());
            return ApiError.of(status, UserMessages.codeForStatus(status.value()),
                    status.is5xxServerError() ? UserMessages.forStatus(status)
                            : UserMessages.safeOr(rse.getReason(), UserMessages.forStatus(status)));
        }

        // ---- security ----
        if (t instanceof BadCredentialsException) {
            return ApiError.of(StandardErrorCodes.INVALID_CREDENTIALS, "Incorrect email or password.");
        }
        if (t instanceof LockedException) {
            return ApiError.of(StandardErrorCodes.ACCOUNT_LOCKED,
                    "Your account is locked. Please contact your administrator.");
        }
        if (t instanceof DisabledException) {
            return ApiError.of(StandardErrorCodes.ACCOUNT_DISABLED,
                    "Your account is disabled. Please contact your administrator.");
        }
        if (t instanceof AccountExpiredException || t instanceof CredentialsExpiredException) {
            return ApiError.of(HttpStatus.UNAUTHORIZED, StandardErrorCodes.TOKEN_EXPIRED.getCode(),
                    "Your account or password has expired. Please contact your administrator.");
        }
        if (t instanceof InsufficientAuthenticationException || t instanceof AuthenticationCredentialsNotFoundException) {
            return ApiError.forStatus(HttpStatus.UNAUTHORIZED);
        }
        if (t instanceof AuthenticationException) {
            return ApiError.of(HttpStatus.UNAUTHORIZED, StandardErrorCodes.UNAUTHORIZED_ACCESS.getCode(),
                    "Sign-in failed. Please check your details and try again.");
        }
        if (t instanceof AccessDeniedException) return ApiError.forStatus(HttpStatus.FORBIDDEN);
        // java.lang.SecurityException carries internal reasons ("Unauthorized super admin mode",
        // "Access to protected tenant ...") — never shown, always a plain 403.
        if (t instanceof SecurityException) return ApiError.forStatus(HttpStatus.FORBIDDEN);

        // ---- persistence ----
        if (t instanceof OptimisticLockingFailureException || isJpa(t, "OptimisticLockException")) {
            return ApiError.of(HttpStatus.CONFLICT, "CONFLICT",
                    "This record was changed by someone else. Please reload it and try again.");
        }
        if (t instanceof EmptyResultDataAccessException || isJpa(t, "EntityNotFoundException")
                || isJpa(t, "NoResultException")) {
            return ApiError.of(HttpStatus.NOT_FOUND, StandardErrorCodes.RESOURCE_NOT_FOUND.getCode(),
                    UserMessages.safeOr(t.getMessage(), "The requested record was not found."));
        }
        if (t instanceof DataIntegrityViolationException || t instanceof DataAccessException
                || t instanceof CannotCreateTransactionException || t instanceof java.sql.SQLException
                || t.getClass().getName().startsWith("org.hibernate.exception.")) {
            return database(t);
        }

        // ---- plugins ----
        if (t instanceof PluginException pe) {
            return ApiError.of(HttpStatus.BAD_REQUEST, "PLUGIN_ERROR", UserMessages.safeOr(pe.getMessage(),
                    "The module operation could not be completed. Please check the module and try again."));
        }

        // Any other Spring MVC exception that knows its status (e.g. ServletRequestBindingException).
        if (t instanceof ErrorResponse er) return ApiError.forStatus(toStatus(er.getStatusCode()));
        return null;
    }

    // =====================================================================================
    // Convention-based mappings — used only when nothing precise is in the chain
    // =====================================================================================

    private ApiError byConvention(Throwable t) {
        ResponseStatus annotated = AnnotatedElementUtils.findMergedAnnotation(t.getClass(), ResponseStatus.class);
        if (annotated != null) {
            HttpStatus status = annotated.code();
            String reason = annotated.reason().isEmpty() ? t.getMessage() : annotated.reason();
            return ApiError.of(status, UserMessages.codeForStatus(status.value()),
                    status.is5xxServerError() ? UserMessages.forStatus(status)
                            : UserMessages.safeOr(reason, UserMessages.forStatus(status)));
        }

        // Application exceptions from modules this library doesn't know, by naming convention.
        String name = t.getClass().getSimpleName();
        if (name.endsWith("NotFoundException")) {
            return ApiError.of(HttpStatus.NOT_FOUND, StandardErrorCodes.RESOURCE_NOT_FOUND.getCode(),
                    UserMessages.safeOr(t.getMessage(), "The requested record was not found."));
        }
        if (name.contains("AlreadyExist") || name.contains("Duplicate")) {
            return ApiError.of(HttpStatus.CONFLICT, StandardErrorCodes.RESOURCE_ALREADY_EXISTS.getCode(),
                    UserMessages.safeOr(t.getMessage(), "This record already exists."));
        }
        if (name.contains("RateLimit") || name.contains("TooManyRequests")) {
            return ApiError.forStatus(HttpStatus.TOO_MANY_REQUESTS);
        }
        if (name.equals("PluginExecutionException")) {
            return ApiError.of(HttpStatus.SERVICE_UNAVAILABLE, StandardErrorCodes.SERVICE_UNAVAILABLE.getCode(),
                    "This module is temporarily unavailable. Please try again shortly.");
        }
        if (name.equals("PluginException") || name.equals("PluginDependencyException")
                || name.equals("PluginInstallationException")) {
            return ApiError.of(HttpStatus.BAD_REQUEST, "PLUGIN_ERROR", UserMessages.safeOr(t.getMessage(),
                    "The module operation could not be completed. Please check the module and try again."));
        }

        if (t instanceof IllegalArgumentException) return illegalArgument(t.getMessage());
        if (t instanceof IllegalStateException) {
            // Used both for business conflicts ("Cannot delete system role") and for internal
            // invariants ("Tenant context not set"): readable ones are a 409, the rest a 500.
            String message = t.getMessage();
            if (!UserMessages.isSafe(message)) return null;
            if (message.toLowerCase(Locale.ROOT).contains("not found")) {
                return ApiError.of(HttpStatus.NOT_FOUND, StandardErrorCodes.RESOURCE_NOT_FOUND.getCode(),
                        UserMessages.safeOr(message, UserMessages.forStatus(404)));
            }
            return ApiError.of(HttpStatus.CONFLICT, "CONFLICT", UserMessages.safeOr(message, UserMessages.forStatus(409)));
        }
        if (t instanceof UnsupportedOperationException) {
            return ApiError.of(HttpStatus.BAD_REQUEST, StandardErrorCodes.OPERATION_NOT_ALLOWED.getCode(),
                    "This action is not supported.");
        }
        return null;
    }

    // =====================================================================================
    // Builders
    // =====================================================================================

    private ApiError business(BusinessException be) {
        HttpStatus status = be.getHttpStatus() != null ? be.getHttpStatus() : HttpStatus.BAD_REQUEST;
        String fallback = UserMessages.forStatus(status);
        List<ErrorModel> errors = be.getErrors() == null ? List.of() : be.getErrors().stream()
                .map(e -> new ErrorModel(e.getCode(),
                        // Server errors never echo their message; client errors only when readable.
                        status.is5xxServerError() ? fallback : UserMessages.safeOr(e.getMessage(), fallback),
                        e.getField()))
                .toList();
        if (errors.isEmpty()) {
            errors = List.of(new ErrorModel(UserMessages.codeForStatus(status.value()), fallback, null));
        }
        return new ApiError(status, errors);
    }

    private ApiError validation(List<FieldError> fieldErrors, List<ObjectError> globalErrors) {
        List<ErrorModel> errors = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        for (FieldError fe : fieldErrors) {
            errors.add(new ErrorModel(StandardErrorCodes.VALIDATION_FAILED.getCode(),
                    UserMessages.fieldMessage(fe.getField(), fe.getDefaultMessage()), fe.getField()));
            labels.add(UserMessages.label(fe.getField()));
        }
        for (ObjectError ge : globalErrors) {
            errors.add(new ErrorModel(StandardErrorCodes.VALIDATION_FAILED.getCode(),
                    UserMessages.safeOr(ge.getDefaultMessage(), UserMessages.forStatus(400)), null));
        }
        return validationResult(errors, labels);
    }

    private ApiError methodValidation(HandlerMethodValidationException ex) {
        List<ErrorModel> errors = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        ex.getParameterValidationResults().forEach(result -> {
            String param = result.getMethodParameter().getParameterName();
            for (MessageSourceResolvable error : result.getResolvableErrors()) {
                String field = error instanceof FieldError fe ? fe.getField() : param;
                errors.add(new ErrorModel(StandardErrorCodes.VALIDATION_FAILED.getCode(),
                        UserMessages.fieldMessage(field, error.getDefaultMessage()), field));
                labels.add(UserMessages.label(field));
            }
        });
        return validationResult(errors, labels);
    }

    private ApiError constraintViolations(Set<ConstraintViolation<?>> violations) {
        List<ErrorModel> errors = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        if (violations != null) {
            for (ConstraintViolation<?> v : violations) {
                String field = leafProperty(v.getPropertyPath());
                errors.add(new ErrorModel(StandardErrorCodes.VALIDATION_FAILED.getCode(),
                        UserMessages.fieldMessage(field, v.getMessage()), field));
                labels.add(UserMessages.label(field));
            }
        }
        return validationResult(errors, labels);
    }

    /**
     * Several invalid fields: a summary goes first (clients show the first message as the
     * headline), followed by one entry per field for inline display.
     */
    private ApiError validationResult(List<ErrorModel> errors, List<String> labels) {
        if (errors.isEmpty()) return ApiError.forStatus(HttpStatus.BAD_REQUEST);
        if (errors.size() == 1) return new ApiError(HttpStatus.BAD_REQUEST, errors);
        List<ErrorModel> withSummary = new ArrayList<>(errors.size() + 1);
        withSummary.add(new ErrorModel(StandardErrorCodes.VALIDATION_FAILED.getCode(),
                labels.isEmpty() ? UserMessages.forStatus(400) : UserMessages.summary(labels), null));
        withSummary.addAll(errors);
        return new ApiError(HttpStatus.BAD_REQUEST, withSummary);
    }

    private ApiError unreadableBody(HttpMessageNotReadableException ex) {
        Throwable cause = ex.getCause();
        String message = ex.getMessage() == null ? "" : ex.getMessage();

        if (cause == null && message.contains("Required request body is missing")) {
            return ApiError.of(HttpStatus.BAD_REQUEST, StandardErrorCodes.INVALID_JSON.getCode(),
                    "The request is missing its data. Please fill in the form and try again.");
        }

        // Jackson 3 (Spring Boot 4 default).
        if (cause instanceof tools.jackson.databind.exc.InvalidFormatException ife) {
            String field = jackson3Field(ife);
            return ApiError.of(HttpStatus.BAD_REQUEST, StandardErrorCodes.INVALID_INPUT_FORMAT.getCode(),
                    invalidValue(field, ife.getValue(), ife.getTargetType()), field);
        }
        if (cause instanceof tools.jackson.databind.exc.MismatchedInputException mie) {
            String field = jackson3Field(mie);
            return ApiError.of(HttpStatus.BAD_REQUEST, StandardErrorCodes.INVALID_INPUT_FORMAT.getCode(),
                    field == null ? "The request data is not in the expected format."
                            : UserMessages.label(field) + " has the wrong type of value.", field);
        }
        if (cause instanceof tools.jackson.core.exc.StreamReadException) {
            return ApiError.of(HttpStatus.BAD_REQUEST, StandardErrorCodes.INVALID_JSON.getCode(),
                    "The request data is not valid. Please check the form and try again.");
        }

        // Jackson 2 (com.fasterxml) — used if a consuming app registers a Jackson 2 converter.
        // Read reflectively so this library has no compile-time dependency on Jackson 2.
        if (cause != null && cause.getClass().getName().startsWith("com.fasterxml.jackson")) {
            String field = jackson2Field(cause);
            if (cause.getClass().getSimpleName().equals("InvalidFormatException")) {
                Object value = invoke(cause, "getValue");
                Object type = invoke(cause, "getTargetType");
                return ApiError.of(HttpStatus.BAD_REQUEST, StandardErrorCodes.INVALID_INPUT_FORMAT.getCode(),
                        invalidValue(field, value, type instanceof Class<?> c ? c : null), field);
            }
            if (field != null) {
                return ApiError.of(HttpStatus.BAD_REQUEST, StandardErrorCodes.INVALID_INPUT_FORMAT.getCode(),
                        UserMessages.label(field) + " has the wrong type of value.", field);
            }
        }
        return ApiError.of(HttpStatus.BAD_REQUEST, StandardErrorCodes.INVALID_JSON.getCode(),
                "The request data is not in the expected format. Please check the form and try again.");
    }

    private ApiError illegalArgument(String message) {
        if (message != null) {
            Matcher enumMatch = ENUM_CONSTANT.matcher(message);
            if (enumMatch.find()) {
                return ApiError.of(HttpStatus.BAD_REQUEST, StandardErrorCodes.INVALID_INPUT_FORMAT.getCode(),
                        "'" + enumMatch.group(1) + "' is not an accepted value.");
            }
            Matcher uuidMatch = INVALID_UUID.matcher(message);
            if (uuidMatch.find()) {
                return ApiError.of(HttpStatus.BAD_REQUEST, StandardErrorCodes.INVALID_INPUT_FORMAT.getCode(),
                        "'" + uuidMatch.group(1).strip() + "' is not a valid ID.");
            }
        }
        return ApiError.of(HttpStatus.BAD_REQUEST, StandardErrorCodes.INVALID_REQUEST.getCode(),
                UserMessages.safeOr(message, UserMessages.forStatus(400)));
    }

    private ApiError database(Throwable t) {
        ApiError translated = DatabaseErrorTranslator.translate(t);
        if (translated != null) return translated;

        if (t instanceof PessimisticLockingFailureException || t instanceof QueryTimeoutException
                || t.getClass().getSimpleName().equals("LockAcquisitionException")) {
            return ApiError.of(HttpStatus.SERVICE_UNAVAILABLE, StandardErrorCodes.SERVICE_UNAVAILABLE.getCode(),
                    "The system is busy right now. Please try again in a moment.");
        }
        if (t instanceof DataAccessResourceFailureException || t instanceof CannotCreateTransactionException
                || t.getClass().getSimpleName().equals("JDBCConnectionException")) {
            return ApiError.forStatus(HttpStatus.SERVICE_UNAVAILABLE);
        }
        if (t instanceof InvalidDataAccessResourceUsageException
                || t.getClass().getSimpleName().equals("SQLGrammarException")) {
            return ApiError.of(HttpStatus.SERVICE_UNAVAILABLE, StandardErrorCodes.SERVICE_UNAVAILABLE.getCode(),
                    "This feature is not available right now. Please try again later or contact support.");
        }
        if (t instanceof DataIntegrityViolationException) {
            return ApiError.of(HttpStatus.CONFLICT, StandardErrorCodes.BUSINESS_RULE_VIOLATION.getCode(),
                    "This change conflicts with existing records. Please review your input.");
        }
        return ApiError.of(HttpStatus.INTERNAL_SERVER_ERROR, StandardErrorCodes.DATABASE_ERROR.getCode(),
                "We couldn't save or load your data. Please try again. If it continues, contact support.");
    }

    private ApiError fileTooLarge(long maxBytes) {
        String limit = maxBytes > 0 ? " The maximum allowed size is " + humanBytes(maxBytes) + "." : "";
        return ApiError.of(HttpStatus.PAYLOAD_TOO_LARGE, StandardErrorCodes.FILE_TOO_LARGE.getCode(),
                "The file is too large." + limit);
    }

    // =====================================================================================
    // Helpers
    // =====================================================================================

    /** Friendly "invalid value" message based on the target type. */
    private String invalidValue(String field, Object value, Class<?> type) {
        String label = UserMessages.label(field);
        if (type != null) {
            if (type.isEnum()) {
                List<String> accepted = Arrays.stream(type.getEnumConstants()).map(Object::toString).toList();
                String shown = accepted.stream().limit(MAX_ENUM_VALUES_SHOWN).collect(Collectors.joining(", "));
                return label + " must be one of: " + shown + (accepted.size() > MAX_ENUM_VALUES_SHOWN ? ", …" : "") + ".";
            }
            if (type == UUID.class) return label + " must be a valid ID.";
            if (java.time.LocalDate.class.isAssignableFrom(type)) return label + " must be a valid date (YYYY-MM-DD).";
            if (Temporal.class.isAssignableFrom(type) || java.util.Date.class.isAssignableFrom(type)) {
                return label + " must be a valid date and time.";
            }
            if (Number.class.isAssignableFrom(type) || (type.isPrimitive() && type != boolean.class)) {
                return label + " must be a number.";
            }
            if (type == Boolean.class || type == boolean.class) return label + " must be true or false.";
        }
        String shownValue = value == null ? "" : String.valueOf(value);
        if (shownValue.isEmpty() || shownValue.length() > 40 || !UserMessages.isSafe(shownValue)) {
            return label + " has an invalid value.";
        }
        return "'" + shownValue + "' is not a valid value for " + label.toLowerCase(Locale.ROOT) + ".";
    }

    private static String jackson3Field(tools.jackson.core.JacksonException ex) {
        List<tools.jackson.core.JacksonException.Reference> path = ex.getPath();
        for (int i = path.size() - 1; i >= 0; i--) {
            String name = path.get(i).getPropertyName();
            if (name != null) return name;
        }
        return null;
    }

    private static String jackson2Field(Throwable ex) {
        Object path = invoke(ex, "getPath");
        if (!(path instanceof List<?> refs)) return null;
        for (int i = refs.size() - 1; i >= 0; i--) {
            Object name = invoke(refs.get(i), "getFieldName");
            if (name != null) return name.toString();
        }
        return null;
    }

    private static Object invoke(Object target, String method) {
        try {
            Method m = target.getClass().getMethod(method);
            return m.invoke(target);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    private static String leafProperty(Path path) {
        String leaf = null;
        if (path != null) {
            for (Path.Node node : path) {
                if (node.getName() != null) leaf = node.getName();
            }
        }
        return leaf;
    }

    private static boolean isJpa(Throwable t, String simpleName) {
        return t.getClass().getName().equals("jakarta.persistence." + simpleName);
    }

    private static HttpStatus toStatus(HttpStatusCode code) {
        HttpStatus status = HttpStatus.resolve(code.value());
        return status != null ? status : (code.is5xxServerError() ? HttpStatus.INTERNAL_SERVER_ERROR : HttpStatus.BAD_REQUEST);
    }

    private static String humanBytes(long bytes) {
        if (bytes >= 1024L * 1024 * 1024) return (bytes / (1024L * 1024 * 1024)) + " GB";
        if (bytes >= 1024L * 1024) return (bytes / (1024L * 1024)) + " MB";
        if (bytes >= 1024) return (bytes / 1024) + " KB";
        return bytes + " bytes";
    }

    /** Outermost first; stops on cycles. Async wrappers (CompletionException/ExecutionException) expose the real error as their cause. */
    static List<Throwable> causeChain(Throwable ex) {
        List<Throwable> chain = new ArrayList<>();
        Set<Throwable> seen = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        Throwable t = ex;
        while (t != null && seen.add(t)) {
            chain.add(t);
            Throwable next = t.getCause();
            if (t instanceof InvocationTargetException ite) next = ite.getTargetException();
            if (t instanceof UndeclaredThrowableException ute) next = ute.getUndeclaredThrowable();
            t = next;
        }
        return chain;
    }
}
