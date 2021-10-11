package com.oncolens.fhirengine.model;


import com.oncolens.fhirengine.FhirBridge;
import com.oncolens.fhirengine.FhirBridgeDSTU2Epic;
import com.oncolens.fhirengine.FhirBridgeR4Epic;

public enum FhirBridgeType {


    //**DSTU2CERNER(new FhirBridgeDSTU2(), "DSTU2CERNER"),
    DSTU2EPIC(new FhirBridgeDSTU2Epic(), "DSTU2EPIC"),
    //**DSTU2ATHENA(new FhirBridgeDSTU2Athena(), "DSTU2ATHENA")

    //**R4CERNER(new FhirBridgeR4(), "R4CERNER"),

    R4EPIC(new FhirBridgeR4Epic(), "R4EPIC");

    private final FhirBridge impl;
    private final String description;

    FhirBridgeType(FhirBridge impl, String description) {
        this.impl = impl;
        this.description = description;
    }

    public FhirBridge getImpl() {
        return impl;
    }
    public String getDescription() {
        return description;
    }

}

