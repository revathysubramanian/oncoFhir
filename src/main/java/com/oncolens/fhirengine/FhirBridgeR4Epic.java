package com.oncolens.fhirengine;

import com.oncolens.fhirengine.model.AuditType;
import com.oncolens.fhirengine.util.ClientCredentialsEpic;
import com.oncolens.fhirengine.util.FhirUtilBase;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.instance.model.api.IBaseBundle;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.*;
import ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException;
import ca.uhn.fhir.util.BundleUtil;
import java.util.ArrayList;
import java.util.List;

/*
 * This class is meant for handling any epic specific R4 functionality
 *
 */

@Slf4j
public class FhirBridgeR4Epic extends FhirBridgeR4 {
    @Override
    public String getAuthorizationToken() {
        String token;
        try {
            token = ClientCredentialsEpic.getToken(getFhirConfig());
        } catch (Exception e) {
            FhirProcessor.auditEventList.add(FhirUtilBase.processAuditEvent(getFhirConfig(), AuditType.EXCEPTION_OCCURRED,"getAccessToken",FhirProcessor.PROCESSUUID, getFhirConfig().getCohortListFile(), "Unable to get auth token :" + getFhirConfig().getFhirBaseURL() + " token url :" + getFhirConfig().getTokenURL() + " Exception - " + FhirUtilBase.getExceptionAsString(e)));
            log.error("Exception occurred when trying to get auth token, e");
            throw new RuntimeException(e);
        }
        log.info("Token received successfully");
        return token;
    }
    @Override
    public List<IBaseResource> getObservationsForPatientId(String patientId) {
        String searchURL = "Observation?patient=" + patientId + "&category=laboratory";
        List<IBaseResource> observations = getResourcesByURL(searchURL, true, Observation.class);
        log.info("{} observations (labs) fetched!", observations.size());
        searchURL = "Observation?patient=" + patientId + "&category=social-history"; //"&code=72166-2"; social-history covers smoking as well as obstetrics
        List<IBaseResource> observationsSmokingObstetrics = getResourcesByURL(searchURL, true, Observation.class);
        log.info("{} observations (Smoking & ObGyn) fetched!", observationsSmokingObstetrics.size());
        if (observationsSmokingObstetrics.size() > 0) {
            observations.addAll(observationsSmokingObstetrics);
        }
        searchURL = "Observation?patient=" + patientId + "&category=vital-signs";  //"&code=8310-5";
        List<IBaseResource> observationsVitals = getResourcesByURL(searchURL, true, Observation.class);
        log.info("{} observations (vitals) fetched!", observationsVitals.size());
        if (observationsVitals.size() > 0) {
            observations.addAll(observationsVitals);
        }
        searchURL = "Observation?patient=" + patientId + "&category=LDA";
        List<IBaseResource> observationsLDA = getResourcesByURL(searchURL, true, Observation.class);
        log.info("{} observations (LDA) fetched!", observationsLDA.size());
        if (observationsLDA.size() > 0) {
            observations.addAll(observationsLDA);
        }
        searchURL = "Observation?patient=" + patientId + "&category=smartdata";
        List<IBaseResource> observationsSmartData = getResourcesByURL(searchURL, true, Observation.class);
        log.info("{} observations (smartdata) fetched!", observationsSmartData.size());
        if (observationsSmartData.size() > 0) {
            observations.addAll(observationsSmartData);
        }
        searchURL = "Observation?patient=" + patientId + "&category=core-characteristics";
        List<IBaseResource> observationsCoreCharacteristics = getResourcesByURL(searchURL, true, Observation.class);
        log.info("{} observations (observationsCoreCharacteristics) fetched!", observationsCoreCharacteristics.size());
        if (observationsCoreCharacteristics.size() > 0) {
            observations.addAll(observationsCoreCharacteristics);
        }
        searchURL = "Observation?patient=" + patientId + "&category=functional-mental-status";
        List<IBaseResource> observationsActivitiesDailyLiving = getResourcesByURL(searchURL, true, Observation.class);
        log.info("{} observations (observationsActivitiesDailyLiving) fetched!", observationsActivitiesDailyLiving.size());
        if (observationsActivitiesDailyLiving.size() > 0) {
            observations.addAll(observationsActivitiesDailyLiving);
        }
        return observations;
    }


