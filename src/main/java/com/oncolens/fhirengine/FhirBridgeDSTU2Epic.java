package com.oncolens.fhirengine;

import ca.uhn.fhir.model.dstu2.resource.Observation;
import com.oncolens.fhirengine.model.AuditType;
import com.oncolens.fhirengine.util.ClientCredentialsEpic;
import com.oncolens.fhirengine.util.FhirUtilBase;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.instance.model.api.IBaseResource;
import java.util.List;
/*
 * This class is meant for handling any epic specific DSTU2 functionality
 *
 */

@Slf4j
public class FhirBridgeDSTU2Epic extends FhirBridgeDSTU2 {
	@Override
	public String getAuthorizationToken() {
		String token ;
		try {
			token = ClientCredentialsEpic.getToken(getFhirConfig());
		} catch (Exception e) {
			FhirProcessor.auditEventList.add(FhirUtilBase.processAuditEvent(getFhirConfig(), AuditType.EXCEPTION_OCCURRED,"getAccessToken",FhirProcessor.PROCESSUUID, getFhirConfig().getCohortListFile(), "Unable to get auth token :" + getFhirConfig().getFhirBaseURL() + " token url :" + getFhirConfig().getTokenURL() + " Exception - " + FhirUtilBase.getExceptionAsString(e)));
			log.error("Exception while trying to get the auth token", e);
			throw new RuntimeException(e);
		}
		log.debug("Token received successfully");
		return token;
	}

	@Override
	public List<IBaseResource> getObservationsForPatientId(String patientId) {
		String searchURL = "Observation?patient=" + patientId + "&category=laboratory";
		List<IBaseResource> observations = getResourcesByURL(searchURL, true, Observation.class);
		log.info ("{} observations (labs) fetched!", observations.size());
		searchURL = "Observation?patient=" + patientId + "&category=social-history"; //"&code=72166-2";
		List<IBaseResource> observationsSmoking = getResourcesByURL(searchURL, true, Observation.class);
		log.info("{} observations (Smoking) fetched!", observationsSmoking.size());
		if (observationsSmoking.size() > 0) {
			observations.addAll(observationsSmoking);
		}
		searchURL = "Observation?patient=" + patientId + "&category=vital-signs";  //"&code=8310-5";
		List<IBaseResource> observationsVitals = getResourcesByURL(searchURL, true, Observation.class);
		log.info("{} observations (vitals) fetched!", observationsVitals.size());
		if (observationsVitals.size() > 0) {
			observations.addAll(observationsVitals);
		}
		return observations;

	}
}
