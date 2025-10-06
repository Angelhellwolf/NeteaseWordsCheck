package com.remiaft.neteasewordscheck.http;

import org.json.JSONObject;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class NeteaseApiClient {
    private static final URI API_ENDPOINT = URI.create("https://g79apigatewayobt.nie.netease.com/mc-server/check-words-sensitive");

    private final HttpClient client;
    private final Logger logger;

    public NeteaseApiClient(Logger logger) {
        this.logger = logger;
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public Optional<String> checkContent(String content) {
        try {
            JSONObject payload = new JSONObject();
            payload.put("content", content);

            HttpRequest request = HttpRequest.newBuilder(API_ENDPOINT)
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload.toString(), StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            if (response.statusCode() != 200) {
                logger.log(Level.WARNING, "Unexpected response code {0} from NetEase API", response.statusCode());
                return Optional.empty();
            }

            JSONObject json = new JSONObject(response.body());
            if (json.optInt("code", -1) == 0) {
                return Optional.empty();
            }
            return Optional.ofNullable(json.optString("details", null));
        } catch (Exception exception) {
            logger.log(Level.WARNING, "Failed to contact NetEase API", exception);
            return Optional.empty();
        }
    }
}
