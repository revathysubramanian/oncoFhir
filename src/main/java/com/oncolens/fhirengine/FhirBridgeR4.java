package com.oncolens.fhirengine;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.okhttp.client.OkHttpRestfulClientFactory;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.client.interceptor.BearerTokenAuthInterceptor;
import ca.uhn.fhir.rest.server.exceptions.InvalidRequestException;
import ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException;
import ca.uhn.fhir.util.BundleUtil;

import com.oncolens.fhirengine.model.AuditType;
import com.oncolens.fhirengine.util.FhirConfig;
import com.oncolens.fhirengine.util.FhirUtilBase;
//**import com.oncolens.fhir.connector.client.util.ClientCredentials;
import com.oncolens.fhirengine.util.S3Access;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.instance.model.api.IBaseBundle;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.*;
import org.hl7.fhir.r4.model.DocumentReference.DocumentReferenceContentComponent;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

@Slf4j
public class FhirBridgeR4 implements FhirBridge {
    private final FhirContext fhirContext = FhirContext.forR4();
    private FhirConfig fhirConfig;
    private String authToken;
    private IGenericClient fhirClient = null;

    public IGenericClient getFhirClient() {
        return fhirClient;
    }
    public void initialize(FhirConfig fc) {
        FhirUtilBase.setOutputPath(fc);
        this.fhirConfig = fc;
        String serverBase = fhirConfig.getFhirBaseURL();
        fhirContext.setRestfulClientFactory(new OkHttpRestfulClientFactory(fhirContext));
        fhirClient = fhirContext.newRestfulGenericClient(serverBase);
        log.info("Going to reach out to the Conformance End Point -{}", serverBase);
        processConformance();
        if ((this.fhirConfig.getTokenURL() == null) || (this.fhirConfig.getAuthURL() == null)) {
            log.error("Failed to get authorization URLs. Cannot proceed");
            //FhirProcessor.auditEventList.add(FhirUtilBase.processAuditEvent(fc, AuditType.EXCEPTION_OCCURRED,"getTokenURL",FhirProcessor.PROCESSUUID, fc.getCohortListFile(), "Unable to find token URL using fhirbase :" + fc.getFhirBaseURL() + " token key :" + fc.getFhirTokenUrlKey()));
            throw new RuntimeException("Failed to obtain authorization URLs... Cannot proceed");
        }
        log.info("Going to get Authorization token ...");
        this.authToken = getAuthorizationToken();
        BearerTokenAuthInterceptor authInterceptor = new BearerTokenAuthInterceptor(this.authToken);
        this.fhirClient.registerInterceptor(authInterceptor);
        log.info("FhirBridge initialization successful");
    }

    @Override
    public String getAuthorizationToken() {
        String token = null;
        try {
            //**token = ClientCredentials.getToken(this.fhirConfig);
        } catch (Exception e) {
            //**FhirProcessor.auditEventList.add(FhirUtilBase.processAuditEvent(fhirConfig, AuditType.EXCEPTION_OCCURRED,"getAccessToken",FhirProcessor.PROCESSUUID, fhirConfig.getCohortListFile(), "Unable to get auth token :" + fhirConfig.getFhirBaseURL() + " token url :" + fhirConfig.getTokenURL() + " Exception - " + FhirUtilBase.getExceptionAsString(e)));
            log.error("Exception occurred when trying to get auth token, e");
            throw new RuntimeException(e);
        }
        log.info("Token received successfully");
        return token;
    }