    //This resource requires a category for searching. Use 38717003 for longitudinal, 734163000 for encounter, 736271009 for outpatient, 736353004 for inpatient,
    // 738906000 for dental, 736378000 for oncology, 719091000000102 for questionnaires due, 959871000000107 for anticoagulation, or inpatient-pathway for inpatient pathway.

    public List<IBaseResource> getCarePlansForPatientId(String patientId) {
        List<IBaseResource> careplans = new ArrayList<>();
        try {
            Bundle bundle = getFhirClient().search()
                    .forResource(CarePlan.class)
                    .where(CarePlan.PATIENT.hasId(patientId))
                    .and(CarePlan.CATEGORY.exactly().code("38717003")) //longitudinal
                    .returnBundle(Bundle.class).execute();
            careplans.addAll(BundleUtil.toListOfResources(getFhirContext(), bundle));
            // Load the subsequent pages
            while (bundle.getLink(IBaseBundle.LINK_NEXT) != null) {
                bundle = getFhirClient().loadPage().next(bundle).execute();
                careplans.addAll(BundleUtil.toListOfResources(getFhirContext(), bundle));
            }
            FhirUtilBase.ensureResourceType(careplans, CarePlan.class);
        } catch (ResourceNotFoundException e) {
            // ca.uhn.fhir.rest.server.exceptions.

        }
        log.info("{} care plans (longitudinal) fetched!", careplans.size());
        try {
            Bundle bundle = getFhirClient().search()
                    .forResource(CarePlan.class)
                    .where(CarePlan.PATIENT.hasId(patientId))
                    .and(CarePlan.CATEGORY.exactly().code("734163000")) //encounter
                    .returnBundle(Bundle.class).execute();
            careplans.addAll(BundleUtil.toListOfResources(getFhirContext(), bundle));
            // Load the subsequent pages
            while (bundle.getLink(IBaseBundle.LINK_NEXT) != null) {
                bundle = getFhirClient().loadPage().next(bundle).execute();
                careplans.addAll(BundleUtil.toListOfResources(getFhirContext(), bundle));
            }
            FhirUtilBase.ensureResourceType(careplans, CarePlan.class);
        } catch (ResourceNotFoundException e) {
            // ca.uhn.fhir.rest.server.exceptions.

        }
        log.info("{} care plans (after adding encounter) !", careplans.size());

        try {
            Bundle bundle = getFhirClient().search()
                    .forResource(CarePlan.class)
                    .where(CarePlan.PATIENT.hasId(patientId))
                    .and(CarePlan.CATEGORY.exactly().code("736271009")) //out patient
                    .returnBundle(Bundle.class).execute();
            careplans.addAll(BundleUtil.toListOfResources(getFhirContext(), bundle));
            // Load the subsequent pages
            while (bundle.getLink(IBaseBundle.LINK_NEXT) != null) {
                bundle = getFhirClient().loadPage().next(bundle).execute();
                careplans.addAll(BundleUtil.toListOfResources(getFhirContext(), bundle));
            }
            FhirUtilBase.ensureResourceType(careplans, CarePlan.class);
        } catch (ResourceNotFoundException e) {
            // ca.uhn.fhir.rest.server.exceptions.

        }
        log.info("{} care plans (after adding outpatient) !", careplans.size());

        try {
            Bundle bundle = getFhirClient().search()
                    .forResource(CarePlan.class)
                    .where(CarePlan.PATIENT.hasId(patientId))
                    .and(CarePlan.CATEGORY.exactly().code("736353004")) //in patient
                    .returnBundle(Bundle.class).execute();
            careplans.addAll(BundleUtil.toListOfResources(getFhirContext(), bundle));
            // Load the subsequent pages
            while (bundle.getLink(IBaseBundle.LINK_NEXT) != null) {
                bundle = getFhirClient().loadPage().next(bundle).execute();
                careplans.addAll(BundleUtil.toListOfResources(getFhirContext(), bundle));
            }
            FhirUtilBase.ensureResourceType(careplans, CarePlan.class);
        } catch (ResourceNotFoundException e) {
            // ca.uhn.fhir.rest.server.exceptions.

        }
        log.info("{} care plans (after adding inpatient) !", careplans.size());

        try {
            Bundle bundle = getFhirClient().search()
                    .forResource(CarePlan.class)
                    .where(CarePlan.PATIENT.hasId(patientId))
                    .and(CarePlan.CATEGORY.exactly().code("736378000")) //oncology
                    .returnBundle(Bundle.class).execute();
            careplans.addAll(BundleUtil.toListOfResources(getFhirContext(), bundle));
            // Load the subsequent pages
            while (bundle.getLink(IBaseBundle.LINK_NEXT) != null) {
                bundle = getFhirClient().loadPage().next(bundle).execute();
                careplans.addAll(BundleUtil.toListOfResources(getFhirContext(), bundle));
            }
            FhirUtilBase.ensureResourceType(careplans, CarePlan.class);
        } catch (ResourceNotFoundException e) {
            // ca.uhn.fhir.rest.server.exceptions.

        }
        log.info("{} care plans (after adding oncology) !", careplans.size());


        return careplans;
    }


