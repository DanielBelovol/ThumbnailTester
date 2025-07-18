package com.example.ThumbnailTester.controller;

import com.example.ThumbnailTester.util.AESUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/youtube")
public class YouTubeAuthController {

    private static final Logger log = LoggerFactory.getLogger(YouTubeAuthController.class);
    @Autowired
    private AESUtil aesUtil;

    @Value("${youtube.client.id}")
    private String clientId;

    @Value("${youtube.client.secret}")
    private String clientSecret;

    @Value("${youtube.redirect.uri}")
    private String redirectUri;

    private static final String OAUTH_TOKEN_URL = "https://oauth2.googleapis.com/token";

    /**
     * Exchange authorization code for refresh token.
     *
     * @param code the authorization code received from Google OAuth2
     * @return JSON containing the refresh token or error message
     */
    @PostMapping(value = "/exchange-code", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public RefreshTokenResponse exchangeCodeForRefreshToken(@RequestParam("code") String code) {
        try {
            String requestBody = "code=" + URLEncoder.encode(aesUtil.decrypt(code), StandardCharsets.UTF_8)
                    + "&client_id=" + URLEncoder.encode(clientId, StandardCharsets.UTF_8)
                    + "&client_secret=" + URLEncoder.encode(clientSecret, StandardCharsets.UTF_8)
                    + "&redirect_uri=" + URLEncoder.encode(redirectUri, StandardCharsets.UTF_8)
                    + "&grant_type=authorization_code";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(OAUTH_TOKEN_URL))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpClient client = HttpClient.newHttpClient();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            log.info("Token exchange response: {}", response.body());

            ObjectMapper mapper = new ObjectMapper();
            JsonNode jsonNode = mapper.readTree(response.body());

            if (jsonNode.has("refresh_token")) {
                String refreshToken = jsonNode.get("refresh_token").asText();
                return new RefreshTokenResponse(aesUtil.encrypt(refreshToken), "Successful");
            } else if (jsonNode.has("error")) {
                String error = jsonNode.get("error").asText();
                String errorDescription = jsonNode.has("error_description") ? jsonNode.get("error_description").asText() : "";
                return new RefreshTokenResponse(null, error + ": " + errorDescription);
            } else {
                return new RefreshTokenResponse(null, "Unknown error during token exchange");
            }
        } catch (IOException | InterruptedException e) {
            log.error("Exception during token exchange", e);
            return new RefreshTokenResponse(null, "Exception: " + e.getMessage());
        } catch (Exception e) {
            log.error("Error with decrypting code");
            return new RefreshTokenResponse(null, "Error with decrypting code");
        }
    }

    public static class RefreshTokenResponse {
        private String refreshToken;
        private String message;

        public RefreshTokenResponse(String refreshToken, String message) {
            this.refreshToken = refreshToken;
            this.message = message;
        }

        public String getRefreshToken() {
            return refreshToken;
        }

        public void setRefreshToken(String refreshToken) {
            this.refreshToken = refreshToken;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }
    }
}
