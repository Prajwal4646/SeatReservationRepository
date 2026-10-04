package com.example.seats.dao;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Statement;

@Component
public class SchemaInitializer {
    private static final Logger log = LoggerFactory.getLogger(SchemaInitializer.class);

    static final String SCHEMA = """
            CREATE TABLE IF NOT EXISTS shows (
                id             uuid PRIMARY KEY,
                name           text NOT NULL,
                price_paise    bigint NOT NULL CHECK (price_paise >= 0),
                per_user_limit int    NOT NULL DEFAULT 4 CHECK (per_user_limit >= 1),
                total_seats    int    NOT NULL CHECK (total_seats > 0),
                created_at     timestamptz NOT NULL DEFAULT now()
            );

            CREATE TABLE IF NOT EXISTS seats (
                show_id        uuid NOT NULL REFERENCES shows(id) ON DELETE CASCADE,
                label          text NOT NULL,
                ord            int  NOT NULL,
                status         text NOT NULL DEFAULT 'available'
                               CHECK (status IN ('available', 'held', 'confirmed')),
                reservation_id uuid,
                PRIMARY KEY (show_id, label),
                CHECK ((status = 'available') = (reservation_id IS NULL))
            );

            CREATE INDEX IF NOT EXISTS seats_show_ord ON seats (show_id, ord);

            CREATE TABLE IF NOT EXISTS reservations (
                id           uuid PRIMARY KEY,
                show_id      uuid   NOT NULL REFERENCES shows(id),
                user_id      text   NOT NULL,
                seats        text[] NOT NULL,
                amount_paise bigint NOT NULL CHECK (amount_paise >= 0),
                status       text   NOT NULL CHECK (status IN ('confirmed', 'cancelled')),
                created_at   timestamptz NOT NULL DEFAULT now(),
                cancelled_at timestamptz
            );

            CREATE INDEX IF NOT EXISTS reservations_user ON reservations (user_id);

            CREATE TABLE IF NOT EXISTS user_show_usage (
                show_id uuid NOT NULL REFERENCES shows(id) ON DELETE CASCADE,
                user_id text NOT NULL,
                held    int  NOT NULL DEFAULT 0 CHECK (held >= 0),
                PRIMARY KEY (show_id, user_id)
            );

            CREATE TABLE IF NOT EXISTS idempotency_keys (
                user_id        text NOT NULL,
                key            text NOT NULL,
                request_hash   text NOT NULL,
                reservation_id uuid NOT NULL,
                created_at     timestamptz NOT NULL DEFAULT now(),
                PRIMARY KEY (user_id, key)
            );
            """;

    private final JdbcTemplate jdbc;
    private volatile boolean ready = false;

    public SchemaInitializer(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean isReady() {
        return ready;
    }

    @PostConstruct
    void start() {
        Thread thread = new Thread(this::run, "schema-init");
        thread.setDaemon(true);
        thread.start();
    }

    private void run() {
        int attempt = 0;
        while (!ready) {
            try {
                jdbc.execute((ConnectionCallback<Void>) con -> {
                    try (Statement st = con.createStatement()) {
                        st.execute("SELECT pg_advisory_lock(727274)");
                        try {
                            st.execute(SCHEMA);
                        } finally {
                            st.execute("SELECT pg_advisory_unlock(727274)");
                        }
                    }
                    return null;
                });

                ready = true;
                log.info("schema ready");
            } catch (Exception e) {
                attempt++;
                log.warn("database not ready (attempt {}): {}", attempt, e.getMessage());
                try {
                    Thread.sleep(Math.min(attempt, 5) * 1000L);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }
}
