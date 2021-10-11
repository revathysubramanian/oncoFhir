package com.oncolens.fhirengine.model;

public enum AuditType {


    //TODO Fix the scope to something more meaningful once the app name has been finalized
    STARTED("com.oncolens.data.integrations.audit","Process started", "started"),
    COMPLETED("com.oncolens.data.integrations.audit","Process completed", "completed"),
    ERRORED("com.oncolens.data.integrations.audit","Process errored", "errored"),
    EXCEPTION_OCCURRED("com.oncolens.data.integrations.audit","Exception occurred", "exception_occurred"),
    VALIDATION_FAILED("com.oncolens.data.integrations.audit","Validation Error occurred", "validation_failed");

    private final String message;
    private final String scope;
    private final String status;

    AuditType(String scope, String message, String status) {
        this.message = message;
        this.scope = scope;
        this.status = status;
    }
    public String getMessage() {
        return message;
    }
    public String getScope() {
        return scope;
    }
    public String getStatus() {
        return status;
    }

}
