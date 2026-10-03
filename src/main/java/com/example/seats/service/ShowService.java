package com.example.seats.service;

import com.example.seats.exception.ApiException;
import com.example.seats.model.Show;
import com.example.seats.model.ShowDetails;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ShowService {
    private final Map<String, Show> shows = new ConcurrentHashMap<>();
    private final Map<String, List<String>> seatInventory = new ConcurrentHashMap<>();

    public ShowDetails createShow(String name, List<String> seats, long pricePaise, int perUserLimit) {
        String showId = UUID.randomUUID().toString();
        Show show = new Show(showId, name.strip(), pricePaise, perUserLimit, seats.size());
        shows.put(showId, show);
        seatInventory.put(showId, new ArrayList<>(seats));
        return new ShowDetails(showId, show.name(), show.pricePaise(), show.perUserLimit(), show.totalSeats(), List.copyOf(seats));
    }

    public List<Show> listShows() {
        return shows.values().stream().toList();
    }

    public ShowDetails getShow(String showId, boolean includeSeats) {
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
        Show show = shows.get(showId);
        if (show == null) {
            throw new ApiException(404, "show_not_found", "Show not found.");
        }
        return show;
    }

    public List<String> seatsForShow(String showId) {
        return List.copyOf(seatInventory.getOrDefault(showId, List.of()));
    }

    public void reserveSeats(String showId, List<String> selectedSeats) {
        List<String> current = new ArrayList<>(seatInventory.getOrDefault(showId, List.of()));
        for (String seat : selectedSeats) {
            if (!current.contains(seat)) {
                throw new ApiException(422, "seat_not_found", "Seat " + seat + " does not exist in this show.");
            }
        }
        current.removeAll(selectedSeats);
        seatInventory.put(showId, current);
    }

    public void cancelSeats(String showId, List<String> seats) {
        List<String> current = new ArrayList<>(seatInventory.getOrDefault(showId, List.of()));
        current.addAll(seats);
        seatInventory.put(showId, current.stream().distinct().sorted().toList());
    }
}
