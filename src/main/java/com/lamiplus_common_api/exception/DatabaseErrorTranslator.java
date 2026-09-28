package com.lamiplus_common_api.exception;

import org.springframework.http.HttpStatus;

import java.lang.reflect.Method;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Translates database failures into user-facing errors using the standard SQLState code, not
 * message text (which varies by driver version and locale). Table, column and constraint names
 * are never shown as-is: affected columns become field labels, and tenant/facility scoping
 * columns are dropped because they are not something the user typed.
 */
final class DatabaseErrorTranslator {

    /** Columns that scope data rather than describe it — never mentioned to users. */
    private static final Set<String> SCOPE_COLUMNS = Set.of("tenant_id", "facility_id", "archived", "deleted");

    /** Postgres detail: {@code Key (tenant_id, email)=(x, a@b.c) already exists.} */
    private static final Pattern KEY_COLUMNS = Pattern.compile("Key \\(([^)]+)\\)=");
    private static final Pattern NULL_COLUMN = Pattern.compile("column \"([^\"]+)\"");

    private DatabaseErrorTranslator() {
    }

    /** Finds the SQLException in the cause chain, or null. */
    static SQLException findSqlException(Throwable ex) {
        for (Throwable t = ex; t != null; t = t.getCause() == t ? null : t.getCause()) {
            if (t instanceof SQLException sql) {
                // Batch failures chain the real error via getNextException().
                SQLException next = sql.getNextException();
                return next != null && next.getSQLState() != null ? next : sql;
            }
        }
        return null;
    }

    /** Translation for a database failure, or null if there is no SQLException to go on. */
    static ApiError translate(Throwable ex) {
        SQLException sql = findSqlException(ex);
        if (sql == null || sql.getSQLState() == null) return null;

        String state = sql.getSQLState();
        String detail = serverDetail(sql);
        String message = sql.getMessage() == null ? "" : sql.getMessage();

        return switch (state) {
            case "23505" -> duplicate(columns(detail, message));
            case "23503" -> foreignKey(detail + " " + message, columns(detail, message));
            case "23502" -> notNull(serverColumn(sql, message));
            case "23514" -> ApiError.of(HttpStatus.BAD_REQUEST, StandardErrorCodes.BUSINESS_RULE_VIOLATION.getCode(),
                    "One of the values provided is not allowed. Please check your input.");
            case "22001" -> ApiError.of(HttpStatus.BAD_REQUEST, StandardErrorCodes.INPUT_TOO_LONG.getCode(),
                    "One of the values is too long. Please shorten it and try again.");
            case "22003" -> ApiError.of(HttpStatus.BAD_REQUEST, StandardErrorCodes.INVALID_INPUT_FORMAT.getCode(),
                    "A number is outside the allowed range.");
            case "22007", "22008" -> ApiError.of(HttpStatus.BAD_REQUEST, StandardErrorCodes.INVALID_DATE_FORMAT.getCode(),
                    "A date or time value is invalid.");
            case "22P02" -> ApiError.of(HttpStatus.BAD_REQUEST, StandardErrorCodes.INVALID_INPUT_FORMAT.getCode(),
                    "One of the values has an invalid format.");
            // Row-level security WITH CHECK failure / missing grants.
            case "42501" -> ApiError.of(HttpStatus.FORBIDDEN, StandardErrorCodes.INSUFFICIENT_PRIVILEGES.getCode(),
                    "You don't have permission to change this record.");
            // Serialization failure, deadlock, lock not available, statement timeout.
            case "40001", "40P01", "55P03", "57014" -> ApiError.of(HttpStatus.SERVICE_UNAVAILABLE,
                    StandardErrorCodes.SERVICE_UNAVAILABLE.getCode(),
                    "The system is busy right now. Please try again in a moment.");
            // Raised by a database trigger/function — a business rule enforced in SQL.
            case "P0001" -> ApiError.of(HttpStatus.BAD_REQUEST, StandardErrorCodes.BUSINESS_RULE_VIOLATION.getCode(),
                    "This action isn't allowed for this record.");
            default -> byClass(state);
        };
    }

