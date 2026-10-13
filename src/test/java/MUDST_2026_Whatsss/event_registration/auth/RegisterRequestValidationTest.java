package MUDST_2026_Whatsss.event_registration.auth;

import MUDST_2026_Whatsss.event_registration.auth.web.dto.RegisterRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegisterRequestValidationTest {

    private static jakarta.validation.ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        validatorFactory.close();
    }

    @Test
    void acceptsValidRegistrationFields() {
        var request = new RegisterRequest(
                "person@example.com", "StrongPass1", "สมชาย", "O'Connor", "081-234-5678");

        assertTrue(validator.validate(request).isEmpty());
    }

    @Test
    void rejectsBlankOrMalformedPhoneNumbers() {
        var blankPhone = new RegisterRequest(
                "person@example.com", "StrongPass1", "First", "Last", "");
        var malformedPhone = new RegisterRequest(
                "person@example.com", "StrongPass1", "First", "Last", "0--------");

        assertFalse(validator.validate(blankPhone).isEmpty());
        assertFalse(validator.validate(malformedPhone).isEmpty());
    }

    @Test
    void rejectsValuesOverTheirLimits() {
        var request = new RegisterRequest(
                "a".repeat(310) + "@example.com",
                "Aa1" + "x".repeat(126),
                "a".repeat(101),
                "b".repeat(101),
                "0812345678");

        assertFalse(validator.validate(request).isEmpty());
    }
}
