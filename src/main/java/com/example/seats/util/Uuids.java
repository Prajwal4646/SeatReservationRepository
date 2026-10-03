package com.example.seats.util;

import java.util.UUID;

public final class Uuids {
    private Uuids() {}

    public static UUID tryParse(String value) {
        try {
            return UUID.fromString(value);
        } catch (Exception e) {
            return null;
        }
    }
}
