package com.example.seats.model;

public record IdempotencyRecord(String userId, String idempotencyKey, String requestHash, String reservationId) {
}
