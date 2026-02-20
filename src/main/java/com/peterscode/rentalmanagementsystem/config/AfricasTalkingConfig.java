package com.peterscode.rentalmanagementsystem.config;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Configuration
@ConfigurationProperties(prefix = "africastalking")
@Getter
@Setter
public class AfricasTalkingConfig {

    private String username;
    private String apiKey;
    private String senderId;

    private boolean configured = false;
    private HttpClient httpClient;

    private static final String SANDBOX_URL = "https://api.sandbox.africastalking.com/version1/messaging";
    private static final String LIVE_URL = "https://api.africastalking.com/version1/messaging";

    @PostConstruct
    public void init() {
        if (apiKey != null && !apiKey.isEmpty()) {
            try {
                javax.net.ssl.SSLContext sslContext = javax.net.ssl.SSLContext.getInstance("TLSv1.3");
                sslContext.init(null, null, null);
                httpClient = HttpClient.newBuilder()
                        .version(HttpClient.Version.HTTP_1_1)
                        .sslContext(sslContext)
                        .connectTimeout(java.time.Duration.ofSeconds(15))
                        .build();
                configured = true;
                log.info("Africa's Talking SMS configured with username: {}", username);
            } catch (Exception e) {
                log.error("Failed to initialize Africa's Talking HTTP client: {}", e.getMessage());
            }
        } else {
            log.warn("Africa's Talking not configured (no API key).");
        }
    }

    /**
     * Send SMS via Africa's Talking REST API
     */
    public String sendSms(String message, String from, List<String> recipients) throws Exception {
        if (!configured) {
            throw new RuntimeException("Africa's Talking not configured");
        }

        String to = recipients.stream().collect(Collectors.joining(","));
        String url = "sandbox".equalsIgnoreCase(username) ? SANDBOX_URL : LIVE_URL;

        StringBuilder body = new StringBuilder();
        body.append("username=").append(enc(username));
        body.append("&to=").append(enc(to));
        body.append("&message=").append(enc(message));
        if (from != null && !from.isEmpty()) {
            body.append("&from=").append(enc(from));
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Accept", "application/json")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("apiKey", apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        log.info("Africa's Talking response [{}]: {}", response.statusCode(), response.body());

        if (response.statusCode() != 200 && response.statusCode() != 201) {
            throw new RuntimeException("Africa's Talking error: " + response.body());
        }

        return response.body();
    }

    private String enc(String val) {
        return URLEncoder.encode(val, StandardCharsets.UTF_8);
    }
}
