package com.example.seats.service;

import com.example.seats.model.Show;
import com.example.seats.model.ShowDetails;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class SeatReservationFlowTest {

    @Test
    void createShow_shouldExposePriceAndSeatCount() {
        ShowDetails show = new ShowDetails(
                "show-1",
                "Sunset Live",
                2500L,
                4,
                3,
                List.of("A1", "A2", "A3")
        );

        assertEquals("Sunset Live", show.name());
        assertEquals(2500L, show.pricePaise());
        assertEquals(3, show.totalSeats());
        assertNotNull(show.seats());
    }

    @Test
    void show_shouldTrackSeatLabels() {
        Show show = new Show("show-2", "Night Show", 1500L, 2, 2);

        assertEquals("Night Show", show.name());
        assertEquals(2, show.totalSeats());
        assertEquals(2, show.perUserLimit());
    }
}
