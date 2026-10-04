package com.example.seats.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class AppMetrics {
    private final MeterRegistry registry;
    public final Counter confirmed;
    public final Counter seatsSold;
    public final Counter cancelled;

    public AppMetrics(MeterRegistry registry) {
        this.registry = registry;
        this.confirmed = Counter.builder("reservations.confirmed")
                .description("Reservations confirmed by the service")
                .register(registry);
        this.seatsSold = Counter.builder("seats.sold")
                .description("Seats sold across all reservations")
                .register(registry);
        this.cancelled = Counter.builder("reservations.cancelled")
                .description("Reservations cancelled by the owner")
                .register(registry);

        for (String reason : new String[]{"idempotent_replay", "seat_unavailable", "per_user_limit", "idempotency_key_reuse"}) {
            declined(reason, 0);
        }
    }

    public void declined(String reason) {
        declined(reason, 1);
    }

    private void declined(String reason, double amount) {
        Counter counter = Counter.builder("reservations.declined")
                .description("Reservation requests declined by reason")
                .tag("reason", reason)
                .register(registry);
        if (amount > 0) {
            counter.increment(amount);
        }
    }
}