    @Override
    public List<IBaseResource> getConditionsForPatientId(String patientId) {
        String searchURL = "Condition?patient=" + patientId + "&category=health-concern";
        List<IBaseResource> conditions = getResourcesByURL(searchURL, true, Condition.class);
        log.info("{} conditions (health-concern) fetched!", conditions.size());
        searchURL = "Condition?patient=" + patientId + "&category=problem-list-item";
        List<IBaseResource> conditionsProblems = getResourcesByURL(searchURL, true, Condition.class);
        log.info("{} conditions (Problems) fetched!", conditionsProblems.size());
        if (conditionsProblems.size() > 0) {
            conditions.addAll(conditionsProblems);
        }
        searchURL = "Condition?patient=" + patientId + "&category=encounter-diagnosis";
        List<IBaseResource> conditionsEncounterDiagnosis = getResourcesByURL(searchURL, true, Condition.class);
        log.info("{} conditions (encounter diagnosis) fetched!", conditionsEncounterDiagnosis.size());
        if (conditionsEncounterDiagnosis.size() > 0) {
            conditions.addAll(conditionsEncounterDiagnosis);
        }
        searchURL = "Condition?patient=" + patientId + "&category=genomics";
        List<IBaseResource> conditionsGenomics = getResourcesByURL(searchURL, true, Condition.class);
        log.info(conditionsGenomics.size() + " conditions (genomics) fetched!");
        if (conditionsGenomics.size() > 0) {
            conditions.addAll(conditionsGenomics);
        }
        searchURL = "Condition?patient=" + patientId + "&category=problem-list-item";
        List<IBaseResource> conditionsProblemList = getResourcesByURL(searchURL, true, Condition.class);
        log.info(conditionsProblemList.size() + " conditions (problem-list) fetched!");
        if (conditionsProblemList.size() > 0) {
            conditions.addAll(conditionsProblemList);
        }
        return conditions;
    }













































    //@Override
    public List<IBaseResource> getAllergyIntolerancesForPatientId(String patientId) {
        List<IBaseResource> allergies = new ArrayList<>();
        try {
            Bundle bundle = getFhirClient().search()
                    .forResource(AllergyIntolerance.class)
                    .where(AllergyIntolerance.PATIENT.hasId(patientId))
                    .and(AllergyIntolerance.CLINICAL_STATUS.exactly().code("active"))
                    .returnBundle(Bundle.class).execute();
            allergies.addAll(BundleUtil.toListOfResources(getFhirContext(), bundle));
            // Load the subsequent pages
            while (bundle.getLink(IBaseBundle.LINK_NEXT) != null) {
                bundle = getFhirClient().loadPage().next(bundle).execute();
                allergies.addAll(BundleUtil.toListOfResources(getFhirContext(), bundle));
            }
            FhirUtilBase.ensureResourceType(allergies, AllergyIntolerance.class);
        } catch (ResourceNotFoundException e) {
            // ca.uhn.fhir.rest.server.exceptions.

        }
        log.info("{} allergies fetched!", allergies.size());
        return allergies;
    }


/*
	@Override
	public List<IBaseResource> getMedicationRequestsForPatientId(String patientId) {
		String searchURL = "MedicationRequest?patient=" + patientId + "&_include=MedicationRequest:requester:Practitioner";
		List<IBaseResource> medications = getResourcesByURL(searchURL, false, Condition.class);
		log.info("{} medications-- fetched!", medications.size());

		return medications;
	}*/

	@Override
	public List<IBaseResource> getGoalsForPatientId(String patientId) {
	    System.out.println("Patient is " + patientId);
		String searchURL = "Goal?patient=" + patientId ;
		List<IBaseResource> goals = getResourcesByURL(searchURL, true, Goal.class);
		log.info("{} Goals overridden- fetched!", goals.size());

		return goals;
	}



}
