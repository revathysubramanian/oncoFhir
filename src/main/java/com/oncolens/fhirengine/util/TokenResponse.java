package com.oncolens.fhirengine.util;
import lombok.Data;

@Data
public class TokenResponse {
    private String access_token;
    private String scope;
    private String token_type;
    private Long expires_in;
    private String refresh_token;
}
