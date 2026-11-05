package com.aqualink.sms;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Map;

@Service
public class AiSmsAssistant {

    private final RestClient restClient;
    private final String apiKey;
    private final String baseUrl;
    private final String model;

    public AiSmsAssistant(
            RestClient.Builder restClientBuilder,
            @Value("${aqualink.ai.api-key:}") String apiKey,
            @Value("${aqualink.ai.base-url:https://api.openai.com/v1}") String baseUrl,
            @Value("${aqualink.ai.model:gpt-4o-mini}") String model) {
        this.restClient = restClientBuilder.build();
        this.apiKey = apiKey;
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.model = model;
    }

    public String answer(String incomingMessage) {
        if (apiKey.isBlank()) {
            return fallbackReply();
        }

        Map<String, Object> request = Map.of(
                "model", model,
                "temperature", 0.2,
                "max_tokens", 180,
                "messages", List.of(
                        Map.of("role", "system", "content",
                                "You are AquaLink's SMS water-ordering assistant. Reply in concise plain text, "
                                        + "under 500 characters. Help users order water using: ORDER <litres> "
                                        + "<start-UTC> <end-UTC>, with ISO-8601 timestamps such as "
                                        + "2026-11-06T09:00:00Z. STATUS checks their latest order. Never claim "
                                        + "to have placed, changed, or cancelled an order; only explicit commands "
                                        + "do that. Do not ask for passwords or payment details."),
                        Map.of("role", "user", "content", incomingMessage)));

        try {
            JsonNode response = restClient.post()
                    .uri(baseUrl + "/chat/completions")
                    .headers(headers -> headers.setBearerAuth(apiKey))
                    .body(request)
                    .retrieve()
                    .body(JsonNode.class);
            String answer = response == null ? "" : response.path("choices").path(0)
                    .path("message").path("content").asText("").trim();
            return answer.isBlank() ? fallbackReply() : answer.substring(0, Math.min(answer.length(), 1000));
        } catch (RestClientException ex) {
            return fallbackReply();
        }
    }

    private String fallbackReply() {
        return "To order: ORDER <litres> <start-UTC> <end-UTC>. Example: ORDER 1000 "
                + "2026-11-06T09:00:00Z 2026-11-06T11:00:00Z. Reply STATUS for your latest order, "
                + "or HELP for assistance.";
    }
}