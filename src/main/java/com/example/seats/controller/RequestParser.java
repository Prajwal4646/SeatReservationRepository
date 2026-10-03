package com.example.seats.controller;

import com.example.seats.exception.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

@Component
public class RequestParser {
    private final ObjectMapper mapper = new ObjectMapper();

    public JsonNode readBody(HttpServletRequest request) {
        try {
            StringBuilder body = new StringBuilder();
            try (BufferedReader reader = request.getReader()) {
                String line;
                while ((line = reader.readLine()) != null) {
                    body.append(line);
                }
            }
            if (body.isEmpty()) {
                return mapper.readTree("{}");
            }
            return mapper.readTree(body.toString());
        } catch (Exception e) {
            throw new ApiException(400, "invalid_json", "Request body must be valid JSON.");
        }
    }

    public List<String> seatList(JsonNode node, int maxSeats) {
        if (node == null || !node.isArray()) {
            throw new ApiException(422, "invalid_seats", "seats must be a JSON array.");
        }

        List<String> result = new ArrayList<>();
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        for (JsonNode item : node) {
            if (!item.isTextual()) {
                throw new ApiException(422, "invalid_seat", "Each seat must be a string.");
            }
            String value = item.asText().trim();
            if (value.isBlank()) {
                throw new ApiException(422, "invalid_seat", "Seat labels cannot be blank.");
            }
            if (seen.add(value)) {
                result.add(value);
            }
        }
        if (result.size() > maxSeats) {
            throw new ApiException(422, "too_many_seats", "You can request at most " + maxSeats + " seats.");
        }
        return result;
    }

    public String idempotencyKey(String headerKey, JsonNode body) {
        String bodyKey = body.has("idempotency_key") && body.get("idempotency_key") != null
                ? body.get("idempotency_key").asText() : null;

        String resolved = bodyKey != null && !bodyKey.isBlank() ? bodyKey : headerKey;
        if (resolved == null || resolved.isBlank()) {
            throw new ApiException(400, "idempotency_key_required", "An idempotency key is required.");
        }
        if (headerKey != null && bodyKey != null && !headerKey.equals(bodyKey)) {
            throw new ApiException(400, "idempotency_key_conflict", "Header and body idempotency keys differ.");
        }
        return resolved;
    }
}
