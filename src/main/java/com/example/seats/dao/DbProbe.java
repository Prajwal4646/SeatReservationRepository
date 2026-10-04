package com.example.seats.dao;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class DbProbe {
    private final JdbcTemplate jdbc;

    public DbProbe(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean ready() {
        try {
            Integer value = jdbc.queryForObject("SELECT 1", Integer.class);
            return value != null && value == 1;
        } catch (Exception e) {
            return false;
        }
    }
}
