package com.example.seats.service;

import com.example.seats.exception.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Map;
import java.util.regex.Pattern;

@Component
public class AuthService {
    public static final long TOKEN_TTL_SECONDS = 7L * 24 * 3600;
    public static final Pattern USER_ID = Pattern.compile("^[A-Za-z0-9_.@:-]{1,128}$");

    public record Principal(String userId, boolean admin) {}

    private final byte[] secret;
    private final byte[] adminToken;
    private final ObjectMapper mapper;

    public AuthService(@Value("${app.jwt-secret}") String secret,
                       @Value("${app.admin-token}") String adminToken,
                       ObjectMapper mapper) {
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.adminToken = adminToken.getBytes(StandardCharsets.UTF_8);
        this.mapper = mapper;
    }

    private static String b64(byte[] b) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    private String sign(String signingInput) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret, "HmacSHA256"));
        return b64(mac.doFinal(signingInput.getBytes(StandardCharsets.US_ASCII)));
    }

    public String makeToken(String userId) {
        try {
            String header = b64("{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
            long exp = System.currentTimeMillis() / 1000 + TOKEN_TTL_SECONDS;
            String payload = b64(mapper.writeValueAsBytes(Map.of("sub", userId, "exp", exp)));
            return header + "." + payload + "." + sign(header + "." + payload);
        } catch (Exception e) {
            throw new IllegalStateException("could not sign token", e);
        }
    }

    private String verify(String token) {
        try {
            String[] parts = token.split("\\.", -1);
            if (parts.length != 3) return null;
            JsonNode header = mapper.readTree(Base64.getUrlDecoder().decode(parts[0]));
            if (!"HS256".equals(header.path("alg").asText())) return null;
            String expected = sign(parts[0] + "." + parts[1]);
            if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII),
                    parts[2].getBytes(StandardCharsets.US_ASCII))) {
                return null;
            }
            JsonNode payload = mapper.readTree(Base64.getUrlDecoder().decode(parts[1]));
            if (payload.path("exp").asLong(0) < System.currentTimeMillis() / 1000) return null;
            JsonNode sub = payload.get("sub");
            if (sub == null || !sub.isTextual() || !USER_ID.matcher(sub.asText()).matches()) return null;
            return sub.asText();
        } catch (Exception e) {
            return null;
        }
    }

    public Principal principal(String header) {
        if (header == null) throw new ApiException(401, "unauthorized", "missing or malformed Authorization header");
        int space = header.indexOf(' ');
        if (space < 0 || !header.substring(0, space).equalsIgnoreCase("bearer")
                || header.substring(space + 1).isBlank()) {
            throw new ApiException(401, "unauthorized", "missing or malformed Authorization header");
        }
        String token = header.substring(space + 1).strip();
        if (MessageDigest.isEqual(token.getBytes(StandardCharsets.UTF_8), adminToken)) {
            return new Principal(null, true);
        }
        String userId = verify(token);
        if (userId == null) throw new ApiException(401, "unauthorized", "invalid or expired token");
        return new Principal(userId, false);
    }

    public String requireUser(String authorizationHeader) {
        Principal principal = principal(authorizationHeader);
        if (principal.admin()) throw new ApiException(403, "user_token_required", "this endpoint needs a user token");
        return principal.userId();
    }

    public void requireAdmin(String authorizationHeader) {
        if (!principal(authorizationHeader).admin()) throw new ApiException(403, "admin_required", "admin token required");
    }
}
