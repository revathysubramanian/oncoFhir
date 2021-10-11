package com.oncolens.fhirengine;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.oncolens.fhirengine.model.AuditEvent;
import com.oncolens.fhirengine.model.AuditType;
import com.oncolens.fhirengine.util.AWSSecretsManager;
import com.oncolens.fhirengine.util.FhirConfig;
import com.oncolens.fhirengine.util.FhirUtilBase;
import com.oncolens.fhirengine.util.S3Access;
import lombok.Cleanup;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.instance.model.api.IBaseResource;
import ca.uhn.fhir.rest.client.exceptions.FhirClientConnectionException;
import ca.uhn.fhir.rest.server.exceptions.AuthenticationException;
import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
public class FhirProcessor {
    private static final ObjectMapper MAPPER = new ObjectMapper(new YAMLFactory());
    public static final String PROCESSUUID = UUID.randomUUID().toString();
    public  static List<AuditEvent> auditEventList = new ArrayList<>();

    public static void main(String[] args) throws IOException {
        FhirConfig fc = null;
        log.info("Starting Fhir Processor");
        auditEventList.add(FhirUtilBase.processAuditEvent(null, AuditType.STARTED,"main process",FhirProcessor.PROCESSUUID, "", "Starting FHIR Processor"));
        try {
            if (args.length != 0) {
                log.error("Expected no arguments, found " + args.length + "They will be ignored");
                auditEventList.add(FhirUtilBase.processAuditEvent(null, AuditType.ERRORED,"main process",FhirProcessor.PROCESSUUID, "", "Expected two arguments: <config file>  <cohort file>, found " + args.length));
            }

            String cohortFile = System.getenv("COHORT_FILE");
            log.info("Inputs [configFile={}, cohortFile={}, path={}]", System.getenv("CONFIG_FILE"), System.getenv("SECRETS_FILE"), System.getenv("PATH"));
            File configFile = new File(System.getenv("CONFIG_FILE"));
            File secretFile = new File(System.getenv("SECRETS_FILE"));
            log.info("Inputs. [configPath={}, cohortFile={}]", configFile.getAbsolutePath(), cohortFile);
            fc = new FhirConfig();
            fc.setS3EndPoint(System.getenv("S3_ENDPOINT"));
            ObjectReader objectReader = MAPPER.readerForUpdating(fc);
            objectReader.readValue(configFile);
            ObjectMapper mapper = new ObjectMapper(new YAMLFactory());

            String secret = AWSSecretsManager.getSecretValue("fhir/dev/secrets");
            //TODO check to make sure secret is not a null
            Map readValue = mapper.readValue(secret, Map.class);


            //Map readValue = mapper.readValue(secretFile, Map.class);
            if (readValue.containsKey(fc.getCustomer())) {
                Map sites = (Map)(readValue.get(fc.getCustomer()));
                if (sites.containsKey(fc.getSiteId())) {
                    fc.setClientId((String)((Map)sites.get(fc.getSiteId())).get("clientId"));
                    fc.setClientSecret((String)((Map)sites.get(fc.getSiteId())).get("clientSecret"));
                }
            }
            if (fc.getClientSecret() == null || fc.getClientId() == null) {
                auditEventList.add(FhirUtilBase.processAuditEvent(fc, AuditType.ERRORED,cohortFile,FhirProcessor.PROCESSUUID, cohortFile, "Unable to find entry for site " + fc.getSiteId() + " under customer " + fc.getCustomer() + " in the secrets"));
            }
            else {
                fc.setCohortListFile(cohortFile);
                new FhirProcessor().run(fc);
                auditEventList.add(FhirUtilBase.processAuditEvent(fc, AuditType.COMPLETED, cohortFile, FhirProcessor.PROCESSUUID, cohortFile, "FhirProcessorClient completed successfully"));
                log.info("FhirProcessorClient completed successfully");
            }

        }
        catch(Exception e){
            //The ERRORED Audit Type is used for alerting. The  EXCEPTION AuditType event is created at the actual site of the exception
            auditEventList.add(FhirUtilBase.processAuditEvent(fc, AuditType.ERRORED,"main process",FhirProcessor.PROCESSUUID, "", "FhirProcessorClient completed with an error-" + FhirUtilBase.getExceptionAsString(e)));
            log.error("FhirProcessorClient completed with an error", e);
        }
        finally{

            ObjectMapper objectMapper = new ObjectMapper();
            @Cleanup ByteArrayOutputStream bos = new ByteArrayOutputStream();
            for (AuditEvent ae : auditEventList) {
                bos.write(objectMapper.writeValueAsBytes(ae));
                bos.write('\n');
            }
            FhirUtilBase.writeBytesToFile(fc.getAuditOutputFilePath(),"audit", bos.toByteArray());
            //ByteArrayInputStream bis = new ByteArrayInputStream(bos.toByteArray());
            S3Access.getInstance(fc).upload(bos.toByteArray(), fc, PROCESSUUID, fc.getAuditOutputFilePath(), null, "text" +
                    "/plain");
            //**S3Access.getInstance(fc).upload(bis, fc, PROCESSUUID, fc.getLogOutputFilePath(), null, "text" +
                //**    "/plain");
            log.info("Uploaded AuditEvents to S3");
        }
    }

