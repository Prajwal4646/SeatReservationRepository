package com.example.seats.service;

import com.example.seats.exception.ApiException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ReservationServiceTest {

    @Test
    void reserve_shouldRejectAlreadyTakenSeat() {
        ShowService showService = new ShowService();
        ReservationService reservationService = new ReservationService(showService);

        String showId = showService.createShow("Test show", List.of("A1", "A2"), 1000, 2).id();

        reservationService.reserve(showId, "user-1", List.of("A1"), "idempotency-1");

        ApiException ex = assertThrows(ApiException.class,
                () -> reservationService.reserve(showId, "user-2", List.of("A1"), "idempotency-2"));

        assertEquals("seat_unavailable", ex.code());
    }

    @Test
    void sameIdempotencyKeyWithDifferentSeatsShouldFail() {
        ShowService showService = new ShowService();
        ReservationService reservationService = new ReservationService(showService);

        String showId = showService.createShow("Test show", List.of("A1", "A2"), 1000, 2).id();
        reservationService.reserve(showId, "user-1", List.of("A1"), "same-key");

        ApiException ex = assertThrows(ApiException.class,
                () -> reservationService.reserve(showId, "user-1", List.of("A2"), "same-key"));

        assertEquals("idempotency_key_reuse", ex.code());
    }
}
