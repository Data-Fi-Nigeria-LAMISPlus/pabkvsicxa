package com.lamiplus_common_api.audit;

public class AuditProcessingException extends RuntimeException {
    public AuditProcessingException(String message, Throwable cause) {
        super(message, cause);
    }
}
