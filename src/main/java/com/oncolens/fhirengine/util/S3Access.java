package com.oncolens.fhirengine.util;

import com.amazonaws.auth.AWSCredentialsProvider;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;


import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3. AmazonS3ClientBuilder;

import com.amazonaws.services.s3.model.AmazonS3Exception;
import com.amazonaws.services.s3.model.Bucket;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import software.amazon.awssdk.auth.credentials.*;

@Slf4j
public class S3Access {

    private static S3Access s3Access;

    private final S3Client s3Client;

    private S3Access(FhirConfig fc) {

        s3Client = S3Client.builder().credentialsProvider(getCredentialProvider()).build();
    }

    public static AwsCredentialsProvider getCredentialProvider() {
        if (System.getenv("AWS_WEB_IDENTITY_TOKEN_FILE") != null) { //Container should inject this
            log.info("Using IamAwsProvider");
            //return new IamAwsProvider(null,null);
            return null; // TODO once docker is implemented
        }
        else {
            log.info("Using Environment Variables");
            return EnvironmentVariableCredentialsProvider.create();
        }
        /*
        else {
            log.info("Using AwsEnvironmentProvider");

            AwsSessionCredentials creds = AwsSessionCredentials.create(System.getenv("AWS_TEMP_ACCESS_KEY"),
                    System.getenv("AWS_TEMP_SECRET_KEY"), System.getenv("AWS_TEMP_ACCESS_TOKEN"));
            return StaticCredentialsProvider.create(creds);
        }*/
    }

    public static S3Access getInstance(FhirConfig fc) {
        if (s3Access == null) {
            s3Access = new S3Access(fc);
        }
        return s3Access;
    }

    public void upload(byte[] bytes, FhirConfig fc, String docId, String filePath, String fileSuffix,
                       String contentType) throws IOException {
        final String bucket = "oncofhir-" + fc.getEnvironment() + "-" + fc.getCustomer();
        String file = filePath + '-' + docId;
        if (fileSuffix != null) {
            file = file + fileSuffix;
        }
      //  try {
            long start = System.currentTimeMillis();

            PutObjectRequest req = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(file)
                    .build();
            final PutObjectResponse putObjectResponse = s3Client.putObject(req, RequestBody.fromBytes(bytes));
            log.debug("Putting object complete. [id={}, bucket={}, file={}, runtime={}ms]",
                    docId, bucket, file, System.currentTimeMillis() - start);

   //     } catch (MinioException | GeneralSecurityException ex) {
     //       throw new IOException(ex);
   //     }
    }

}