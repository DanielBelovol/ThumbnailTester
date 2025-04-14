package com.example.ThumbnailTester.services;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;


import okhttp3.*;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Base64;

@Service
public class GoogleOauthService {
    private final OkHttpClient httpClient = new OkHttpClient();

    private static final String TOKEN_ENDPOINT = "https://oauth2.googleapis.com/token";
    private static final String CLIENT_ID = "YOUR_CLIENT_ID";
    private static final String CLIENT_SECRET = "YOUR_CLIENT_SECRET";
    private static final String REDIRECT_URI = "YOUR_REDIRECT_URI";

    public GoogleTokenResponse exchangeCode(String code) throws IOException {
        RequestBody formBody = new FormBody.Builder()
                .add("code", code)
                .add("client_id", CLIENT_ID)
                .add("client_secret", CLIENT_SECRET)
                .add("redirect_uri", REDIRECT_URI)
                .add("grant_type", "authorization_code")
                .build();

        Request request = new Request.Builder()
                .url(TOKEN_ENDPOINT)
                .post(formBody)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) throw new IOException("Unexpected code " + response);

            String responseBody = response.body().string();
            JsonObject json = JsonParser.parseString(responseBody).getAsJsonObject();

            String accessToken = json.get("access_token").getAsString();
            String refreshToken = json.get("refresh_token").getAsString();
            String idToken = json.get("id_token").getAsString();
            String googleId = parseGoogleIdFromIdToken(idToken);

            return new GoogleTokenResponse(googleId, accessToken, refreshToken);
        }
    }

    private String parseGoogleIdFromIdToken(String idToken) {
        // id_token is a JWT: header.payload.signature (we need payload)
        String[] parts = idToken.split("\\.");
        if (parts.length != 3) throw new IllegalArgumentException("Invalid ID token");

        String payloadJson = new String(Base64.getUrlDecoder().decode(parts[1]));
        JsonObject payload = JsonParser.parseString(payloadJson).getAsJsonObject();

        return payload.get("sub").getAsString(); // Google User ID
    }

    public record GoogleTokenResponse(String googleId, String accessToken, String refreshToken) {
    }
}
