package com.example.seats.service;

import com.example.seats.exception.ApiException;
import com.example.seats.model.CancelResult;
import com.example.seats.model.IdempotencyRecord;
import com.example.seats.model.Reservation;
import com.example.seats.model.ReserveResult;
import com.example.seats.model.Show;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
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
    private final JdbcTemplate jdbc;
    private final Map<String, Reservation> reservations = new ConcurrentHashMap<>();
    private final Map<String, Map<String, IdempotencyRecord>> idempotencyByUser = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Integer>> userUsageByShow = new ConcurrentHashMap<>();
    private final Map<String, List<String>> reservedSeatsByShow = new ConcurrentHashMap<>();

    public ReservationService() {
        this(null, null);
    }

    public ReservationService(ShowService showService) {
        this(showService, null);
    }

    public ReservationService(ShowService showService, JdbcTemplate jdbc) {
        this.showService = showService;
        this.jdbc = jdbc;
    }

    @Transactional
    public synchronized ReserveResult reserve(String showId, String userId, List<String> requestedSeats, String idempotencyKey) {
        if (jdbc != null) {
            return reserveDb(showId, userId, requestedSeats, idempotencyKey);
        }

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

    @Transactional(readOnly = true)
    public Reservation getReservation(String reservationId, String userId) {
        if (jdbc != null) {
            Reservation reservation = getReservationDb(reservationId);
            if (!reservation.userId().equals(userId)) {
                throw new ApiException(403, "forbidden", "You do not own this reservation.");
            }
            return reservation;
        }

        Reservation reservation = reservations.get(reservationId);
        if (reservation == null) {
            throw new ApiException(404, "reservation_not_found", "Reservation not found.");
        }
        if (!reservation.userId().equals(userId)) {
            throw new ApiException(403, "forbidden", "You do not own this reservation.");
        }
        return reservation;
    }

    @Transactional
    public synchronized CancelResult cancel(String reservationId, String userId) {
        if (jdbc != null) {
            return cancelDb(reservationId, userId);
        }

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

    private ReserveResult reserveDb(String showId, String userId, List<String> requestedSeats, String idempotencyKey) {
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
        try {
            Map<String, Object> priorRow = jdbc.queryForMap(
                    "SELECT request_hash, reservation_id FROM idempotency_keys WHERE user_id = ? AND key = ?",
                    userId,
                    idempotencyKey
            );
            String priorHash = String.valueOf(priorRow.get("request_hash"));
            if (!Objects.equals(priorHash, requestHash)) {
                throw ApiException.idempotencyKeyReuse();
            }
            Reservation existing = getReservationDb(String.valueOf(priorRow.get("reservation_id")));
            if (existing != null) {
                return new ReserveResult(existing, true);
            }
        } catch (EmptyResultDataAccessException ignored) {
            // no prior idempotency record yet
        }

        for (String seat : normalized) {
            String status = jdbc.queryForObject(
                    "SELECT status FROM seats WHERE show_id = ? AND label = ? FOR UPDATE",
                    String.class,
                    UUID.fromString(showId),
                    seat
            );
            if (!"available".equals(status)) {
                throw new ApiException(422, "seat_unavailable", "Seat " + seat + " is no longer available.");
            }
        }

        List<Integer> usageRows = jdbc.queryForList(
                "SELECT held FROM user_show_usage WHERE show_id = ? AND user_id = ? FOR UPDATE",
                Integer.class,
                UUID.fromString(showId),
                userId
        );
        int alreadyHeld = usageRows.isEmpty() ? 0 : usageRows.getFirst();
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

        try {
            jdbc.update(con -> {
                PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO reservations (id, show_id, user_id, seats, amount_paise, status) VALUES (?, ?, ?, ?, ?, ?)");
                ps.setObject(1, UUID.fromString(reservationId));
                ps.setObject(2, UUID.fromString(showId));
                ps.setString(3, userId);
                ps.setArray(4, toTextArray(con, normalized));
                ps.setLong(5, reservation.amountPaise());
                ps.setString(6, Reservation.CONFIRMED);
                return ps;
            });
        } catch (DuplicateKeyException e) {
            throw new ApiException(409, "reservation_conflict", "Reservation already exists.");
        }

        for (String seat : normalized) {
            int updated = jdbc.update(
                    "UPDATE seats SET status = 'confirmed', reservation_id = ? WHERE show_id = ? AND label = ?",
                    UUID.fromString(reservationId),
                    UUID.fromString(showId),
                    seat
            );
            if (updated == 0) {
                throw new ApiException(422, "seat_unavailable", "Seat " + seat + " is no longer available.");
            }
        }

        jdbc.update(
                "INSERT INTO user_show_usage (show_id, user_id, held) VALUES (?, ?, ?) ON CONFLICT (show_id, user_id) DO UPDATE SET held = user_show_usage.held + EXCLUDED.held",
                UUID.fromString(showId),
                userId,
                normalized.size()
        );

        try {
            jdbc.update(
                    "INSERT INTO idempotency_keys (user_id, key, request_hash, reservation_id) VALUES (?, ?, ?, ?)",
                    userId,
                    idempotencyKey,
                    requestHash,
                    UUID.fromString(reservationId)
            );
        } catch (DuplicateKeyException duplication) {
            Map<String, Object> row = jdbc.queryForMap(
                    "SELECT request_hash, reservation_id FROM idempotency_keys WHERE user_id = ? AND key = ?",
                    userId,
                    idempotencyKey
            );
            String storedHash = String.valueOf(row.get("request_hash"));
            if (!Objects.equals(storedHash, requestHash)) {
                throw ApiException.idempotencyKeyReuse();
            }
            Reservation existing = getReservationDb(String.valueOf(row.get("reservation_id")));
            return new ReserveResult(existing, true);
        }

        reservations.put(reservationId, reservation);
        return new ReserveResult(reservation, false);
    }

    private CancelResult cancelDb(String reservationId, String userId) {
        Reservation reservation = getReservationDb(reservationId);
        if (!reservation.userId().equals(userId)) {
            throw new ApiException(403, "forbidden", "You do not own this reservation.");
        }
        if (Reservation.CANCELLED.equals(reservation.status())) {
            return new CancelResult(reservation, false);
        }

        jdbc.update(
                "UPDATE seats SET status = 'available', reservation_id = NULL WHERE show_id = ? AND reservation_id = ?",
                UUID.fromString(reservation.showId()),
                UUID.fromString(reservationId)
        );

        jdbc.update(
                "UPDATE reservations SET status = 'cancelled', cancelled_at = now() WHERE id = ?",
                UUID.fromString(reservationId)
        );

        jdbc.update(
                "UPDATE user_show_usage SET held = GREATEST(0, held - ?) WHERE show_id = ? AND user_id = ?",
                reservation.seats().size(),
                UUID.fromString(reservation.showId()),
                userId
        );

        Reservation cancelled = reservation.withStatus(Reservation.CANCELLED);
        return new CancelResult(cancelled, true);
    }

    private Reservation getReservationDb(String reservationId) {
        try {
            return jdbc.queryForObject(
                    "SELECT id, show_id, user_id, seats, amount_paise, status FROM reservations WHERE id = ?",
                    (rs, rowNum) -> new Reservation(
                            rs.getString("id"),
                            rs.getString("show_id"),
                            rs.getString("user_id"),
                            readSeatArray(rs.getArray("seats")),
                            rs.getLong("amount_paise"),
                            rs.getString("status")
                    ),
                    UUID.fromString(reservationId)
            );
        } catch (EmptyResultDataAccessException e) {
            throw new ApiException(404, "reservation_not_found", "Reservation not found.");
        }
    }

    private static List<String> readSeatArray(Array array) {
        try {
            Object value = array == null ? null : array.getArray();
            if (value instanceof String[] seats) {
                return List.of(seats);
            }
            if (value instanceof Object[] arr) {
                List<String> result = new ArrayList<>();
                for (Object item : arr) {
                    result.add(String.valueOf(item));
                }
                return result;
            }
            return List.of();
        } catch (SQLException e) {
            return List.of();
        }
    }

    private static Array toTextArray(Connection con, List<String> seats) throws SQLException {
        return con.createArrayOf("text", seats.toArray(new String[0]));
    }
}
