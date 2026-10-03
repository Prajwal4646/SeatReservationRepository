package com.example.seats.controller;

import com.example.seats.model.CancelResult;
import com.example.seats.model.Reservation;
import com.example.seats.model.ReserveResult;
import com.example.seats.service.ReservationService;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class ReservationController {
    private final ReservationService reservationService;
    private final RequestParser requestParser;

    public ReservationController(ReservationService reservationService, RequestParser requestParser) {
        this.reservationService = reservationService;
        this.requestParser = requestParser;
    }

    @PostMapping("/shows/{showId}/reserve")
    public ResponseEntity<Reservation> reserve(@PathVariable String showId, HttpServletRequest request) {
        String userId = request.getHeader("X-User-Id");
        if (userId == null || userId.isBlank()) {
            userId = "anonymous-user";
        }

        JsonNode payload = requestParser.readBody(request);
        List<String> seats = requestParser.seatList(payload.get("seats"), 10);
        String key = requestParser.idempotencyKey(request.getHeader("Idempotency-Key"), payload);

        ReserveResult result = reservationService.reserve(showId, userId, seats, key);
        if (result.replayed()) {
            return ResponseEntity.ok().header("Idempotent-Replay", "true").body(result.reservation());
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(result.reservation());
    }

    @GetMapping("/reservations/{reservationId}")
    public Reservation getReservation(@PathVariable String reservationId, HttpServletRequest request) {
        String userId = request.getHeader("X-User-Id");
        if (userId == null || userId.isBlank()) {
            userId = "anonymous-user";
        }
        return reservationService.getReservation(reservationId, userId);
    }

    @PostMapping("/reservations/{reservationId}/cancel")
    public Reservation cancel(@PathVariable String reservationId, HttpServletRequest request) {
        String userId = request.getHeader("X-User-Id");
        if (userId == null || userId.isBlank()) {
            userId = "anonymous-user";
        }
        CancelResult result = reservationService.cancel(reservationId, userId);
        return result.reservation();
    }
}
