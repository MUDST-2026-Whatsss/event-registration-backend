package MUDST_2026_Whatsss.event_registration.auth.security;

import MUDST_2026_Whatsss.event_registration.auth.config.AuthProperties;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * Pseudonymizes predictable login identifiers with a keyed HMAC.
 *
 * <p>A plain SHA-256 digest is safe for random refresh tokens but not for email addresses, which
 * can be recovered with a dictionary. This component derives a purpose-specific key from the JWT
 * secret so a database-only leak is not enough to test guesses offline.
 */
@Component
public class IdentifierHasher {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final byte[] DOMAIN =
            "event-registration/login-identifier/v1\0".getBytes(StandardCharsets.UTF_8);

    private final SecretKeySpec key;

    public IdentifierHasher(AuthProperties properties) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(DOMAIN);
            digest.update(properties.getJwtSecret().getBytes(StandardCharsets.UTF_8));
            this.key = new SecretKeySpec(digest.digest(), HMAC_ALGORITHM);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    public String hash(String normalizedIdentifier) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(key);
            return HexFormat.of().formatHex(
                    mac.doFinal(normalizedIdentifier.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("HmacSHA256 is unavailable", ex);
        }
    }
}
