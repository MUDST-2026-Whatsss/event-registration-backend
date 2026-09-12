package MUDST_2026_Whatsss.event_registration.auth.security;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Generates opaque tokens and reduces them to the SHA-256 hex digests stored in PostgreSQL.
 *
 * <p>Refresh tokens are random 256-bit values, not JWTs, so there is nothing to forge and nothing
 * to parse; the only way to use one is to present a value whose digest is already on a live row.
 * Storing just the digest means a database dump cannot be replayed against the API.
 *
 * <p>Fast SHA-256 is the right primitive here, unlike for passwords: the input is full-entropy
 * random, so there is no dictionary for an attacker to grind through and a slow KDF would only
 * cost the server. Predictable values such as login email addresses use a keyed HMAC instead; see
 * {@link IdentifierHasher}.
 */
@Component
public class TokenHasher {

    private static final int TOKEN_BYTES = 32;

    private final SecureRandom secureRandom = new SecureRandom();

    /** A URL-safe, unpadded random token suitable for a cookie value or an email link. */
    public String generateToken() {
        byte[] buffer = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(buffer);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buffer);
    }

    /** Lowercase hex SHA-256, matching the {@code ^[0-9a-f]{64}$} column constraints. */
    public String hash(String rawValue) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(rawValue.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException ex) {
            // SHA-256 is mandated by the JRE spec; absence means a broken runtime.
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }
}
