package com.oncolens.fhirengine;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.model.api.ExtensionDt;
import ca.uhn.fhir.model.dstu2.resource.*;
import ca.uhn.fhir.model.dstu2.resource.DocumentReference.Content;
import ca.uhn.fhir.model.primitive.StringDt;
import ca.uhn.fhir.model.primitive.UriDt;
import ca.uhn.fhir.okhttp.client.OkHttpRestfulClientFactory;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.client.interceptor.BearerTokenAuthInterceptor;
import ca.uhn.fhir.util.BundleUtil;

import com.oncolens.fhirengine.model.AuditType;
import com.oncolens.fhirengine.util.FhirConfig;
import com.oncolens.fhirengine.util.FhirUtilBase;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.instance.model.api.IBaseBundle;
import org.hl7.fhir.instance.model.api.IBaseResource;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

@Slf4j
public class FhirBridgeDSTU2 implements FhirBridge {
	private final FhirContext fhirContext = FhirContext.forDstu2();
	private FhirConfig fhirConfig;
	private String authToken;
	private IGenericClient fhirClient = null;

	public IGenericClient getFhirClient() {
		return fhirClient;
	}

	public String getAuthToken() {
		return authToken;
	}

	public void initialize(FhirConfig fc) {
		FhirUtilBase.setOutputPath(fc);
		this.fhirConfig = fc;
		String serverBase = fhirConfig.getFhirBaseURL();
		fhirContext.setRestfulClientFactory(new OkHttpRestfulClientFactory(fhirContext));
		fhirClient = fhirContext.newRestfulGenericClient(serverBase);
		log.info("Going to reach out to the Conformance End Point ...");
		processConformance();
		if ((this.fhirConfig.getTokenURL() == null)) {    //|| (this.fhirConfig.getAuthURL() == null)
			log.error("Failed to get authorization URLs. Cannot proceed");
			FhirProcessor.auditEventList.add(FhirUtilBase.processAuditEvent(fc, AuditType.EXCEPTION_OCCURRED,"getTokenURL",FhirProcessor.PROCESSUUID, fc.getCohortListFile(), "Unable to find token URL using fhirbase :" + fc.getFhirBaseURL() + " token key :" + fc.getFhirTokenUrlKey()));
			throw  (new RuntimeException("Failed to get authorization URLs. Cannot proceed"));
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
			//token = ClientCredentials.getToken(this.fhirConfig);
		} catch (Exception e) {
			FhirProcessor.auditEventList.add(FhirUtilBase.processAuditEvent(fhirConfig, AuditType.EXCEPTION_OCCURRED,"getAccessToken",FhirProcessor.PROCESSUUID, fhirConfig.getCohortListFile(), "Unable to get auth token :" + fhirConfig.getFhirBaseURL() + " token url :" + fhirConfig.getTokenURL() + " Exception - " + FhirUtilBase.getExceptionAsString(e)));
			log.error("Exception occurred while trying to get auth token", e);
			throw new RuntimeException(e);
		}
		log.debug("Token received successfully");
		return token;
	}

