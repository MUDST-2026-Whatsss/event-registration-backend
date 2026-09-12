package MUDST_2026_Whatsss.event_registration.auth.security;

import MUDST_2026_Whatsss.event_registration.auth.config.AuthProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Issues and verifies the HS256 access token.
 *
 * <p>The token carries the user id as subject plus the primary role, all role codes and all
 * permission codes, so an authenticated request needs no database round trip to be authorized.
 * The trade-off is that the claims are a snapshot: a role revoked mid-token stays effective until
 * the token expires. Anything that must take effect immediately has to be checked against the
 * database in the service layer rather than read from the JWT.
 */
@Service
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);

    public static final String CLAIM_ROLE = "role";
    public static final String CLAIM_ROLES = "roles";
    public static final String CLAIM_PERMISSIONS = "permissions";
    public static final String CLAIM_EMAIL = "email";

    /** HS256 requires a key of at least 256 bits; a shorter secret is a configuration error. */
    private static final int MIN_KEY_BYTES = 32;

    private final AuthProperties properties;
    private final SecretKey signingKey;

    public JwtService(AuthProperties properties) {
        this.properties = properties;
        this.signingKey = buildSigningKey(properties.getJwtSecret());
    }

    private static SecretKey buildSigningKey(String configuredSecret) {
        byte[] keyBytes;
        try {
            // Accept a base64 secret (what `openssl rand -base64 48` produces) and fall back to
            // treating the value as raw text.
            keyBytes = Base64.getDecoder().decode(configuredSecret);
        } catch (IllegalArgumentException ex) {
            keyBytes = configuredSecret.getBytes(StandardCharsets.UTF_8);
        }
        if (keyBytes.length < MIN_KEY_BYTES) {
            throw new IllegalStateException(
                    "event.auth.jwt-secret must decode to at least " + MIN_KEY_BYTES
                            + " bytes for HS256; generate one with: openssl rand -base64 48");
        }
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public String issueAccessToken(UUID userId,
                                   String email,
                                   String primaryRole,
                                   List<String> roles,
                                   List<String> permissions) {
        Instant now = Instant.now();
        Instant expiry = now.plus(properties.getAccessTokenTtl());

        return Jwts.builder()
                .issuer(properties.getJwtIssuer())
                .subject(userId.toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .id(UUID.randomUUID().toString())
                .claim(CLAIM_EMAIL, email)
                .claim(CLAIM_ROLE, primaryRole)
                .claim(CLAIM_ROLES, roles)
                .claim(CLAIM_PERMISSIONS, permissions)
                .signWith(signingKey)
                .compact();
    }

    /**
     * Verifies signature, issuer and expiry.
     *
     * @return the claims, or empty when the token is absent, tampered with, or expired
     */
    public Optional<Claims> parse(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .requireIssuer(properties.getJwtIssuer())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return Optional.of(claims);
        } catch (JwtException | IllegalArgumentException ex) {
            // Expected for expired or forged tokens; never surface the reason to the caller.
            log.debug("Rejected access token: {}", ex.getMessage());
            return Optional.empty();
        }
    }

}
