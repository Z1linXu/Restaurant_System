package com.restaurant.system.auth.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurant.system.auth.service.TokenService;
import com.restaurant.system.common.auth.AuthenticatedUser;
import com.restaurant.system.common.auth.UnauthorizedException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class TokenServiceImpl implements TokenService {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final Base64.Encoder BASE64_URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder BASE64_URL_DECODER = Base64.getUrlDecoder();

    private final ObjectMapper objectMapper;
    private final SecureRandom secureRandom;
    private final String jwtSecret;
    private final long accessTokenExpirationSeconds;

    public TokenServiceImpl(
        ObjectMapper objectMapper,
        @Value("${app.auth.jwt-secret:dev-local-change-me-please-32-bytes-minimum}") String jwtSecret,
        @Value("${app.auth.access-token-expiration-seconds:900}") long accessTokenExpirationSeconds
    ) {
        this.objectMapper = objectMapper;
        this.secureRandom = new SecureRandom();
        this.jwtSecret = jwtSecret;
        this.accessTokenExpirationSeconds = accessTokenExpirationSeconds;
    }

    @Override
    public String createAccessToken(AuthenticatedUser user, Long organizationId) {
        long issuedAt = Instant.now().getEpochSecond();
        long expiresAt = issuedAt + accessTokenExpirationSeconds;
        Map<String, Object> header = Map.of("alg", "HS256", "typ", "JWT");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("user_id", user.userId());
        payload.put("role_id", user.roleId());
        payload.put("store_id", user.storeId());
        payload.put("organization_id", organizationId);
        payload.put("role_code", user.roleCode());
        payload.put("iat", issuedAt);
        payload.put("exp", expiresAt);

        String unsignedToken = encodeJson(header) + "." + encodeJson(payload);
        return unsignedToken + "." + sign(unsignedToken);
    }

    @Override
    public AccessTokenClaims parseAccessToken(String token) {
        if (token == null || token.isBlank()) {
            throw new UnauthorizedException("Bearer token is required");
        }
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            throw new UnauthorizedException("Invalid bearer token");
        }
        String unsignedToken = parts[0] + "." + parts[1];
        String expectedSignature = sign(unsignedToken);
        if (!MessageDigest.isEqual(expectedSignature.getBytes(StandardCharsets.UTF_8), parts[2].getBytes(StandardCharsets.UTF_8))) {
            throw new UnauthorizedException("Invalid bearer token signature");
        }
        Map<String, Object> payload = decodeJson(parts[1]);
        long expiresAt = toLong(payload.get("exp"));
        if (Instant.now().getEpochSecond() >= expiresAt) {
            throw new UnauthorizedException("Bearer token expired");
        }
        return new AccessTokenClaims(
            toLongObject(payload.get("user_id")),
            toLongObject(payload.get("role_id")),
            toLongObject(payload.get("store_id")),
            toLongObject(payload.get("organization_id")),
            String.valueOf(payload.get("role_code")),
            toLong(payload.get("iat")),
            expiresAt
        );
    }

    @Override
    public String generateRefreshToken() {
        byte[] bytes = new byte[48];
        secureRandom.nextBytes(bytes);
        return BASE64_URL_ENCODER.encodeToString(bytes);
    }

    @Override
    public String hashRefreshToken(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new UnauthorizedException("Refresh token is required");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(refreshToken.getBytes(StandardCharsets.UTF_8));
            return BASE64_URL_ENCODER.encodeToString(hash);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to hash refresh token", exception);
        }
    }

    @Override
    public long getAccessTokenExpirationSeconds() {
        return accessTokenExpirationSeconds;
    }

    private String encodeJson(Map<String, Object> value) {
        try {
            return BASE64_URL_ENCODER.encodeToString(objectMapper.writeValueAsBytes(value));
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to encode JWT", exception);
        }
    }

    private Map<String, Object> decodeJson(String encodedJson) {
        try {
            byte[] bytes = BASE64_URL_DECODER.decode(encodedJson);
            return objectMapper.readValue(bytes, new TypeReference<>() {});
        } catch (Exception exception) {
            throw new UnauthorizedException("Invalid bearer token payload");
        }
    }

    private String sign(String value) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(jwtSecret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            return BASE64_URL_ENCODER.encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to sign JWT", exception);
        }
    }

    private long toLong(Object value) {
        Long result = toLongObject(value);
        if (result == null) {
            throw new UnauthorizedException("Invalid bearer token claim");
        }
        return result;
    }

    private Long toLongObject(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.valueOf(String.valueOf(value));
    }
}
