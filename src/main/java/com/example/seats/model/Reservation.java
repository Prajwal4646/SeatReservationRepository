package com.example.seats.model;

import java.util.List;

public record Reservation(String id, String showId, String userId, List<String> seats, long amountPaise,
                          String status) {
    public static final String CONFIRMED = "confirmed";
    public static final String CANCELLED = "cancelled";

    public Reservation withStatus(String newStatus) {
        return new Reservation(id, showId, userId, seats, amountPaise, newStatus);
    }
}
