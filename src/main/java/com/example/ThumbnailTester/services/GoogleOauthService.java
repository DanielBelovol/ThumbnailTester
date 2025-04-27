package com.example.ThumbnailTester.services;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
public class GoogleOauthService {
    private final OkHttpClient httpClient = new OkHttpClient();

    @Value("${google.client.id}")
    private String clientId;

    @Value("${google.client.secret}")
    private String clientSecret;

    private static final String TOKEN_ENDPOINT = "https://oauth2.googleapis.com/token";

    // update the access token using the refresh token
    public GoogleTokenResponse refreshAccessToken(String refreshToken) throws IOException {
        RequestBody formBody = new FormBody.Builder()
                .add("client_id", clientId)
                .add("client_secret", clientSecret)
                .add("refresh_token", refreshToken)
                .add("grant_type", "refresh_token")
                .build();

        Request request = new Request.Builder()
                .url(TOKEN_ENDPOINT)
                .post(formBody)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Failed to refresh token: " + response);
            }

            String responseBody = response.body().string();
            JsonObject json = JsonParser.parseString(responseBody).getAsJsonObject();

            String accessToken = json.get("access_token").getAsString();
            return new GoogleTokenResponse(accessToken);
        }
    }

    public record GoogleTokenResponse(String accessToken) {
    }
}