    @Override
    public List<String> createPatientExtracts(IBaseResource patient, String identifier, List<String> extracts,
                                              List<IBaseResource> resourcesAll) {

        List<String> resourcesJsonAll = new ArrayList<>();
        List<IBaseResource> currentResources = new ArrayList<>();
        String patientid = extractPatientIdFromPatient(patient);
        currentResources.add(patient);
        // Get Observations
        if (extracts.contains("observation")) {
            List<IBaseResource> observationResources = getObservationsForPatientId(patientid);
            currentResources.addAll(observationResources);
            extracts.remove("observation");
        }
        // Get Service Requests
        if (extracts.contains("servicerequest")) {
            List<IBaseResource> serviceRequestResources = getServiceRequestsForPatientId(patientid);
            currentResources.addAll(serviceRequestResources);
            extracts.remove("servicerequest");
        }

        // Get Conditions
        if (extracts.contains("condition")) {
            List<IBaseResource> conditionResources = getConditionsForPatientId(patientid);
            currentResources.addAll(conditionResources);
            extracts.remove("condition");
        }

        // Get Care Team
        if (extracts.contains("careteam")) {
            List<IBaseResource> careTeamResources = getCareTeamForPatientId(patientid);
            currentResources.addAll(careTeamResources);
            extracts.remove("careteam");
        }
        // Get Family Member History
        if (extracts.contains("familymemberhistory")) {
            List<IBaseResource> familyMemberHistories = getFamilyMemberHistoryForPatientId(patientid);
            currentResources.addAll(familyMemberHistories);
            extracts.remove("familymemberhistory");
        }
        // Get Encounters
        if (extracts.contains("encounter")) {
            List<IBaseResource> encounterResources = getEncountersForPatientId(patientid);
            currentResources.addAll(encounterResources);
            extracts.remove("encounter");
        }

        // Get DiagnosticReport
        if (extracts.contains("diagnosticreport")) {
            List<IBaseResource> diagnosticReports = getDiagnosticReportsForPatientId(patientid);
            currentResources.addAll(diagnosticReports);
            extracts.remove("diagnosticreport");
        }

        // Get MedicationRequest
        if (extracts.contains("medicationrequest")) {
            List<IBaseResource> medicationResources = getMedicationRequestsForPatientId(patientid);
            currentResources.addAll(medicationResources);
            extracts.remove("medicationrequest");
        }

        // Get Careplan
        if (extracts.contains("careplan")) {
            List<IBaseResource> careplans = getCarePlansForPatientId(patientid);
            currentResources.addAll(careplans);
            extracts.remove("careplan");
        }

        // Get Goal
        if (extracts.contains("goal")) {
            List<IBaseResource> goals = getGoalsForPatientId(patientid);
            currentResources.addAll(goals);
            extracts.remove("goal");
        }

        // Get Allergies
        if (extracts.contains("allergyintolerance")) {
            List<IBaseResource> allergies = getAllergyIntoleranceForPatientId(patientid);
            currentResources.addAll(allergies);
            extracts.remove("allergyintolerance");
        }

        // Get Immunizations
        if (extracts.contains("immunization")) {
            List<IBaseResource> immunizations = getImmunizationsForPatientId(patientid);
            currentResources.addAll(immunizations);
            extracts.remove("immunization");
        }

        // Get Procedures
        if (extracts.contains("procedure")) {
            List<IBaseResource> procedureResources = getProceduresForPatientId(patientid);
            currentResources.addAll(procedureResources);
            extracts.remove("procedure");
        }

        if (extracts.contains("documentreference")) {
            getDocumentsForPatientId(patientid, identifier);
            extracts.remove("documentreference");
        }

        if (extracts.contains("operationccd")) {
            byte[] ccd = getCCDUsingOperationForPatientId(patientid, identifier);
            if (ccd != null) {
               //** try {
                    ByteArrayInputStream bis = new ByteArrayInputStream(ccd);
                    //**S3Access.getInstance(fhirConfig).upload(bis, fhirConfig, identifier,
                           //** fhirConfig.getCcdOutputFilePath(), null, "application/xml");
                //**} catch (IOException e1) {
                    //**FhirProcessor.auditEventList.add(FhirUtilBase.processAuditEvent(fhirConfig, AuditType.VALIDATION_FAILED,"Patient/" + patient.getIdElement().getIdPart(),FhirProcessor.PROCESSUUID, fhirConfig.getCohortListFile(), "Exception occurred during operationccd and hence skipping, for " + identifier + " due to " + FhirUtilBase.getExceptionAsString(e1)));
                //**}
            }
            extracts.remove("operationccd");
        }
        // Process all the resources to Json
        for (IBaseResource currentResource : currentResources) {
            String jsonStr = FhirUtilBase.getJsonForResource(fhirContext, currentResource);
            resourcesJsonAll.add(jsonStr);
            // System.out.println(jsonStr);
        }
        resourcesAll.addAll(currentResources);
        return resourcesJsonAll;
    }

