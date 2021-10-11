package com.oncolens.fhirengine.util;

import ca.uhn.fhir.context.FhirContext;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.oncolens.fhirengine.model.FhirBridgeType;
import lombok.Data;

import java.util.List;

@Data
public class FhirConfig {

    @JsonIgnore
    private FhirContext fhirContext;
    private String authURL;

    private String tokenURL;
    private String fhirBaseURL;
    private String fhirSourceSystem;
    private String scope;
    private String grantType;
    private String clientId;
    private String clientSecret;
    private String fhirVersion;
    private String cohortSelectionType;
    private String cohortListFile;
    private String dataOutputFilePath;
    private String ccdOutputFilePath;
    private String auditOutputFilePath;
    private String documentContentType;
    private FhirBridgeType fhirBridgeType;
    private List<String> extracts;
    private String fhirAuthNameSpace;
    private String fhirAuthUrlKey;
    private String fhirTokenUrlKey;
    private String s3EndPoint;
    private String environment;
    private String customer;
    private String siteId;
}