    public void run(FhirConfig fc) throws IOException {
        try {
            log.info("Creating  [fhirBridge]");
            FhirBridge fhirBridge = fc.getFhirBridgeType().getImpl();
            fhirBridge.initialize(fc);
            log.info("Created [fhirBridge]");
            List<String> cohortSelectionList = FhirUtilBase.getFileData(fc.getCohortListFile());
            List<String> extracts = fc.getExtracts();
            if (extracts.size() == 0) {
                log.error("No extracts were requested in the config file");
                auditEventList.add(FhirUtilBase.processAuditEvent(fc, AuditType.EXCEPTION_OCCURRED,"CreateExtract",FhirProcessor.PROCESSUUID, fc.getCohortListFile(), "No extracts were requested in the config file" ));
                throw  (new RuntimeException("No extracts were requested in the config file"));
            }
            List<IBaseResource> resourcesAll = new ArrayList<>();
            List<IBaseResource> patientResources = new ArrayList<>();
            List<String> jsonResourcesAll = new ArrayList<>();
            log.info("Going to create patient cohort using input list containing {} patients", cohortSelectionList.size());
            List<String> patientsIdentifierList = fhirBridge.createPatientCohort(cohortSelectionList,
                    fc.getCohortSelectionType(),
                    patientResources);
            if (patientsIdentifierList == null) {
                log.error("Invalid cohort selection type, please check config file");
                auditEventList.add(FhirUtilBase.processAuditEvent(fc, AuditType.EXCEPTION_OCCURRED,"CreatePatientCohort",FhirProcessor.PROCESSUUID, fc.getCohortListFile(), "No cohorts could be determined" ));
                throw  (new RuntimeException("Invalid cohort selection type, please check config file"));
            }
            log.info("There are {} patients in the cohort", patientsIdentifierList.size());

            int patientIndex = -1;
            for (IBaseResource patient : patientResources) {
                patientIndex++;
                jsonResourcesAll.clear();
                String identifier = patientsIdentifierList.get(patientIndex).trim();
                List<String> extractsCopy = new ArrayList<>(extracts);
                try {
                    jsonResourcesAll.addAll(fhirBridge.createPatientExtracts(patient, identifier, extractsCopy,
                            resourcesAll));
                } catch (AuthenticationException | FhirClientConnectionException ea) {
                    log.info("####################################################################");
                    log.info("Token Expired or Connection timed out, reinitializing fhirBridge...");
                    log.info("####################################################################");
                    fhirBridge.initialize(fc);
                    //Re process the last patient since it never completed
                    extractsCopy = new ArrayList<>(extracts);
                    jsonResourcesAll.addAll(fhirBridge.createPatientExtracts(patient, identifier, extractsCopy,
                            resourcesAll));

                }
                log.debug("Size of jsonResourcesAll in fhirProcessor {}", jsonResourcesAll.size());
                log.debug("Size of resourcesAll in fhirProcessor {}", resourcesAll.size());
               // ArrayList<String> s3List = new ArrayList<>(jsonResourcesAll);
                @Cleanup ByteArrayOutputStream bos = new ByteArrayOutputStream(); //Combine all the json resources into a Stream

                for (String jsonLine : jsonResourcesAll) {
                    bos.write(jsonLine.getBytes());
                    bos.write('\n');
                }
               // ByteArrayInputStream bis = new ByteArrayInputStream(bos.toByteArray());
                log.debug ("size of byte stream before writing to file {}", bos.size());
                FhirUtilBase.writeBytesToFile(fc.getDataOutputFilePath(), fhirBridge.extractPatientIdFromPatient(patient), bos.toByteArray());

                S3Access.getInstance(fc).upload(bos.toByteArray(), fc, fhirBridge.extractPatientIdFromPatient(patient), fc.getDataOutputFilePath(), null, "text" +
                        "/plain");
                log.info("Uploaded json data file containing {} objects to S3", jsonResourcesAll.size());
                if (extractsCopy.size() > 0) {
                    log.info("Unsupported extracts {} ", extractsCopy);
                }
            }
        } catch (Exception ex) {
            log.error("Exception occurred while running FhirProcessor ", ex);
            //AUDIT here
            throw ex;
        }
    }
}







