package com.example.seats.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DbConfigTest {

    @Test
    void parse_shouldConvertPostgresUrlToJdbcUrl() {
        DbConfig.DbSettings settings = DbConfig.parse("postgresql://user:pass@localhost:5432/seats");

        assertEquals("jdbc:postgresql://localhost:5432/seats", settings.jdbcUrl());
        assertEquals("user", settings.user());
        assertEquals("pass", settings.password());
    }
}
