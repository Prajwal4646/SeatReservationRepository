package com.example.seats.controller;

import com.example.seats.model.CancelResult;
import com.example.seats.model.Reservation;
import com.example.seats.model.ReserveResult;
import com.example.seats.service.AuthService;
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
    private final AuthService authService;

    public ReservationController(ReservationService reservationService, RequestParser requestParser, AuthService authService) {
        this.reservationService = reservationService;
        this.requestParser = requestParser;
        this.authService = authService;
    }

    @PostMapping("/shows/{showId}/reserve")
    public ResponseEntity<Reservation> reserve(@PathVariable String showId, HttpServletRequest request) {
        String userId = authService.requireUser(request.getHeader("Authorization"));

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
        String userId = authService.requireUser(request.getHeader("Authorization"));
        return reservationService.getReservation(reservationId, userId);
    }

    @PostMapping("/reservations/{reservationId}/cancel")
    public Reservation cancel(@PathVariable String reservationId, HttpServletRequest request) {
        String userId = authService.requireUser(request.getHeader("Authorization"));
        CancelResult result = reservationService.cancel(reservationId, userId);
        return result.reservation();
    }
}
