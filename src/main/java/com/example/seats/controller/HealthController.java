package com.example.seats.controller;

import com.example.seats.dao.DbProbe;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class HealthController {
    private final DbProbe dbProbe;
    private final PrometheusMeterRegistry registry;

    public HealthController(DbProbe dbProbe, PrometheusMeterRegistry registry) {
        this.dbProbe = dbProbe;
        this.registry = registry;
    }

    @GetMapping("/")
    public Map<String, String> root() {
        return Map.of("status", "ok", "app", "seat-reservation-app");
    }

    @GetMapping("/healthz")
    public Map<String, String> healthz() {
        return Map.of("status", "ok");
    }

    @GetMapping("/readyz")
    public ResponseEntity<Map<String, Object>> readyz() {
        if (dbProbe.ready()) {
            return ResponseEntity.ok(Map.of("status", "ready"));
        }
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("status", "not_ready", "message", "database unavailable"));
    }

    @GetMapping(value = "/metrics", produces = "text/plain;version=0.0.4;charset=utf-8")
    public String metrics() {
        return registry.scrape();
    }
}