    @Override
    public List<String> createPatientCohort(List<String> cohortSelectionList, String cohortSelectionType,
                                            List<IBaseResource> patients) {

        if (cohortSelectionType.contentEquals("patientid")) {
             return createPatientCohortUsingIdentifier(cohortSelectionList, patients);
        }
        else if (cohortSelectionType.contentEquals("nameDOB")) {
            //return createPatientCohortUsingNameDOB(cohortSelectionList, patients);
        }
        else {
            FhirProcessor.auditEventList.add(FhirUtilBase.processAuditEvent(fhirConfig, AuditType.ERRORED,
                    "createPatientCohort", FhirProcessor.PROCESSUUID, fhirConfig.getCohortListFile(),
                    "Invalid cohortSelectionType : " + cohortSelectionType));
        }
        return null;
    }

    private List<String> createPatientCohortUsingIdentifier(List<String> cohortSelectionList,
                                                            List<IBaseResource> patients) {
        List<String> patientIds = new ArrayList<>();

        for (String patientInfo : cohortSelectionList) {
            String system = patientInfo.split(",")[0].trim();
            String identifier = patientInfo.split(",")[1].trim();
            List<IBaseResource> patientResources = getPatientUsingIdentifierSystem(system, identifier);
            if (patientResources.size() != 1) {
                FhirProcessor.auditEventList.add(FhirUtilBase.processAuditEvent(fhirConfig, AuditType.VALIDATION_FAILED,
                        "Patient:" + patientInfo, FhirProcessor.PROCESSUUID, fhirConfig.getCohortListFile(),
                        patientResources.size() + " patients returned for " + patientInfo + " ,expected one "));
                continue;
            }
            IBaseResource res = patientResources.get(0);
            if (res instanceof Patient) {
                // Add patient to the list of resources
                patients.add(res);
                patientIds.add(identifier);
            } else if (res instanceof OperationOutcome) {
                OperationOutcome o = (OperationOutcome) res;
                FhirProcessor.auditEventList.add(FhirUtilBase.processAuditEvent(fhirConfig, AuditType.VALIDATION_FAILED,
                        "Patient:" + patientInfo, FhirProcessor.PROCESSUUID, fhirConfig.getCohortListFile(),
                        "Patient retrieval unsuccessful for " + patientInfo + " due to " + o.getIssue().get(0).getDetails().getText()));
            } else {
                FhirProcessor.auditEventList.add(FhirUtilBase.processAuditEvent(fhirConfig, AuditType.VALIDATION_FAILED,
                        "Patient:" + patientInfo, FhirProcessor.PROCESSUUID, fhirConfig.getCohortListFile(),
                        "Patient retrieval unsuccessful for " + patientInfo + " due to unknown reasons"));
            }
        }

        return patientIds;
    }

    @Override
    public void processConformance() {
        CapabilityStatement cnf = fhirClient.capabilities().ofType(CapabilityStatement.class).execute();
        log.info("Retrieved capability for fhir base URL -{} ", fhirClient.getServerBase());
        List<Extension> extensions =
                cnf.getRest().get(0).getSecurity().getExtensionsByUrl(fhirConfig.getFhirAuthNameSpace()).get(0).getExtension();
        if (extensions.size() > 0) {
            log.info("Found authorization details in conformance, going to look for auth and token urls");
            for (Extension e : extensions) {
                if (e.getUrl().contentEquals(fhirConfig.getFhirAuthUrlKey())) {
                    log.info("Found authorization URL details in conformance");
                    String authURL = e.getValueAsPrimitive().getValueAsString();
                    fhirConfig.setAuthURL(authURL);
                }
                if (e.getUrl().contentEquals(fhirConfig.getFhirTokenUrlKey())) {
                    log.info("Found token URL details in conformance");
                    String tokenURL = e.getValueAsPrimitive().getValueAsString();
                    fhirConfig.setTokenURL(tokenURL);
                }
            }
        }
    }

