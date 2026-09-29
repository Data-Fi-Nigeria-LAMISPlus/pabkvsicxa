package com.lamiplus_common_api.exception;

import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Wording rules for everything the API shows to an end user.
 *
 * Exception messages are written by developers and libraries (SQL, class names, internal state),
 * so a message is only passed through when it reads like plain language; otherwise the caller
 * falls back to a generic, status-appropriate message. The original is always logged server-side.
 */
public final class UserMessages {

    private static final int MAX_LENGTH = 250;

    /** Anything that looks like code, SQL, internals or configuration. */
    private static final List<Pattern> TECHNICAL = List.of(
            Pattern.compile("(?i)\\b(exception|stack ?trace|nullpointer|classloader|class ?cast|reflection|instrumentation)\\b"),
            Pattern.compile("(?i)\\b(sql|jdbc|jpa|hibernate|liquibase|entitymanager|emf|datasource|postgres|psql|redis|jackson)\\b"),
            // Postgres/Hibernate wording (quoted identifiers) — plain words like "relation" or
            // "transaction" also appear in business messages, so only the technical forms match.
            Pattern.compile("(?i)\\b(relation|column|table|constraint|index|type) \"|\\bconstraint\\b|\\bforeign key\\b|\\bprimary key\\b|\\bdeadlock\\b|\\bschema\\b|\\bpartition\\b"),
            Pattern.compile("(?i)\\bselect\\b.+\\bfrom\\b|\\binsert into\\b|\\bdelete from\\b|\\bupdate\\s+\\w+\\s+set\\b"),
            Pattern.compile("(?i)\\b(context|bean|proxy|null)\\b"),
            Pattern.compile("\\b(java|javax|jakarta|org|com|tools)\\.[a-z]"),     // package names
            Pattern.compile("\\b[A-Z]{2,}(_[A-Z0-9]+)+\\b"),                        // CONSTANT_NAMES, ROLE_ADMIN
            Pattern.compile("\\w+\\(\\)|::|->|\\$\\{|=\\(|\\bat \\w+\\.")          // calls, SQL detail, frames
    );

    private static final Pattern CAMEL_BOUNDARY = Pattern.compile("(?<=[a-z0-9])(?=[A-Z])");

    private UserMessages() {
    }

    /** True when the message is short plain language that is safe to show to a user. */
    public static boolean isSafe(String message) {
        if (message == null) return false;
        String m = message.strip();
        if (m.isEmpty() || m.length() > MAX_LENGTH || m.indexOf('\n') >= 0 || m.indexOf('\t') >= 0) {
            return false;
        }
        for (Pattern p : TECHNICAL) {
            if (p.matcher(m).find()) return false;
        }
        return true;
    }

    /** The message itself when safe to show, otherwise {@code fallback}. */
    public static String safeOr(String message, String fallback) {
        return isSafe(message) ? finish(message.strip()) : fallback;
    }

    /** Generic message for an HTTP status, written for end users. */
    public static String forStatus(int status) {
        return switch (status) {
            case 400 -> "Some of the information provided is invalid. Please check and try again.";
            case 401 -> "Your session has expired or you are not signed in. Please sign in again.";
            case 403 -> "You don't have permission to perform this action.";
            case 404 -> "The requested record or page was not found.";
            case 405 -> "This action is not supported.";
            case 406 -> "The requested response format is not supported.";
            case 408 -> "The request took too long. Please try again.";
            case 409 -> "This action conflicts with the current state of the data. Please refresh and try again.";
            case 413 -> "The file or request is too large.";
            case 415 -> "This type of content is not supported.";
            case 422 -> "Some of the information provided is invalid. Please check and try again.";
            case 429 -> "Too many requests. Please wait a moment and try again.";
            case 502, 504 -> "A connected service did not respond. Please try again shortly.";
            case 503 -> "The service is temporarily unavailable. Please try again shortly.";
            default -> status >= 500
                    ? "Something went wrong on our side. Please try again. If it continues, contact support."
                    : "The request could not be completed. Please check and try again.";
        };
    }

    /** Error code used when there is nothing more specific than the status. */
    public static String codeForStatus(int status) {
        return switch (status) {
            case 400, 422 -> StandardErrorCodes.INVALID_REQUEST.getCode();
            case 401 -> StandardErrorCodes.UNAUTHORIZED_ACCESS.getCode();
            case 403 -> StandardErrorCodes.INSUFFICIENT_PRIVILEGES.getCode();
            case 404 -> StandardErrorCodes.RESOURCE_NOT_FOUND.getCode();
            case 405 -> StandardErrorCodes.METHOD_NOT_ALLOWED.getCode();
            case 408 -> StandardErrorCodes.CONNECTION_TIMEOUT.getCode();
            case 409 -> "CONFLICT";
            case 413 -> StandardErrorCodes.FILE_TOO_LARGE.getCode();
            case 429 -> StandardErrorCodes.TOO_MANY_REQUESTS.getCode();
            case 502, 504 -> StandardErrorCodes.EXTERNAL_SERVICE_ERROR.getCode();
            case 503 -> StandardErrorCodes.SERVICE_UNAVAILABLE.getCode();
            default -> status >= 500 ? StandardErrorCodes.INTERNAL_ERROR.getCode()
                    : StandardErrorCodes.INVALID_REQUEST.getCode();
        };
    }

