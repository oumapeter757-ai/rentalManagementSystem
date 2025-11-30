package com.peterscode.rentalmanagementsystem.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "mpesa")
public class MpesaConfig {

    private String consumerKey;
    private String consumerSecret;
    private String passkey;
    private String shortcode;
    private String environment;
    private String callbackUrl;
    private String stkPushUrl;
    private String authUrl;

    public boolean isSandbox() {
        return "sandbox".equalsIgnoreCase(environment);
    }
}