    @Override
    public String extractPatientIdFromPatient(IBaseResource patient) {
        return patient.getIdElement().getIdPart();
    }

    public List<IBaseResource> getPatientUsingIdentifierSystem(String system, String identifier) {
        Bundle bundle = fhirClient.search()
                .forResource(Patient.class)
                .and(Patient.IDENTIFIER.exactly().systemAndIdentifier(system, identifier))
                .returnBundle(Bundle.class)
                .execute();

        List<IBaseResource> patientResources = new ArrayList<>(BundleUtil.toListOfResources(fhirContext, bundle));
        FhirUtilBase.ensureResourceType(patientResources, Patient.class);
        return patientResources;
    }

    public List<IBaseResource> getObservationsForPatientId(String patientId) {
        List<IBaseResource> observations = new ArrayList<>();
        try {
            Bundle bundle = fhirClient.search()
                    .forResource(Observation.class)
                    .where(Observation.PATIENT.hasId(patientId))
                    .returnBundle(Bundle.class).execute();
            observations.addAll(BundleUtil.toListOfResources(fhirContext, bundle));
            // Load the subsequent pages
            while (bundle.getLink(IBaseBundle.LINK_NEXT) != null) {
                bundle = fhirClient.loadPage().next(bundle).execute();
                observations.addAll(BundleUtil.toListOfResources(fhirContext, bundle));
            }
            FhirUtilBase.ensureResourceType(observations, Observation.class);
        } catch (ResourceNotFoundException e) {
            // ca.uhn.fhir.rest.server.exceptions.

        }
        log.info("{} Observations fetched!", observations.size());
        return observations;
    }


    public List<IBaseResource> getCarePlansForPatientId(String patientId) {
        List<IBaseResource> careplans = new ArrayList<>();
        try {
            Bundle bundle = fhirClient.search()
                    .forResource(CarePlan.class)
                    .where(CarePlan.PATIENT.hasId(patientId))
                    .returnBundle(Bundle.class).execute();
            careplans.addAll(BundleUtil.toListOfResources(fhirContext, bundle));
            // Load the subsequent pages
            while (bundle.getLink(IBaseBundle.LINK_NEXT) != null) {
                bundle = fhirClient.loadPage().next(bundle).execute();
                careplans.addAll(BundleUtil.toListOfResources(fhirContext, bundle));
            }
            FhirUtilBase.ensureResourceType(careplans, CarePlan.class);
        } catch (ResourceNotFoundException e) {
            // ca.uhn.fhir.rest.server.exceptions.

        }
        log.info("{} care plans fetched!", careplans.size());
        return careplans;
    }



    public List<IBaseResource> getDiagnosticReportsForPatientId(String patientId) {
        List<IBaseResource> diagnosticReports = new ArrayList<>();
        try {
            Bundle bundle = fhirClient.search()
                    .forResource(DiagnosticReport.class)
                    .where(DiagnosticReport.PATIENT.hasId(patientId))
                    .returnBundle(Bundle.class).execute();
            diagnosticReports.addAll(BundleUtil.toListOfResources(fhirContext, bundle));
            // Load the subsequent pages
            while (bundle.getLink(IBaseBundle.LINK_NEXT) != null) {
                bundle = fhirClient.loadPage().next(bundle).execute();
                diagnosticReports.addAll(BundleUtil.toListOfResources(fhirContext, bundle));
            }
            FhirUtilBase.ensureResourceType(diagnosticReports, DiagnosticReport.class);
        } catch (ResourceNotFoundException e) {
            // ca.uhn.fhir.rest.server.exceptions.
        }
        log.info("{} Diagnostic Reports fetched!", diagnosticReports.size());
        return diagnosticReports;
    }

