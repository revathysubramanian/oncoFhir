package com.oncolens.fhirengine.util;

import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;

import javax.security.sasl.AuthenticationException;
import java.util.ArrayList;
import java.util.List;

@Slf4j
public class ClientCredentialsEpic {

    /**
     * Request a fresh access token using the given client ID, client secret, and token request URL, If an exception is
     * thrown, print the stack trace instead.
     */
    public static String getToken(FhirConfig fc) throws Exception {
        String idJti = Long.toString(System.currentTimeMillis());
        String jwtToken = FhirUtilBase.createJWT(fc.getClientId(), fc.getTokenURL(), fc.getClientId(), idJti, 250000,
                fc.getClientSecret());
        log.info("Created jwt token for requesting access token from EHR");
        HttpClient client = HttpClientBuilder.create().build();
        HttpPost tokenPost = new HttpPost(fc.getTokenURL());

        log.debug("Adding the Parameters in an ArrayList as NameValuePair ...");
        List<NameValuePair> tokenParams = new ArrayList<>();

        tokenPost.addHeader("Content-Type", "application/x-www-form-urlencoded");
        tokenParams.add(new BasicNameValuePair("grant_type", fc.getGrantType()));
        tokenParams.add(new BasicNameValuePair("client_assertion_type", "urn:ietf:params:oauth:client-assertion-type" +
                ":jwt-bearer"));
        tokenParams.add(new BasicNameValuePair("client_assertion", jwtToken));
        //tokenParams.add(new BasicNameValuePair("scope", fc.getScope()));

        String token;
        try {
            UrlEncodedFormEntity e = new UrlEncodedFormEntity(tokenParams);
            tokenPost.setEntity(e);
            log.debug("Executing the Token Post Method");
            HttpResponse responseJWT = client.execute(tokenPost);
            String json = EntityUtils.toString(responseJWT.getEntity());
            TokenResponse tResponse = FhirUtilBase.JSONMAPPER.readValue(json, TokenResponse.class);
            log.info("Token {}", tResponse.toString());
            token = tResponse.getAccess_token();
        } catch (Exception e) {
            log.error("Error getting token ", e);
            throw new AuthenticationException("Failed to obtain token from " + fc.getTokenURL());
        }
        return token;
    }

}