    private static ApiError byClass(String state) {
        String cls = state.length() >= 2 ? state.substring(0, 2) : state;
        return switch (cls) {
            // Connection problems / insufficient resources / operator intervention.
            case "08", "53", "57" -> ApiError.of(HttpStatus.SERVICE_UNAVAILABLE,
                    StandardErrorCodes.SERVICE_UNAVAILABLE.getCode(),
                    "The service is temporarily unavailable. Please try again shortly.");
            // Missing table/column or bad SQL — a deployment/migration problem, not the user's.
            case "42" -> ApiError.of(HttpStatus.SERVICE_UNAVAILABLE,
                    StandardErrorCodes.SERVICE_UNAVAILABLE.getCode(),
                    "This feature is not available right now. Please try again later or contact support.");
            case "22" -> ApiError.of(HttpStatus.BAD_REQUEST, StandardErrorCodes.INVALID_INPUT_FORMAT.getCode(),
                    "One of the values provided is invalid. Please check your input.");
            case "23" -> ApiError.of(HttpStatus.CONFLICT, StandardErrorCodes.BUSINESS_RULE_VIOLATION.getCode(),
                    "This change conflicts with existing records. Please review your input.");
            default -> ApiError.of(HttpStatus.INTERNAL_SERVER_ERROR, StandardErrorCodes.DATABASE_ERROR.getCode(),
                    "We couldn't save or load your data. Please try again. If it continues, contact support.");
        };
    }

    private static ApiError duplicate(List<String> columns) {
        if (columns.isEmpty()) {
            return ApiError.of(HttpStatus.CONFLICT, "DUPLICATE_RESOURCE",
                    "This record already exists.");
        }
        if (columns.equals(List.of("email"))) {
            return ApiError.of(HttpStatus.CONFLICT, "DUPLICATE_RESOURCE",
                    "This email address is already in use.", "email");
        }
        String labels = columns.stream().map(c -> UserMessages.label(c).toLowerCase(Locale.ROOT))
                .collect(Collectors.joining(" and "));
        return ApiError.of(HttpStatus.CONFLICT, "DUPLICATE_RESOURCE",
                "A record with the same " + labels + " already exists.",
                UserMessages.toProperty(columns.get(0)));
    }

    private static ApiError foreignKey(String text, List<String> columns) {
        // Deleting/updating a row that other rows still point to.
        if (text.contains("still referenced")) {
            return ApiError.of(HttpStatus.CONFLICT, StandardErrorCodes.RESOURCE_IN_USE.getCode(),
                    "This record is used by other records, so it can't be deleted or changed.");
        }
        String field = columns.isEmpty() ? null : UserMessages.toProperty(columns.get(0));
        String what = columns.isEmpty() ? "item" : UserMessages.label(columns.get(0)).toLowerCase(Locale.ROOT);
        return ApiError.of(HttpStatus.BAD_REQUEST, "REFERENCE_ERROR",
                "The selected " + what + " doesn't exist or is no longer available.", field);
    }

    private static ApiError notNull(String column) {
        if (column == null || SCOPE_COLUMNS.contains(column)) {
            return ApiError.of(HttpStatus.BAD_REQUEST, StandardErrorCodes.MISSING_REQUIRED_FIELD.getCode(),
                    "A required value is missing. Please complete all required fields.");
        }
        return ApiError.of(HttpStatus.BAD_REQUEST, StandardErrorCodes.MISSING_REQUIRED_FIELD.getCode(),
                UserMessages.label(column) + " is required.", UserMessages.toProperty(column));
    }

    /** Columns named in a {@code Key (...)=} detail, minus tenant/facility scoping columns. */
    private static List<String> columns(String detail, String message) {
        Matcher m = KEY_COLUMNS.matcher(detail.isEmpty() ? message : detail);
        if (!m.find()) return List.of();
        return Arrays.stream(m.group(1).split(","))
                .map(String::trim)
                .map(c -> c.replace("\"", "").toLowerCase(Locale.ROOT))
                .map(c -> c.startsWith("lower(") ? c.substring(6).replace("::text", "") : c)
                .filter(c -> !c.isEmpty() && !SCOPE_COLUMNS.contains(c))
                .toList();
    }

    private static String serverColumn(SQLException sql, String message) {
        String column = invokeServerErrorMessage(sql, "getColumn");
        if (column != null) return column;
        Matcher m = NULL_COLUMN.matcher(message);
        return m.find() ? m.group(1) : null;
    }

    private static String serverDetail(SQLException sql) {
        String detail = invokeServerErrorMessage(sql, "getDetail");
        return detail == null ? "" : detail;
    }

    /**
     * Reads a field of PSQLException.getServerErrorMessage() reflectively, so this library has
     * no compile-time dependency on the Postgres driver. Returns null for other drivers.
     */
    private static String invokeServerErrorMessage(SQLException sql, String getter) {
        try {
            Method serverMessage = sql.getClass().getMethod("getServerErrorMessage");
            Object info = serverMessage.invoke(sql);
            if (info == null) return null;
            Object value = info.getClass().getMethod(getter).invoke(info);
            return value == null ? null : value.toString();
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }
}