    public List<IBaseResource> getConditionsForPatientId(String patientId) {
        List<IBaseResource> conditions = new ArrayList<>();
        try {
            Bundle bundle = fhirClient.search()
                    .forResource(Condition.class)
                    .where(Condition.PATIENT.hasId(patientId))
                    .returnBundle(Bundle.class).execute();
            conditions.addAll(BundleUtil.toListOfResources(fhirContext, bundle));
            // Load the subsequent pages
            while (bundle.getLink(IBaseBundle.LINK_NEXT) != null) {
                bundle = fhirClient.loadPage().next(bundle).execute();
                conditions.addAll(BundleUtil.toListOfResources(fhirContext, bundle));
            }
            FhirUtilBase.ensureResourceType(conditions, Condition.class);
        } catch (ResourceNotFoundException e) {
            // ca.uhn.fhir.rest.server.exceptions.

        }
        log.info("{} Conditions fetched!", conditions.size());
        return conditions;
    }

    public List<IBaseResource> getProceduresForPatientId(String patientId) {
        List<IBaseResource> procedures = new ArrayList<>();
        try {
            Bundle bundle = fhirClient.search()
                    .forResource(Procedure.class)
                    .where(Procedure.PATIENT.hasId(patientId))
                    .returnBundle(Bundle.class).execute();
            procedures.addAll(BundleUtil.toListOfResources(fhirContext, bundle));
            // Load the subsequent pages
            while (bundle.getLink(IBaseBundle.LINK_NEXT) != null) {
                bundle = fhirClient.loadPage().next(bundle).execute();
                procedures.addAll(BundleUtil.toListOfResources(fhirContext, bundle));
            }
            FhirUtilBase.ensureResourceType(procedures, Procedure.class);
        } catch (ResourceNotFoundException e) {
            // ca.uhn.fhir.rest.server.exceptions.

        }
        log.info("{} Procedures fetched!", procedures.size());
        return procedures;
    }


    public List<IBaseResource> getAllergyIntoleranceForPatientId(String patientId) {
        List<IBaseResource> allergies = new ArrayList<>();
        try {
            Bundle bundle = fhirClient.search()
                    .forResource(AllergyIntolerance.class)
                    .where(AllergyIntolerance.PATIENT.hasId(patientId))
                    .returnBundle(Bundle.class).execute();
            allergies.addAll(BundleUtil.toListOfResources(fhirContext, bundle));
            // Load the subsequent pages
            while (bundle.getLink(IBaseBundle.LINK_NEXT) != null) {
                bundle = fhirClient.loadPage().next(bundle).execute();
                allergies.addAll(BundleUtil.toListOfResources(fhirContext, bundle));
            }
            FhirUtilBase.ensureResourceType(allergies, AllergyIntolerance.class);
        } catch (ResourceNotFoundException e) {
            // ca.uhn.fhir.rest.server.exceptions.

        }
        log.info("{} allergies fetched!", allergies.size());
        return allergies;
    }

    public List<IBaseResource> getImmunizationsForPatientId(String patientId) {
        List<IBaseResource> immunizations = new ArrayList<>();
        try {
            Bundle bundle = fhirClient.search()
                    .forResource(Immunization.class)
                    .where(Immunization.PATIENT.hasId(patientId))
                    .returnBundle(Bundle.class).execute();
            immunizations.addAll(BundleUtil.toListOfResources(fhirContext, bundle));
            // Load the subsequent pages
            while (bundle.getLink(IBaseBundle.LINK_NEXT) != null) {
                bundle = fhirClient.loadPage().next(bundle).execute();
                immunizations.addAll(BundleUtil.toListOfResources(fhirContext, bundle));
            }
            FhirUtilBase.ensureResourceType(immunizations, Immunization.class);
        } catch (ResourceNotFoundException e) {
            // ca.uhn.fhir.rest.server.exceptions.

        }
        log.info("{} immunizations fetched!", immunizations.size());
        return immunizations;
    }

