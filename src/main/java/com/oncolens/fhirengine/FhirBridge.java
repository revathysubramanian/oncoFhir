package com.oncolens.fhirengine;
import com.oncolens.fhirengine.util.FhirConfig;
import org.hl7.fhir.instance.model.api.IBaseResource;
import java.util.List;

public interface FhirBridge {
    void initialize(FhirConfig fc);

    String getAuthorizationToken();

    List<String> createPatientExtracts(IBaseResource patient, String identifier, List<String> extracts,
                                       List<IBaseResource> resourcesAll);

    List<String> createPatientCohort(List<String> cohortSelectionList, String cohortSelectionType,
                                     List<IBaseResource> resourcesAll);

    void processConformance();

    String extractPatientIdFromPatient(IBaseResource patient);
}