    public static String forStatus(HttpStatus status) {
        return forStatus(status.value());
    }

    /**
     * Turns a property/column name into a label: {@code dateOfBirth} → "Date of birth",
     * {@code facility_id} → "Facility", {@code items[0].unitPrice} → "Unit price".
     */
    public static String label(String field) {
        if (field == null || field.isBlank()) return "This field";
        String name = field.strip();
        int dot = name.lastIndexOf('.');
        if (dot >= 0 && dot < name.length() - 1) name = name.substring(dot + 1);
        name = name.replaceAll("\\[\\d*]", "");
        name = CAMEL_BOUNDARY.matcher(name).replaceAll(" ").replace('_', ' ').replace('-', ' ')
                .trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
        name = name.replaceAll("\\s+(uu)?id$", "");
        if (name.isEmpty() || name.equals("id") || name.equals("uuid")) return "ID";
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    /** snake_case column → camelCase property, so DB-derived field errors match form fields. */
    public static String toProperty(String column) {
        if (column == null || column.indexOf('_') < 0) return column;
        StringBuilder sb = new StringBuilder();
        boolean upper = false;
        for (char c : column.toLowerCase(Locale.ROOT).toCharArray()) {
            if (c == '_') {
                upper = sb.length() > 0;
            } else {
                sb.append(upper ? Character.toUpperCase(c) : c);
                upper = false;
            }
        }
        return sb.toString();
    }

    /**
     * Message for one invalid field. Keeps custom messages written for users and rewrites the
     * terse Bean Validation defaults ("must not be null") into a full sentence with the label.
     */
    public static String fieldMessage(String field, String message) {
        String label = label(field);
        if (message == null || message.isBlank()) return label + " is invalid.";
        String m = message.strip();

        // Bean Validation's own default templates (no subject) → full sentence with the label.
        String lower = m.toLowerCase(Locale.ROOT);
        if (lower.startsWith("must not be null") || lower.startsWith("must not be blank")
                || lower.startsWith("must not be empty") || lower.startsWith("may not be null")
                || lower.startsWith("may not be empty") || lower.equals("is required")) {
            return label + " is required.";
        }
        if (lower.startsWith("must match")) return label + " has an invalid format.";
        if (lower.contains("well-formed email")) return label + " must be a valid email address.";
        if (lower.startsWith("size must be between")) {
            return finish(label + " must be " + m.substring("size must be ".length()) + " characters");
        }

        // Custom messages: "patientUuid is required" → "Patient is required."
        m = humanizeLeadingField(m);
        if (Character.isUpperCase(m.charAt(0))) {
            return safeOr(m, label + " is invalid.");
        }
        return isSafe(m) ? finish(label + " " + m) : label + " is invalid.";
    }

    /** "{subject} is required", "{subject} must be at least 1", "{subject} cannot be in the future", … */
    private static final Pattern SUBJECT_PREDICATE = Pattern.compile(
            "^(.{1,60}?)\\s+(is (?:required|mandatory|missing|invalid)|must\\b.*|cannot\\b.*|can't\\b.*|should\\b.*)$");

    /**
     * Validation messages are often written with the property name as the subject
     * ("patientUuid is required", "vital_sign_date is required", "service Location is required").
     * When the subject looks like an identifier, it is replaced by its label
     * ("Patient is required.", "Vital sign date is required."). Subjects that are already a
     * phrase ("Date enrolled on ART cannot be in the future") are left untouched.
     */
    static String humanizeLeadingField(String message) {
        Matcher m = SUBJECT_PREDICATE.matcher(message);
        if (!m.matches()) return message;
        String subject = m.group(1);
        String predicate = m.group(2).replaceFirst("^is mandatory", "is required");
        if (!looksLikeIdentifier(subject)) {
            return predicate.equals(m.group(2)) ? message : subject + " " + predicate;
        }
        return label(subject) + " " + predicate;
    }

    /** camelCase, snake_case, a lowercase start, or an "id"/"uuid" suffix. */
    private static boolean looksLikeIdentifier(String subject) {
        if (subject.indexOf('_') >= 0) return true;
        if (Character.isLowerCase(subject.charAt(0))) return true;
        if (Pattern.compile("[a-z][A-Z]").matcher(subject).find()) return true;
        return Pattern.compile("(?i)\\s(uu)?id$").matcher(subject).find();
    }

    /** "Please check 3 fields: Email, Date of birth and Phone number." */
    public static String summary(List<String> labels) {
        List<String> distinct = labels.stream().distinct().toList();
        if (distinct.size() == 1) return "Please check " + distinct.get(0) + ".";
        int shown = Math.min(3, distinct.size());
        String names = String.join(", ", distinct.subList(0, shown));
        int rest = distinct.size() - shown;
        return "Please check " + distinct.size() + " fields: " + names
                + (rest > 0 ? " and " + rest + " more." : ".");
    }

    private static String finish(String message) {
        char last = message.charAt(message.length() - 1);
        return (last == '.' || last == '!' || last == '?') ? message : message + ".";
    }
}