    public List<IBaseResource> getServiceRequestsForPatientId(String patientId) {
        List<IBaseResource> serviceRequests = new ArrayList<>();
        try {
            Bundle bundle = fhirClient.search()
                    .forResource(ServiceRequest.class)
                    .where(ServiceRequest.PATIENT.hasId(patientId))
                    .returnBundle(Bundle.class).execute();
            serviceRequests.addAll(BundleUtil.toListOfResources(fhirContext, bundle));
            // Load the subsequent pages
            while (bundle.getLink(IBaseBundle.LINK_NEXT) != null) {
                bundle = fhirClient.loadPage().next(bundle).execute();
                serviceRequests.addAll(BundleUtil.toListOfResources(fhirContext, bundle));
            }
            FhirUtilBase.ensureResourceType(serviceRequests, ServiceRequest.class);
        } catch (ResourceNotFoundException e) {
            // ca.uhn.fhir.rest.server.exceptions.

        }
        log.info("{} service requests fetched!", serviceRequests.size());
        return serviceRequests;
    }
    public List<IBaseResource> getFamilyMemberHistoryForPatientId(String patientId) {
        List<IBaseResource> familyMemberHistories = new ArrayList<>();
        try {
            Bundle bundle = fhirClient.search()
                    .forResource(FamilyMemberHistory.class)
                    .where(FamilyMemberHistory.PATIENT.hasId(patientId))
                    .returnBundle(Bundle.class).execute();
            familyMemberHistories.addAll(BundleUtil.toListOfResources(fhirContext, bundle));
            // Load the subsequent pages
            while (bundle.getLink(IBaseBundle.LINK_NEXT) != null) {
                bundle = fhirClient.loadPage().next(bundle).execute();
                familyMemberHistories.addAll(BundleUtil.toListOfResources(fhirContext, bundle));
            }
            FhirUtilBase.ensureResourceType(familyMemberHistories, FamilyMemberHistory.class);
        } catch (ResourceNotFoundException e) {
            // ca.uhn.fhir.rest.server.exceptions.

        }
        log.info("{} family member histories fetched!", familyMemberHistories.size());
        return familyMemberHistories;
    }

    public List<IBaseResource> getCareTeamForPatientId(String patientId) {
        List<IBaseResource> careTeam = new ArrayList<>();
        try {
            Bundle bundle = fhirClient.search()
                    .forResource(CareTeam.class)
                    .where(CareTeam.PATIENT.hasId(patientId))
                    .returnBundle(Bundle.class).execute();
            careTeam.addAll(BundleUtil.toListOfResources(fhirContext, bundle));
            // Load the subsequent pages
            while (bundle.getLink(IBaseBundle.LINK_NEXT) != null) {
                bundle = fhirClient.loadPage().next(bundle).execute();
                careTeam.addAll(BundleUtil.toListOfResources(fhirContext, bundle));
            }
            FhirUtilBase.ensureResourceType(careTeam, CareTeam.class);
        } catch (ResourceNotFoundException e) {
            // ca.uhn.fhir.rest.server.exceptions.

        }
        log.info("{} care team records fetched!", careTeam.size());
        return careTeam;
    }


    public List<IBaseResource> getGoalsForPatientId(String patientId) {
        log.info("Inside goals");
        List<IBaseResource> goals = new ArrayList<>();
        try {
            Bundle bundle = fhirClient.search()
                    .forResource(Goal.class)
                    .where(Goal.PATIENT.hasId("enNQvBlRfDCq4NsM90Kr8QQ3"))
                   // .and(Goal.LIFECYCLE_STATUS.exactly().code("active"))
                    .returnBundle(Bundle.class).execute();
            goals.addAll(BundleUtil.toListOfResources(fhirContext, bundle));
            // Load the subsequent pages
            while (bundle.getLink(IBaseBundle.LINK_NEXT) != null) {
                bundle = fhirClient.loadPage().next(bundle).execute();
                goals.addAll(BundleUtil.toListOfResources(fhirContext, bundle));
            }
            FhirUtilBase.ensureResourceType(goals, Goal.class);
        } catch (ResourceNotFoundException e) {
            // ca.uhn.fhir.rest.server.exceptions.
            log.info("No goals found");

        }
        log.info("{} goals fetched!", goals.size());
        return goals;
    }



