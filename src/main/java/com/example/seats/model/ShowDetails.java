package com.example.seats.model;

import java.util.List;

public record ShowDetails(String id, String name, long pricePaise, int perUserLimit, int totalSeats,
                         List<String> seats) {
}
