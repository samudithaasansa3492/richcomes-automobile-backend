
package com.richcomes.garage.inventory.chatbot;

import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Service
public class ChatService {

    private static final String GROQ_URL =
            "https://api.groq.com/openai/v1/chat/completions";

    private static final String MODEL = "openai/gpt-oss-20b";

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    // General AI conversation
    public String askAI(String question) {

        if (question == null || question.isBlank()
                || question.length() > 1000) {
            throw new IllegalArgumentException(
                    "Question must contain 1-1000 characters"
            );
        }

        Map<String, Object> body = Map.of(
                "model", MODEL,
                "include_reasoning", false,
                "max_completion_tokens", 300,
                "messages", List.of(
                        Map.of(
                                "role", "system",
                                "content",
                                "You are the Richcomes Garage AI assistant. " +
                                        "Reply in the user's language. " +
                                        "You do not have access to live " +
                                        "garage information in this conversation. " +
                                        "Never invent garage records."
                        ),
                        Map.of(
                                "role", "user",
                                "content", question
                        )
                )
        );

        try {
            JsonNode response = sendToGroq(body);

            String answer = response.path("choices")
                    .path(0)
                    .path("message")
                    .path("content")
                    .asText("");

            if (answer.isBlank()) {
                throw new IllegalStateException(
                        "AI returned an empty answer"
                );
            }

            return answer;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                    "AI request interrupted", e
            );

        } catch (Exception e) {
            throw new IllegalStateException(
                    "AI request failed", e
            );
        }
    }

    // Reusable Groq API connection for all chatbot modules
    public JsonNode sendToGroq(Map<String, Object> body)
            throws Exception {

        String apiKey = System.getenv("GROQ_API_KEY");

        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "GROQ_API_KEY is missing"
            );
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(GROQ_URL))
                .timeout(Duration.ofSeconds(30))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        objectMapper.writeValueAsString(body)
                ))
                .build();

        HttpResponse<String> response = httpClient.send(
                request,
                HttpResponse.BodyHandlers.ofString()
        );

        if (response.statusCode() != 200) {
            throw new IllegalStateException(
                    "AI service unavailable. HTTP status: "
                            + response.statusCode()
            );
        }

        return objectMapper.readTree(response.body());
    }
}