	@Override
	public List<String> createPatientExtracts(IBaseResource patient, String identifier, List<String> extracts,
											  List<IBaseResource> resourcesAll) {
		List<String> resourcesJsonAll = new ArrayList<>();
		List<IBaseResource> currentResources = new ArrayList<>();
		String patientid = extractPatientIdFromPatient(patient);
		currentResources.add(patient);
		log.info("Getting FHIR resources and Creating extracts...");

		// Get Observations
		if (extracts.contains("observation")) {
			List<IBaseResource> observationResources = getObservationsForPatientId(patientid);
			currentResources.addAll(observationResources);
			extracts.remove("observation");
		}
		// Get Conditions
		if (extracts.contains("condition")) {
			List<IBaseResource> conditionResources = getConditionsForPatientId(patientid);
			currentResources.addAll(conditionResources);
			extracts.remove("condition");
		}
		// Get Encounters
		if (extracts.contains("encounter")) {
			List<IBaseResource> encounterResources = getEncountersForPatientId(patientid);
			currentResources.addAll(encounterResources);
			extracts.remove("encounter");
		}
		// Get MedicationStatements
		if (extracts.contains("medicationstatement")) {
			List<IBaseResource> medicationResources = getMedicationStatementsForPatientId(patientid);
			currentResources.addAll(medicationResources);
			extracts.remove("medicationstatement");
		}

		// Get Procedures
		if (extracts.contains("procedure")) {
			List<IBaseResource> procedureResources = getProceduresForPatientId(patientid);
			currentResources.addAll(procedureResources);
			extracts.remove("procedure");
		}

		if (extracts.contains("document")) {
			getDocumentsForPatientId(patientid, identifier);
			extracts.remove("document");
		}

		if (extracts.contains("operationccd")) {
			byte[] ccd = getCCDUsingOperationForPatientId(patientid, identifier);
			if (ccd != null) {
				//try {
				//	ByteArrayInputStream bis = new ByteArrayInputStream(ccd);
					//S3Access.getInstance(fhirConfig).upload(bis, fhirConfig, identifier,
					//		fhirConfig.getCcdOutputFilePath(), null, "application/xml");
				//} catch (IOException e1) {
				//	FhirProcessor.auditEventList.add(FhirUtilBase.processAuditEvent(fhirConfig, AuditType.VALIDATION_FAILED,"Patient/" + patient.getIdElement().getIdPart(),FhirProcessor.PROCESSUUID, fhirConfig.getCohortListFile(), "Exception occurred during operationccd and hence skipping, for " + identifier + " due to " + FhirUtilBase.getExceptionAsString(e1)));
				//}
			}
			extracts.remove("operationccd");
		}
		// Process all the current resources to Json
		for (IBaseResource currentResource : currentResources) {
			String jsonStr = FhirUtilBase.getJsonForResource(fhirContext, currentResource);
			resourcesJsonAll.add(jsonStr);
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

	private List<String> createPatientCohortUsingIdentifier(List<String> cohortSelectionList, List<IBaseResource> patients) {
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
		Conformance cnf = fhirClient.capabilities().ofType(Conformance.class).execute();
		if (cnf.getRest().get(0).getSecurity().getUndeclaredExtensions().get(0).getUrlAsString()
				.equalsIgnoreCase(fhirConfig.getFhirAuthNameSpace())) {
			log.info("Found authorization details in conformance, going to look for auth and token urls");
			List<ExtensionDt> extensions = cnf.getRest().get(0).getSecurity().getUndeclaredExtensions().get(0)
					.getExtension();
			for (ExtensionDt e : extensions) {
				if (e.getUrlAsString().contentEquals(fhirConfig.getFhirAuthUrlKey())) {
					log.info("Found authorization URL details in conformance");
					UriDt ud = (UriDt) e.getValue();
					fhirConfig.setAuthURL(ud.getValueAsString());
				}
				if (e.getUrlAsString().contentEquals(fhirConfig.getFhirTokenUrlKey())) {
					log.info("Found token URL details in conformance");
					UriDt ud = (UriDt) e.getValue();
					fhirConfig.setTokenURL(ud.getValueAsString());
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
		/*
		 * There should be ONLY one record and hence no need to look for subsequent
		 * pages
		 */
		return patientResources;
	}

	public List<IBaseResource> getObservationsForPatientId(String patientId) {
		Bundle bundle = fhirClient.search()
				.forResource(Observation.class)
				.where(Observation.PATIENT.hasId(patientId))
				.returnBundle(Bundle.class).execute();
		List<IBaseResource> observations = new ArrayList<>(BundleUtil.toListOfResources(fhirContext, bundle));

		// Load the subsequent pages
		while (bundle.getLink(IBaseBundle.LINK_NEXT) != null) {
			bundle = fhirClient.loadPage().next(bundle).execute();
			observations.addAll(BundleUtil.toListOfResources(fhirContext, bundle));
		}
		FhirUtilBase.ensureResourceType(observations, Observation.class);
		log.debug("{} Observations fetched!", observations.size());
		return observations;
	}

	public List<IBaseResource> getConditionsForPatientId(String patientId) {
		Bundle bundle = fhirClient.search()
				.forResource(Condition.class)
				.where(Condition.PATIENT.hasId(patientId))
				.returnBundle(Bundle.class).execute();
		List<IBaseResource> conditions = new ArrayList<>(BundleUtil.toListOfResources(fhirContext, bundle));
		// Load the subsequent pages
		while (bundle.getLink(IBaseBundle.LINK_NEXT) != null) {
			bundle = fhirClient.loadPage().next(bundle).execute();
			conditions.addAll(BundleUtil.toListOfResources(fhirContext, bundle));
		}
		FhirUtilBase.ensureResourceType(conditions, Condition.class);
		log.debug("{} Conditions fetched!", conditions.size());
		return conditions;
	}

	public List<IBaseResource> getEncountersForPatientId(String patientId) {
		Bundle bundle = fhirClient.search()
				.forResource(Encounter.class)
				.where(Encounter.PATIENT.hasId(patientId))
				.returnBundle(Bundle.class).execute();
		List<IBaseResource> encounters = new ArrayList<>(BundleUtil.toListOfResources(fhirContext, bundle));
		// Load the subsequent pages
		while (bundle.getLink(IBaseBundle.LINK_NEXT) != null) {
			bundle = fhirClient.loadPage().next(bundle).execute();
			encounters.addAll(BundleUtil.toListOfResources(fhirContext, bundle));
		}
		FhirUtilBase.ensureResourceType(encounters, Encounter.class);
		log.debug("{} Encounters fetched!", encounters.size());
		return encounters;
	}

	public List<IBaseResource> getMedicationStatementsForPatientId(String patientId) {
		Bundle bundle = fhirClient.search()
				.forResource(MedicationStatement.class)
				.where(MedicationStatement.PATIENT.hasId(patientId))
				.returnBundle(Bundle.class).execute();
		List<IBaseResource> medications = new ArrayList<>(BundleUtil.toListOfResources(fhirContext, bundle));
		// Load the subsequent pages
		while (bundle.getLink(IBaseBundle.LINK_NEXT) != null) {
			bundle = fhirClient.loadPage().next(bundle).execute();
			medications.addAll(BundleUtil.toListOfResources(fhirContext, bundle));
		}
		FhirUtilBase.ensureResourceType(medications, MedicationStatement.class);
		log.debug("{} Medications fetched!", medications.size());
		return medications;
	}

	public List<IBaseResource> getProceduresForPatientId(String patientId) {
		Bundle bundle = fhirClient.search().forResource(Procedure.class)
				.where(Procedure.PATIENT.hasId(patientId))
				.returnBundle(Bundle.class).execute();
		List<IBaseResource> procedures = new ArrayList<>(BundleUtil.toListOfResources(fhirContext, bundle));
		// Load the subsequent pages
		while (bundle.getLink(IBaseBundle.LINK_NEXT) != null) {
			bundle = fhirClient.loadPage().next(bundle).execute();
			procedures.addAll(BundleUtil.toListOfResources(fhirContext, bundle));
		}
		FhirUtilBase.ensureResourceType(procedures, Procedure.class);
		log.debug("{} Procedures fetched!", procedures.size());
		return procedures;
	}

	public void getDocumentsForPatientId(String patientId, String identifier) {
		String searchURL = "DocumentReference?patient=" + patientId;
		List<IBaseResource> documentReferences = getResourcesByURL(searchURL, true, DocumentReference.class);
		log.info(" {} documentReferences fetched!", documentReferences.size());

		for (IBaseResource documentReference : documentReferences) {
			DocumentReference res = (DocumentReference) documentReference;
			List<Content> contents = res.getContent();
			//It is possible to have the contents in different formats, We should look for application/zip content type
			for (Content c : contents) {
				String contentType = c.getAttachment().getContentType();
				String contentURL = c.getAttachment().getUrl();
				if (contentType != null && contentURL != null) {
					log.debug("Content Type is " + contentType);
					log.debug("Content URL is " + contentURL);
					String docURL;
					if (FhirUtilBase.isRelativeURL(contentURL)) {
						docURL = getFhirConfig().getFhirBaseURL() + "/" + c.getAttachment().getUrl();
					} else {
						docURL = contentURL;
					}

					if (contentType.equalsIgnoreCase(fhirConfig.getDocumentContentType())) {
						getCCDADocument(docURL, res.getId().getIdPart(), identifier);
					} else {
						log.debug("content type did not match. Found {} looking for {}", contentType,
								fhirConfig.getDocumentContentType());
					}
				} else
					log.debug("Content type and URL were null");
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
					.withParameter(Parameters.class, "patient", new StringDt(patientid)).useHttpGet().execute();
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

		Bundle bundle = fhirClient.search()
				.byUrl(searchURL).returnBundle(Bundle.class)
				.execute();
		List<IBaseResource> resources = new ArrayList<>(BundleUtil.toListOfResources(fhirContext, bundle));
		// Load the subsequent pages
		while (bundle.getLink(IBaseBundle.LINK_NEXT) != null) {
			bundle = fhirClient.loadPage().next(bundle).execute();
			resources.addAll(BundleUtil.toListOfResources(fhirContext, bundle));
		}
		if (checkResourceType && (fhirResourceClass != null)) {
			FhirUtilBase.ensureResourceType(resources, fhirResourceClass);
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

	public void getCCDADocument(String documentReferenceURL, String documentID, String identifier) {
		try {
			Binary b = fhirClient.read().resource(Binary.class)
					.withUrl(documentReferenceURL)
					.execute();
			byte[] decodedBytes = Base64.getDecoder().decode(b.getContentAsBase64());
			ByteArrayInputStream bis = new ByteArrayInputStream(decodedBytes);
		//	S3Access.getInstance(fhirConfig).upload(bis, fhirConfig, identifier + "-" + documentID,
		//			fhirConfig.getCcdOutputFilePath(), null, fhirConfig.getDocumentContentType());
		} catch (Exception e) {
			FhirProcessor.auditEventList.add(FhirUtilBase.processAuditEvent(fhirConfig, AuditType.VALIDATION_FAILED,"Binary/" + documentID,FhirProcessor.PROCESSUUID, fhirConfig.getCohortListFile(), "Exception occurred getting CCD document for patient " + identifier + " due to " + FhirUtilBase.getExceptionAsString(e)));
		}
	}
	//This method assumes that the document is a FHIR Binary Resource. If not please override this 
	//method in the EHR specific sub class
	//Ex: https://apporchard.epic.com/interconnect-aocurprd-oauth/api/FHIR/DSTU2/Binary/TTrIy7p9Oa1P93a0kA.jp.zFCbm.IQAeagjWfV0Ml78iLFTwyWsWRUnvsM8MfBgziln3b6sBjTp0YaT7bqKKdugB"

	public List<IBaseResource> getResourcesByURL(String searchURL) {
		return getResourcesByURL(searchURL, false, null);
	}

}