    public List<IBaseResource> getEncountersForPatientId(String patientId) {
        List<IBaseResource> encounters = new ArrayList<>();
        try {
            Bundle bundle = fhirClient.search()
                    .forResource(Encounter.class)
                    .include(Encounter.INCLUDE_PRACTITIONER.asNonRecursive())
                    .where(Encounter.PATIENT.hasId(patientId))
                    .returnBundle(Bundle.class).execute();
            encounters.addAll(BundleUtil.toListOfResources(fhirContext, bundle));
            // Load the subsequent pages
            while (bundle.getLink(IBaseBundle.LINK_NEXT) != null) {
                bundle = fhirClient.loadPage().next(bundle).execute();
                encounters.addAll(BundleUtil.toListOfResources(fhirContext, bundle));
            }
            //FhirUtilBase.ensureResourceType(encounters, Encounter.class);
        } catch (ResourceNotFoundException e) {
            // ca.uhn.fhir.rest.server.exceptions.

        }
        log.info("{} Encounters fetched!", encounters.size());
        return encounters;

    }

    public List<IBaseResource> getMedicationRequestsForPatientId(String patientId) {
        List<IBaseResource> medications = new ArrayList<>();
        try {
            Bundle bundle = fhirClient.search()
                    .forResource(MedicationRequest.class)
                    .where(MedicationRequest.PATIENT.hasId(patientId))
                    .returnBundle(Bundle.class).execute();
            medications.addAll(BundleUtil.toListOfResources(fhirContext, bundle));
            // Load the subsequent pages
            while (bundle.getLink(IBaseBundle.LINK_NEXT) != null) {
                bundle = fhirClient.loadPage().next(bundle).execute();
                medications.addAll(BundleUtil.toListOfResources(fhirContext, bundle));
            }
            FhirUtilBase.ensureResourceType(medications, MedicationRequest.class);
        } catch (ResourceNotFoundException e) {
            // ca.uhn.fhir.rest.server.exceptions.

        }
        log.info("{} Medications fetched!", medications.size());
        return medications;
    }


    public void getDocumentsForPatientId(String patientId, String identifier) {
        String searchURL = "DocumentReference?patient=" + patientId;
        List<IBaseResource> documentReferences = getResourcesByURL(searchURL, true, DocumentReference.class);
        log.info("{} documentReferences fetched!", documentReferences.size());
        for (IBaseResource documentReference : documentReferences) {
            DocumentReference res = (DocumentReference) documentReference;
            List<DocumentReferenceContentComponent> contents = res.getContent();
            //It is possible to have the contents in different formats, We should look for application/zip content type
            for (DocumentReferenceContentComponent c : contents) {
                String contentType = c.getAttachment().getContentType();
                String contentURL = c.getAttachment().getUrl();
                log.info("Content Type is " + contentType);
                log.info("Content URL is " + contentURL);
                if (contentType != null && contentURL != null) {
                    String docURL;
                    if (FhirUtilBase.isRelativeURL(contentURL)) {
                        docURL = getFhirConfig().getFhirBaseURL() + "/" + c.getAttachment().getUrl();
                    } else {
                        docURL = contentURL;
                    }
                    if (contentType.equalsIgnoreCase(fhirConfig.getDocumentContentType())) {
                        getCCDADocument(docURL, res.getIdElement().getIdPart(), patientId);
                    } else {
                        log.info("Document content type did not match. Found {} looking for {}", contentType,
                                fhirConfig.getDocumentContentType());
                    }
                } else
                    log.info("Content type and URL were null");
            }

        }
    }

    /*
     * The getCCDUsingOperationForPatientId method needs the logicalid of the patient id order to generate the
     * ccd. MRNs or other identifiers do not work. So we may first need to get the
     * id before calling this method
     */
    public byte[] getCCDUsingOperationForPatientId(String patientid, String identifier) {
        byte[] decodedBytes = null;
        try {
            Parameters par = fhirClient.operation().onType(Binary.class).named("autogen-ccd-if")
                    .withParameter(Parameters.class, "patient", new StringType(patientid)).useHttpGet().execute();
            Binary b = (Binary) par.getParameter().get(0).getResource();
            decodedBytes = Base64.getDecoder().decode(b.getContentAsBase64());
        } catch (Exception e) {
            FhirProcessor.auditEventList.add(FhirUtilBase.processAuditEvent(fhirConfig, AuditType.VALIDATION_FAILED,"Patient/" + patientid,FhirProcessor.PROCESSUUID, fhirConfig.getCohortListFile(), "Exception occurred getting CCD using operationccd and hence skipping, for " + identifier + " due to " + FhirUtilBase.getExceptionAsString(e)));

        }
        return decodedBytes;
    }

