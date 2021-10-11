package com.oncolens.fhirengine.util;


import ca.uhn.fhir.context.FhirContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.oncolens.fhirengine.FhirProcessor;
import com.oncolens.fhirengine.model.AuditEvent;
import com.oncolens.fhirengine.model.AuditType;
import lombok.Cleanup;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.OperationOutcome;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.Security;
import java.security.Signature;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
public class FhirUtilBase {

    public static final ObjectMapper JSONMAPPER = new ObjectMapper();

    public static List<String> getFileData(String filename) {
        List<String> fileData = new ArrayList<>();
        try {
            File myObj = new File(filename);
            Scanner myReader = new Scanner(myObj);
            while (myReader.hasNextLine()) {
                String data = myReader.nextLine();
                fileData.add(data);
            }
            myReader.close();
        } catch (FileNotFoundException e) {
            log.error("An error occurred- File not found {} ", filename);
            e.printStackTrace();
        }
        return fileData;
    }

    public static String getJsonForResource(FhirContext ctx, IBaseResource r) {
        return ctx.newJsonParser().encodeResourceToString(r);
    }

    public static Bundle getResourceForJson(FhirContext ctx, String bundleString) {
        return ctx.newJsonParser().parseResource(Bundle.class, bundleString );
    }

    public static String createJWT(String issuer, String aud, String subject, String idJti, long ttlMillis,
                                   String secret) throws Exception {
        Security.addProvider(new BouncyCastleProvider());
        PEMParser pemParser;
        KeyPair kp = null;
        pemParser = new PEMParser(new StringReader("-----BEGIN RSA PRIVATE KEY-----" + "\n" + secret + "\n" + "-----END RSA PRIVATE KEY-----"));
        JcaPEMKeyConverter converter = new JcaPEMKeyConverter().setProvider("BC");
        Object object = pemParser.readObject();
        kp = converter.getKeyPair((PEMKeyPair) object);
        String jwtToken = null;
        assert kp != null;
        PrivateKey privateKey = kp.getPrivate();

        ObjectMapper mapper = new ObjectMapper();
        ObjectNode headerObject = mapper.createObjectNode();
        headerObject.put("alg", "RS384");
        headerObject.put("typ", "JWT");
        byte[] header = headerObject.toString().getBytes();

        ObjectNode payloadObject = mapper.createObjectNode();
        payloadObject.put("iss", issuer);
        payloadObject.put("sub", subject);
        payloadObject.put("aud", aud);
        payloadObject.put("jti", idJti);
        payloadObject.put("exp", new Date(System.currentTimeMillis() + ttlMillis).getTime() / 1000);
        byte[] payload = payloadObject.toString().getBytes();
        String signature = rsaSha384(encode(header) + "." + encode(payload), privateKey);
        jwtToken = encode(header) + "." + encode(payload) + "." + signature;

        return jwtToken;
    }

    public static String rsaSha384(String plainText, PrivateKey privateKey) throws Exception {
        Signature privateSignature = Signature.getInstance("SHA384withRSA");
        privateSignature.initSign(privateKey);
        privateSignature.update(plainText.getBytes(StandardCharsets.UTF_8));
        byte[] signature = privateSignature.sign();
        return encode(signature);
    }

    private static String encode(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static boolean isRelativeURL(String url) {
        return !url.contains("http");
    }

    public static void ensureResourceTypeNew(List<IBaseResource> resources, Class<?> fhirResourceClass, Class<?> operationOutcomeClass) {
        log.info("Inside ensureResourceType begin -size {} ", resources.size());
        Iterator<IBaseResource> iterator = resources.iterator();
        while (iterator.hasNext()) {
            IBaseResource res = iterator.next();
            if (!fhirResourceClass.isInstance(res)) {
                log.info("found unexpected object type. Expected {} and removed {}", fhirResourceClass.getName(),
                        res.getClass().getName());
                if (operationOutcomeClass.isInstance(res)) {
                    log.info("Operation Outcome occurred - {}", ((OperationOutcome)(res)).getIssue().get(0).getDetails().getText());
                }
                iterator.remove();
            }
        }
        log.info("Inside ensureResourceType end -size {} ", resources.size());
    }

    public static void ensureResourceType(List<IBaseResource> resources, Class<?> fhirResourceClass) {
        log.info("Inside ensureResourceType begin -size {} ", resources.size());
        Iterator<IBaseResource> iterator = resources.iterator();
        while (iterator.hasNext()) {
            IBaseResource res = iterator.next();
            if (!fhirResourceClass.isInstance(res)) {
                log.info("found unexpected object type. Expected {} and removed {}", fhirResourceClass.getName(),
                        res.getClass().getName());
                iterator.remove();
            }
        }
        log.info("Inside ensureResourceType end -size {} ", resources.size());
    }

    public static void setOutputPath(FhirConfig fc) {
        String today = (DateTimeFormatter.ofPattern("yyyyMMdd").format(LocalDate.now()));
        String todaytime = (DateTimeFormatter.ofPattern("yyyyMMddHHmmss").format(LocalDateTime.now()));
        fc.setDataOutputFilePath(fc.getDataOutputFilePath().replaceAll("siteId", fc.getSiteId())
                .replaceAll("fhirSourceSystem", fc.getFhirSourceSystem())
                .replaceAll("fhirVersion", fc.getFhirVersion())
                .replaceAll("date", today));
        fc.setCcdOutputFilePath(fc.getCcdOutputFilePath().replaceAll("siteId", fc.getSiteId())
                .replaceAll("fhirSourceSystem", fc.getFhirSourceSystem())
                .replaceAll("fhirVersion", fc.getFhirVersion())
                .replaceAll("date", today));
        fc.setAuditOutputFilePath(fc.getAuditOutputFilePath().replaceAll("siteId", fc.getSiteId())
                .replaceAll("fhirSourceSystem", fc.getFhirSourceSystem())
                .replaceAll("fhirVersion", fc.getFhirVersion())
                .replaceAll("datetime", todaytime));
    }
    public static AuditEvent processAuditEvent(FhirConfig fc, AuditType type, String subject, String processid, String originFile , String detailedMessage){
        OffsetDateTime now = OffsetDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ISO_INSTANT;
        AuditEvent ae = AuditEvent.builder()
                .id(UUID.randomUUID().toString())
                .originfile(originFile)
                .message(type.getMessage())
                .userfriendlymessage(detailedMessage)
                .processuuid(processid)
                .status(type.getStatus())
                .customer((fc ==null)?"":fc.getCustomer())
                .siteid((fc ==null)?"":fc.getSiteId())
                .workflow((fc ==null)?"":fc.getFhirBridgeType().getDescription())
                .subject(subject)
                .scope(type.getScope())
                .source("ehr-connector/fhirconnector-client")
                .time(formatter.format(now))
                .build();
        return ae;
    }
    public static String getExceptionAsString(Exception e) {
        StringWriter sw = new StringWriter();
        e.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }

    public static void writeBytesToFile(String fileName, String identifier, byte[] bytes) {
        try {
            fileName = fileName + "-" + identifier;
            File file = new File(fileName);
            file.createNewFile();
            @Cleanup FileOutputStream fileOut = null;
            fileOut = new FileOutputStream(fileName);
            fileOut.write(bytes);
        } catch (java.io.IOException e) {
            log.error("An error occurred- File not found {}", fileName);
            e.printStackTrace();
        }
    }
}
