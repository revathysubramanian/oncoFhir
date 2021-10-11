package com.oncolens.fhirengine.util;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicSessionCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.secretsmanager.AWSSecretsManagerClient;
import com.amazonaws.services.secretsmanager.AWSSecretsManagerClientBuilder;
import com.amazonaws.services.securitytoken.AWSSecurityTokenService;
import com.amazonaws.services.securitytoken.AWSSecurityTokenServiceClientBuilder;
import software.amazon.awssdk.auth.credentials.*;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;
import software.amazon.awssdk.services.secretsmanager.model.SecretsManagerException;

public class AWSSecretsManager {



        public static void main(String[] args) {

            final String USAGE = "\n" +
                    "Usage:\n" +
                    "    GetSecretValue  <secretName> \n\n" +
                    "Where:\n" +
                    "    secretName - the name of the secret (for example, tutorials/MyFirstSecret). \n";
/*
            if (args.length != 1) {
               System.out.println(USAGE);
               System.exit(1);
            }
            String secretName = args[0];*/
            getSecretValue("fhir/dev/secrets");
        }

        public static String getSecretValue(String secretName) {

            //AWSSecurityTokenService sts_client = new AWSSecurityTokenServiceClientBuilder().standard().withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration("sts-endpoint.amazonaws.com", "signing-region")).build()
            /*BasicSessionCredentials awsCredentials = new BasicSessionCredentials(
                    "ASIAT3C4HJYG3JHDANNX",
                    "2uR3FzDUj0JvPxxfFzhmicGS3EsfI6E1FKS6b2ko",
                    "IQoJb3JpZ2luX2VjEOT//////////wEaCXVzLWVhc3QtMiJIMEYCIQCI/ZA7/BnwqQuWMC/Yy20G90zKCKUURFZf/bubQhr7iQIhAISR3GqT2CS/x3y+RQTrNfiSTVar9YPlOPkLFuPVFVCxKp4CCE0QABoMMjY0MzMzOTA1NDIxIgxqNFsJrpYbvswxrZEq+wHA2YfdLpy+7YtCPEFY8D5DKotrKtdIqdyEl4LxaSarUpzJ7xoijTfbKcMgmlTwBaX/FlNjZpJV6ITchamlRSAH/9qSQKjlxggU6iN7kPDNjjRPtFyXfnUV13IsO9KFWBmfRq7yjYditnR8PUKqLvBINA7UfGLTIyIAL04JBVE7Mp/BshxwmxgRU3pZTPY8R9Z0GWn9y1IWcdsDGGk3rITDlbCk6+tgH16oHiXU1O+78IDDx/FlgYdu6FlCsMFhGYnyYI/z3mlUW/YX3QIV09US88mgaOS2tF3XFCqDb7fEOu51yZc0EEUhFIM3PYZxlxLM3CtWbU84WKW9GzDl2LiKBjqcAeUngeZsMBQl9fGPZU0ChGclD/1iTiFjmFSkHTw29QabLnzJpzdIdacE92u3cWthmX6dJVK0iqQxFih6dVzbWxg6ZGi9nXVYrZpsaZ5k+XV7A8ss9k/QnGl/Pfr9LAPeu0qn2OIYe3sIne0TybkfNFFwilh/RQB8SN/i5IIrfMjK9YeoQBvpHfNI2bVZ0kuWtWZBkavVwT24G+ysYw=="
                    );*/
            /*
            System.out.println("ACCESS, SECRET, TOKEN " + System.getenv("AWS_TEMP_ACCESS_KEY") + " : " + System.getenv("AWS_TEMP_SECRET_KEY") + " : " + System.getenv("AWS_TEMP_ACCESS_TOKEN"));
            AwsSessionCredentials creds = AwsSessionCredentials.create(System.getenv("AWS_TEMP_ACCESS_KEY"),
                    System.getenv("AWS_TEMP_SECRET_KEY"), System.getenv("AWS_TEMP_ACCESS_TOKEN"));*/

            //com.amazonaws.auth.AWSCredentialsProvider credP = new AWSStaticCredentialsProvider(awsCredentials);
            //SecretsManagerClient secretsClient = SecretsManagerClient.builder().credentialsProvider(StaticCredentialsProvider.create(creds)).build();
            SecretsManagerClient secretsClient = SecretsManagerClient.builder().credentialsProvider(S3Access.getCredentialProvider()).build();

                   // .region(region)
                   // .credentialsProvider(ProfileCredentialsProvider.builder().profileName("DataDevelopmentRole").build())
                    //.build();
            //SecretsManagerClient secretsClient = SecretsManagerClient.builder().credentialsProvider((credP)).build();
           // AWSSecretsManagerClient clientBuilder = AWSSecretsManagerClientBuilder
             //       .standard()
               //     .withCredentials(credP).build();
            String secret = null;
            try {
                GetSecretValueRequest valueRequest = GetSecretValueRequest.builder()
                        .secretId(secretName)
                        .build();

                GetSecretValueResponse valueResponse = secretsClient.getSecretValue(valueRequest);
                secret = valueResponse.secretString();
                //TODO check to make sure secret is not null
                secretsClient.close();

            } catch (SecretsManagerException e) {
                System.err.println(e.awsErrorDetails().errorMessage());
                System.exit(1);
            }
            return secret;
        }
    }








