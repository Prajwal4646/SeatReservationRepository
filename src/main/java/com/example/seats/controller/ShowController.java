package com.example.seats.controller;

import com.example.seats.exception.ApiException;
import com.example.seats.model.Show;
import com.example.seats.model.ShowDetails;
import com.example.seats.service.AuthService;
import com.example.seats.service.ShowService;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
public class ShowController {
    private final ShowService showService;
    private final RequestParser requestParser;
    private final AuthService authService;

    public ShowController(ShowService showService, RequestParser requestParser, AuthService authService) {
        this.showService = showService;
        this.requestParser = requestParser;
        this.authService = authService;
    }

    @PostMapping("/shows")
    public ResponseEntity<ShowDetails> createShow(HttpServletRequest request) {
        authService.requireAdmin(request.getHeader("Authorization"));

        JsonNode payload = requestParser.readBody(request);
        JsonNode nameNode = payload.get("name");
        JsonNode seatNode = payload.get("seats");
        JsonNode priceNode = payload.get("price_paise");

        if (nameNode == null || !nameNode.isTextual() || nameNode.asText().isBlank()) {
            throw new ApiException(422, "invalid_name", "name must be a non-empty string");
        }
        if (priceNode == null || !priceNode.isIntegralNumber()) {
            throw new ApiException(422, "invalid_price", "price_paise must be an integer");
        }

        List<String> seats = requestParser.seatList(seatNode, 200);
        String name = nameNode.asText().trim();
        long price = priceNode.asLong();

        int limit = payload.has("per_user_limit") && payload.get("per_user_limit") != null
                ? payload.get("per_user_limit").asInt(4)
                : 4;

        ShowDetails created = showService.createShow(name, seats, price, limit);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/shows")
    public Map<String, List<Show>> listShows() {
        return Map.of("shows", showService.listShows());
    }

    @GetMapping("/shows/{showId}")
    public ShowDetails getShow(@PathVariable String showId,
                             @RequestParam(name = "include_seats", defaultValue = "true") boolean includeSeats) {
        return showService.getShow(showId, includeSeats);
    }
}
