package com.example.seats.service;

import com.example.seats.exception.ApiException;
import com.example.seats.model.Show;
import com.example.seats.model.ShowDetails;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ShowService {
    private final JdbcTemplate jdbc;
    private final Map<String, Show> shows = new ConcurrentHashMap<>();
    private final Map<String, List<String>> seatInventory = new ConcurrentHashMap<>();

    public ShowService() {
        this(null);
    }

    public ShowService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public synchronized ShowDetails createShow(String name, List<String> seats, long pricePaise, int perUserLimit) {
        if (jdbc != null) {
            String showId = UUID.randomUUID().toString();
            UUID showUuid = UUID.fromString(showId);
            jdbc.update(
                    "INSERT INTO shows (id, name, price_paise, per_user_limit, total_seats) VALUES (?, ?, ?, ?, ?)",
                    showUuid, name.strip(), pricePaise, perUserLimit, seats.size()
            );

            for (int i = 0; i < seats.size(); i++) {
                jdbc.update(
                        "INSERT INTO seats (show_id, label, ord, status, reservation_id) VALUES (?, ?, ?, 'available', NULL)",
                        showUuid, seats.get(i), i
                );
            }
            return new ShowDetails(showId, name.strip(), pricePaise, perUserLimit, seats.size(), List.copyOf(seats));
        }

        String showId = UUID.randomUUID().toString();
        Show show = new Show(showId, name.strip(), pricePaise, perUserLimit, seats.size());
        shows.put(showId, show);
        seatInventory.put(showId, new ArrayList<>(seats));
        return new ShowDetails(showId, show.name(), show.pricePaise(), show.perUserLimit(), show.totalSeats(), List.copyOf(seats));
    }

    public List<Show> listShows() {
        if (jdbc != null) {
            return jdbc.query(
                    "SELECT id, name, price_paise, per_user_limit, total_seats FROM shows ORDER BY created_at",
                    (rs, rowNum) -> new Show(
                            rs.getString("id"),
                            rs.getString("name"),
                            rs.getLong("price_paise"),
                            rs.getInt("per_user_limit"),
                            rs.getInt("total_seats")
                    )
            );
        }
        return shows.values().stream().toList();
    }

    public ShowDetails getShow(String showId, boolean includeSeats) {
        if (jdbc != null) {
            Show show = getShowRecord(showId);
            if (!includeSeats) {
                return new ShowDetails(show.id(), show.name(), show.pricePaise(), show.perUserLimit(), show.totalSeats(), List.of());
            }
            List<String> available = jdbc.queryForList(
                    "SELECT label FROM seats WHERE show_id = ? AND status = 'available' ORDER BY ord",
                    String.class,
                    UUID.fromString(showId)
            );
            return new ShowDetails(show.id(), show.name(), show.pricePaise(), show.perUserLimit(), show.totalSeats(), available);
        }

        Show show = shows.get(showId);
        if (show == null) {
            throw new ApiException(404, "show_not_found", "Show not found.");
        }
        if (!includeSeats) {
            return new ShowDetails(show.id(), show.name(), show.pricePaise(), show.perUserLimit(), show.totalSeats(), List.of());
        }
        return new ShowDetails(show.id(), show.name(), show.pricePaise(), show.perUserLimit(), show.totalSeats(),
                List.copyOf(seatInventory.getOrDefault(showId, List.of())));
    }

    public Show getShowRecord(String showId) {
        if (jdbc != null) {
            try {
                return jdbc.queryForObject(
                        "SELECT id, name, price_paise, per_user_limit, total_seats FROM shows WHERE id = ?",
                        (rs, rowNum) -> new Show(
                                rs.getString("id"),
                                rs.getString("name"),
                                rs.getLong("price_paise"),
                                rs.getInt("per_user_limit"),
                                rs.getInt("total_seats")
                        ),
                        UUID.fromString(showId)
                );
            } catch (EmptyResultDataAccessException e) {
                throw new ApiException(404, "show_not_found", "Show not found.");
            }
        }

        Show show = shows.get(showId);
        if (show == null) {
            throw new ApiException(404, "show_not_found", "Show not found.");
        }
        return show;
    }

    public List<String> seatsForShow(String showId) {
        if (jdbc != null) {
            return jdbc.queryForList(
                    "SELECT label FROM seats WHERE show_id = ? AND status = 'available' ORDER BY ord",
                    String.class,
                    UUID.fromString(showId)
            );
        }
        return List.copyOf(seatInventory.getOrDefault(showId, List.of()));
    }

    public synchronized void reserveSeats(String showId, List<String> selectedSeats) {
        if (jdbc != null) {
            for (String seat : selectedSeats) {
                int updated = jdbc.update(
                        "UPDATE seats SET status = 'confirmed', reservation_id = ? WHERE show_id = ? AND label = ? AND status = 'available'",
                        UUID.fromString(showId),
                        UUID.fromString(showId),
                        seat
                );
                if (updated == 0) {
                    throw new ApiException(422, "seat_unavailable", "Seat " + seat + " is no longer available.");
                }
            }
            return;
        }

        List<String> current = new ArrayList<>(seatInventory.getOrDefault(showId, List.of()));
        for (String seat : selectedSeats) {
            if (!current.contains(seat)) {
                throw new ApiException(422, "seat_not_found", "Seat " + seat + " does not exist in this show.");
            }
        }
        current.removeAll(selectedSeats);
        seatInventory.put(showId, current);
    }

    public synchronized void cancelSeats(String showId, List<String> seats) {
        if (jdbc != null) {
            for (String seat : seats) {
                jdbc.update(
                        "UPDATE seats SET status = 'available', reservation_id = NULL WHERE show_id = ? AND label = ?",
                        UUID.fromString(showId),
                        seat
                );
            }
            return;
        }

        List<String> current = new ArrayList<>(seatInventory.getOrDefault(showId, List.of()));
        current.addAll(seats);
        seatInventory.put(showId, current.stream().distinct().sorted().toList());
    }
}
