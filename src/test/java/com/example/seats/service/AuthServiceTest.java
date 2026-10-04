package com.example.seats.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class AuthServiceTest {

    @Test
    void makeToken_shouldReturnValidUserPrincipal() {
        AuthService auth = new AuthService("dev-secret-change-me", "admin-dev-token", new ObjectMapper());

        String token = auth.makeToken("user-42");
        assertNotNull(token);
        assertEquals("user-42", auth.requireUser("Bearer " + token));
    }
}
