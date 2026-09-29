package com.lamiplus_common_api.exception;

import com.lamiplus_common_api.api.PluginException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.InvalidDataAccessResourceUsageException;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;

import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiErrorResolverTest {

    private final ApiErrorResolver resolver = new ApiErrorResolver();

    private static String message(ApiError e) {
        return e.errors().get(0).getMessage();
    }

    @Test
    void businessExceptionWrappedByPluginExecutorKeepsItsStatusAndMessage() {
        BusinessException be = new BusinessException(StandardErrorCodes.RESOURCE_NOT_FOUND, "Patient not found");
        ApiError e = resolver.resolve(new CompletionException(be));

        assertEquals(HttpStatus.NOT_FOUND, e.status());
        assertEquals("RESOURCE_NOT_FOUND", e.primaryCode());
        assertEquals("Patient not found.", message(e));
    }

    @Test
    void businessExceptionWithTechnicalMessageIsNotEchoed() {
        BusinessException be = new BusinessException(StandardErrorCodes.INVALID_REQUEST,
                "could not execute statement; SQL [n/a]; constraint [fk_x]");
        ApiError e = resolver.resolve(be);

        assertEquals(HttpStatus.BAD_REQUEST, e.status());
        assertFalse(message(e).contains("SQL"));
    }

    @Test
    void securityExceptionIsAPlain403WithoutInternalReason() {
        ApiError e = resolver.resolve(new SecurityException("Unauthorized super admin mode"));

        assertEquals(HttpStatus.FORBIDDEN, e.status());
        assertFalse(message(e).contains("super admin"));
    }

    @Test
    void illegalStateIsConflictWhenReadableAndServerErrorWhenInternal() {
        ApiError readable = resolver.resolve(new IllegalStateException("Cannot delete system role: Doctor"));
        assertEquals(HttpStatus.CONFLICT, readable.status());
        assertEquals("Cannot delete system role: Doctor.", message(readable));

        ApiError internal = resolver.resolve(new IllegalStateException("Tenant context not set"));
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, internal.status());
        assertFalse(message(internal).contains("context"));

        ApiError notFound = resolver.resolve(new IllegalStateException("Group not found"));
        assertEquals(HttpStatus.NOT_FOUND, notFound.status());
    }

    @Test
    void duplicateEmailUsesSqlStateAndHidesTenantColumn() {
        SQLException sql = new SQLException("""
                ERROR: duplicate key value violates unique constraint "app_user_unique_email"
                  Detail: Key (tenant_id, email)=(t1, a@b.c) already exists.""", "23505");
        ApiError e = resolver.resolve(new DataIntegrityViolationException("could not execute statement", sql));

        assertEquals(HttpStatus.CONFLICT, e.status());
        assertEquals("This email address is already in use.", message(e));
        assertEquals("email", e.errors().get(0).getField());
    }

    @Test
    void missingReferencedRowNamesTheFieldNotTheTable() {
        SQLException sql = new SQLException("""
                ERROR: insert or update on table "ehr_visits" violates foreign key constraint "fk_patient_uuid"
                  Detail: Key (patient_id, tenant_id)=(x, t1) is not present in table "ehr_patient_person".""", "23503");
        ApiError e = resolver.resolve(new RuntimeException(new DataIntegrityViolationException("fk", sql)));

        assertEquals(HttpStatus.BAD_REQUEST, e.status());
        assertEquals("The selected patient doesn't exist or is no longer available.", message(e));
        assertEquals("patientId", e.errors().get(0).getField());
        assertFalse(message(e).contains("ehr_"));
    }

    @Test
    void deletingARowStillInUseIsAConflict() {
        SQLException sql = new SQLException("""
                ERROR: update or delete on table "roles" violates foreign key constraint "fk"
                  Detail: Key (id, tenant_id)=(4, t1) is still referenced from table "user_role".""", "23503");
        ApiError e = resolver.resolve(new DataIntegrityViolationException("fk", sql));

        assertEquals(HttpStatus.CONFLICT, e.status());
        assertEquals("RESOURCE_IN_USE", e.primaryCode());
    }

    @Test
    void notNullColumnBecomesAFieldLabel() {
        SQLException sql = new SQLException(
                "ERROR: null value in column \"date_of_birth\" of relation \"ehr_patient_person\" violates not-null constraint",
                "23502");
        ApiError e = resolver.resolve(new DataIntegrityViolationException("x", sql));

        assertEquals("Date of birth is required.", message(e));
        assertEquals("dateOfBirth", e.errors().get(0).getField());
    }

    @Test
    void missingTableIsAServiceProblemNotTheUsers() {
        SQLException sql = new SQLException("ERROR: relation \"ehr_visits\" does not exist", "42P01");
        ApiError e = resolver.resolve(new InvalidDataAccessResourceUsageException("x", sql));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, e.status());
        assertFalse(message(e).contains("ehr_visits"));
    }

    @Test
    void severalInvalidFieldsGetASummaryFirst() {
        BeanPropertyBindingResult result = new BeanPropertyBindingResult(new Object(), "patient");
        result.addError(new FieldError("patient", "dateOfBirth", "must not be null"));
        result.addError(new FieldError("patient", "phoneNumber", "must match \"\\d+\""));
        ApiError e = resolver.resolve(new BindException(result));

        assertEquals(HttpStatus.BAD_REQUEST, e.status());
        assertEquals(3, e.errors().size());
        assertNull(e.errors().get(0).getField());
        assertEquals("Please check 2 fields: Date of birth, Phone number.", message(e));
        assertEquals("Date of birth is required.", e.errors().get(1).getMessage());
        assertEquals("Phone number has an invalid format.", e.errors().get(2).getMessage());
    }

    @Test
    void badEnumValueIsExplainedWithoutClassNames() {
        ApiError e = resolver.resolve(new IllegalArgumentException(
                "No enum constant lamisplus.pbh.domain.VisitStatus.DONE"));

        assertEquals(HttpStatus.BAD_REQUEST, e.status());
        assertEquals("'DONE' is not an accepted value.", message(e));
    }

    @Test
    void pluginExceptionWithInternalMessageIsGeneric() {
        ApiError e = resolver.resolve(new PluginException("Failed to load plugin: java.io.IOException: Stream closed"));

        assertEquals("PLUGIN_ERROR", e.primaryCode());
        assertFalse(message(e).contains("java.io"));
    }

    @Test
    void appExceptionsAreMappedByNamingConvention() {
        class PatientNotFoundException extends RuntimeException {
            PatientNotFoundException(String m) { super(m); }
        }
        ApiError e = resolver.resolve(new PatientNotFoundException("Patient with hospital number 123 was not found"));
        assertEquals(HttpStatus.NOT_FOUND, e.status());
    }

    @Test
    void unknownExceptionIsAGeneric500() {
        ApiError e = resolver.resolve(new NullPointerException("Cannot invoke \"String.length()\" because \"x\" is null"));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, e.status());
        assertEquals("INTERNAL_ERROR", e.primaryCode());
    }

    @Test
    void sanitizerAcceptsPlainLanguageAndRejectsInternals() {
        assertTrue(UserMessages.isSafe("Excel file contains no data rows"));
        assertTrue(UserMessages.isSafe("User profile is incomplete"));
        assertTrue(UserMessages.isSafe("Duplicate transaction detected"));
        assertFalse(UserMessages.isSafe("SYNC_SERVER_URL not set — cannot provision/rotate sync key"));
        assertFalse(UserMessages.isSafe("No active EMF for plugin: ehr"));
        assertFalse(UserMessages.isSafe("could not execute statement; SQL [n/a]"));
        assertFalse(UserMessages.isSafe("Cannot invoke \"java.lang.String.length()\""));
        assertEquals("Date of birth", UserMessages.label("dateOfBirth"));
        assertEquals("Facility", UserMessages.label("facility_id"));
        assertEquals("Unit price", UserMessages.label("items[0].unitPrice"));
    }

    @Test
    void summaryListsAtMostThreeFields() {
        assertEquals("Please check 5 fields: A, B, C and 2 more.",
                UserMessages.summary(List.of("A", "B", "C", "D", "E")));
    }

    @Test
    void writerProducesTheSameArrayShapeAndEscapesText() {
        String json = ApiErrorWriter.toJson(
                List.of(new ErrorModel("CODE", "Say \"hi\"\nnow", null)), "ABC123");
        assertEquals("[{\"code\":\"CODE\",\"message\":\"Say \\\"hi\\\"\\nnow (Ref: ABC123)\",\"field\":null}]", json);
    }

    @Test
    void validationMessagesWithPropertyNamesBecomeReadable() {
        assertEquals("Patient is required.", UserMessages.fieldMessage("patientUuid", "patientUuid is required"));
        assertEquals("Vital sign date is required.", UserMessages.fieldMessage("vitalSignDate", "vital_sign_date is required"));
        assertEquals("Service location is required.", UserMessages.fieldMessage("serviceLocation", "service Location is required"));
        assertEquals("Drug is required.", UserMessages.fieldMessage("drugUuid", "drugUuid is mandatory"));
        assertEquals("Patient is required.", UserMessages.fieldMessage("patientUuid", "Patient UUID is required"));
        assertEquals("Max visit duration hours must be at least 1.",
                UserMessages.fieldMessage("maxVisitDurationHours", "maxVisitDurationHours must be at least 1"));
        // Already a proper phrase — left as written.
        assertEquals("Date enrolled on ART cannot be in the future.",
                UserMessages.fieldMessage("dateEnrolledOnArt", "Date enrolled on ART cannot be in the future"));
        assertEquals("Number of kits distributed must be at least 1.",
                UserMessages.fieldMessage("kits", "Number of kits distributed must be at least 1"));
        // Bean Validation defaults still get the field label.
        assertEquals("Date of birth is required.", UserMessages.fieldMessage("dateOfBirth", "must not be null"));
    }
}
