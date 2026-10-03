package com.example.seats.service;

import com.example.seats.exception.ApiException;
import com.example.seats.model.CancelResult;
import com.example.seats.model.IdempotencyRecord;
import com.example.seats.model.Reservation;
import com.example.seats.model.ReserveResult;
import com.example.seats.model.Show;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ReservationService {
    private final ShowService showService;
    private final Map<String, Reservation> reservations = new ConcurrentHashMap<>();
    private final Map<String, Map<String, IdempotencyRecord>> idempotencyByUser = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Integer>> userUsageByShow = new ConcurrentHashMap<>();
    private final Map<String, List<String>> reservedSeatsByShow = new ConcurrentHashMap<>();

    public ReservationService(ShowService showService) {
        this.showService = showService;
    }

    public ReserveResult reserve(String showId, String userId, List<String> requestedSeats, String idempotencyKey) {
        Show show = showService.getShowRecord(showId);
        List<String> normalized = new ArrayList<>(requestedSeats);
        Collections.sort(normalized);

        if (normalized.isEmpty()) {
            throw new ApiException(422, "no_seats_selected", "Please select at least one seat.");
        }
        if (normalized.size() > show.perUserLimit()) {
            throw ApiException.perUserLimit(show.perUserLimit());
        }

        String requestHash = showId + "|" + String.join(",", normalized);
        Map<String, IdempotencyRecord> userKeys = idempotencyByUser.computeIfAbsent(userId, ignored -> new ConcurrentHashMap<>());
        IdempotencyRecord prior = userKeys.get(idempotencyKey);
        if (prior != null) {
            if (!Objects.equals(prior.requestHash(), requestHash)) {
                throw ApiException.idempotencyKeyReuse();
            }
            Reservation existing = reservations.get(prior.reservationId());
            if (existing != null) {
                return new ReserveResult(existing, true);
            }
        }

        List<String> available = new ArrayList<>(showService.seatsForShow(showId));
        for (String seat : normalized) {
            if (!available.contains(seat)) {
                throw new ApiException(422, "seat_unavailable", "Seat " + seat + " is no longer available.");
            }
        }

        Map<String, Integer> usage = userUsageByShow.computeIfAbsent(showId, ignored -> new ConcurrentHashMap<>());
        int alreadyHeld = usage.getOrDefault(userId, 0);
        if (alreadyHeld + normalized.size() > show.perUserLimit()) {
            throw ApiException.perUserLimit(show.perUserLimit());
        }

        String reservationId = UUID.randomUUID().toString();
        Reservation reservation = new Reservation(
                reservationId,
                showId,
                userId,
                normalized,
                show.pricePaise() * normalized.size(),
                Reservation.CONFIRMED
        );

        reservations.put(reservationId, reservation);
        userKeys.put(idempotencyKey, new IdempotencyRecord(userId, idempotencyKey, requestHash, reservationId));
        usage.put(userId, alreadyHeld + normalized.size());
        List<String> reservedSeats = new ArrayList<>(reservedSeatsByShow.getOrDefault(showId, List.of()));
        reservedSeats.addAll(normalized);
        reservedSeatsByShow.put(showId, reservedSeats);

        showService.reserveSeats(showId, normalized);
        return new ReserveResult(reservation, false);
    }

    public Reservation getReservation(String reservationId, String userId) {
        Reservation reservation = reservations.get(reservationId);
        if (reservation == null) {
            throw new ApiException(404, "reservation_not_found", "Reservation not found.");
        }
        if (!reservation.userId().equals(userId)) {
            throw new ApiException(403, "forbidden", "You do not own this reservation.");
        }
        return reservation;
    }

    public CancelResult cancel(String reservationId, String userId) {
        Reservation reservation = reservations.get(reservationId);
        if (reservation == null) {
            throw new ApiException(404, "reservation_not_found", "Reservation not found.");
        }
        if (!reservation.userId().equals(userId)) {
            throw new ApiException(403, "forbidden", "You do not own this reservation.");
        }
        if (Reservation.CANCELLED.equals(reservation.status())) {
            return new CancelResult(reservation, false);
        }

        Reservation cancelled = reservation.withStatus(Reservation.CANCELLED);
        reservations.put(reservationId, cancelled);
        Map<String, Integer> usage = userUsageByShow.getOrDefault(reservation.showId(), Map.of());
        int total = usage.getOrDefault(userId, 0);
        usage.put(userId, Math.max(0, total - reservation.seats().size()));
        showService.cancelSeats(reservation.showId(), reservation.seats());
        return new CancelResult(cancelled, true);
    }
}