    public List<IBaseResource> getResourcesByURL(String searchURL, boolean checkResourceType,
                                                 Class<?> fhirResourceClass) {
        // String searchUrlExample = Patient?identifier=foo";

        // Search URL can also be a relative URL in which case the client's base
        // URL will be added to it
        // String searchUrl = "Patient?_id=12595988";
        // String searchURL =
        // "Patient?identifier=urn:oid:2.16.840.1.113883.6.1000|490176388";
        // "system": "urn:oid:2.16.840.1.113883.6.1000",
        // "value": "490176388"

        List<IBaseResource> resources = new ArrayList<>();
        Bundle bundle;
        try {
            bundle = fhirClient.search()
                    .byUrl(searchURL)
                    .returnBundle(Bundle.class).execute();
        } catch (InvalidRequestException e) { //This error at runtime in production usually means bad data (Epic), so
            // log and continue with getting other resources
            log.error("Exception occurred getting resource by url- error",  e);
            FhirProcessor.auditEventList.add(FhirUtilBase.processAuditEvent(fhirConfig, AuditType.VALIDATION_FAILED,searchURL , FhirProcessor.PROCESSUUID, fhirConfig.getCohortListFile(), "Exception occurred getting resources by URL - " + FhirUtilBase.getExceptionAsString(e)));
            return resources;
        }
        resources.addAll(BundleUtil.toListOfResources(fhirContext, bundle));
        // Load the subsequent pages
        while (bundle.getLink(IBaseBundle.LINK_NEXT) != null) {
            bundle = fhirClient.loadPage().next(bundle).execute();
            resources.addAll(BundleUtil.toListOfResources(fhirContext, bundle));
        }
        if (checkResourceType && (fhirResourceClass != null)) {
            FhirUtilBase.ensureResourceTypeNew(resources, fhirResourceClass, OperationOutcome.class);
        }
        return resources;
    }

    public FhirContext getFhirContext() {
        return fhirContext;
    }

    public FhirConfig getFhirConfig() {
        return fhirConfig;
    }

    public void setFhirConfig(FhirConfig fhirConfig) {
        this.fhirConfig = fhirConfig;
    }

    public void getCCDADocument(String documentReferenceURL, String documentID, String patientId) {
        try {
            Binary b = fhirClient.read()
                    .resource(Binary.class)
                    .withUrl(documentReferenceURL).execute();
            byte[] decodedBytes = Base64.getDecoder().decode(b.getContentAsBase64());
            ByteArrayInputStream bis = new ByteArrayInputStream(decodedBytes);
            FhirUtilBase.writeBytesToFile(getFhirConfig().getCcdOutputFilePath(), patientId, decodedBytes);
            S3Access.getInstance(fhirConfig).upload(bis.readAllBytes(), fhirConfig, patientId + "-" + documentID,
                    fhirConfig.getCcdOutputFilePath(), null, fhirConfig.getDocumentContentType());
        } catch (Exception e) {
            FhirProcessor.auditEventList.add(FhirUtilBase.processAuditEvent(fhirConfig, AuditType.VALIDATION_FAILED,"Binary/" + documentID,FhirProcessor.PROCESSUUID, fhirConfig.getCohortListFile(), "Exception occurred getting CCD document and hence skipping, for patient " + patientId + " due to " + FhirUtilBase.getExceptionAsString(e)));
        }
    }
    //This method assumes that the document is a FHIR Binary Resource. If not please override this
    //method in the EHR specific sub class
    //Ex: https://apporchard.epic.com/interconnect-aocurprd-oauth/api/FHIR/DSTU2/Binary/TTrIy7p9Oa1P93a0kA.jp.zFCbm.IQAeagjWfV0Ml78iLFTwyWsWRUnvsM8MfBgziln3b6sBjTp0YaT7bqKKdugB"

    public List<IBaseResource> getResourcesByURL(String searchURL) {
        return getResourcesByURL(searchURL, false, null);
    }

}
