package com.leadfold.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leadfold.dto.ParsedLead;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

// Calls Claude to turn a messy raw message (WhatsApp text, form submission,
// DM) into structured lead data. This AI-parsing step is the actual product
// differentiator over a plain Zapier/Make webhook — those can move data
// around but can't extract structure from free-form text on their own.
@Service
public class LeadParserService {

    private static final String SYSTEM_PROMPT = """
            You extract structured lead information from an incoming customer message \
            (WhatsApp, web form, or DM). Return ONLY a JSON object with this exact shape, \
            no markdown, no explanation:
            {
              "name": string or null,
              "contact": string or null,
              "interestSummary": string or null,
              "extractedFields": { any other useful structured details you can infer, e.g. \
              budget, preferred_date, service_type, urgency — keep keys snake_case, empty \
              object if none }
            }
            If the message is too vague to extract anything meaningful, still return the \
            object with nulls/empty object rather than refusing.
            """;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public LeadParserService(@Value("${leadfold.anthropic-api-key}") String apiKey, ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder()
                .baseUrl("https://api.anthropic.com/v1")
                .defaultHeader("x-api-key", apiKey)
                .defaultHeader("anthropic-version", "2023-06-01")
                .defaultHeader("content-type", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    public ParsedLead parse(String rawMessage) {
        Map<String, Object> requestBody = Map.of(
                "model", "claude-haiku-4-5-20251001",
                "max_tokens", 500,
                "system", SYSTEM_PROMPT,
                "messages", List.of(Map.of("role", "user", "content", rawMessage))
        );

        @SuppressWarnings("unchecked")
        Map<String, Object> response = restClient.post()
                .uri("/messages")
                .body(requestBody)
                .retrieve()
                .body(Map.class);

        String text = extractText(response);

        try {
            JsonNode node = objectMapper.readTree(text);
            return new ParsedLead(
                    textOrNull(node.get("name")),
                    textOrNull(node.get("contact")),
                    textOrNull(node.get("interestSummary")),
                    node.has("extractedFields") ? node.get("extractedFields").toString() : "{}"
            );
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse model output as JSON: " + text, e);
        }
    }

    @SuppressWarnings("unchecked")
    private String extractText(Map<String, Object> response) {
        List<Map<String, Object>> content = (List<Map<String, Object>>) response.get("content");
        for (Map<String, Object> block : content) {
            if ("text".equals(block.get("type"))) {
                return (String) block.get("text");
            }
        }
        throw new IllegalStateException("No text content returned from parser model");
    }

    private String textOrNull(JsonNode node) {
        return (node == null || node.isNull()) ? null : node.asText();
    }
}
