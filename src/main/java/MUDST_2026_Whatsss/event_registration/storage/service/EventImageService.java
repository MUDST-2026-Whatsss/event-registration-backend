package MUDST_2026_Whatsss.event_registration.storage.service;

import MUDST_2026_Whatsss.event_registration.auth.security.AuthenticatedUser;
import MUDST_2026_Whatsss.event_registration.auth.security.RoleCodes;
import MUDST_2026_Whatsss.event_registration.storage.web.dto.EventImageResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

/** Owns event-image key generation and enforces ownership for destructive operations. */
@Service
@ConditionalOnProperty(name = "event.storage.enabled", havingValue = "true")
public class EventImageService {

    public static final String FILE_NAME_REGEX =
            "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(jpg|png|webp)$";
    public static final String OBJECT_KEY_REGEX =
            "^event-images/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/"
                    + "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(jpg|png|webp)$";

    private static final Pattern FILE_NAME_PATTERN = Pattern.compile(FILE_NAME_REGEX);

    private final ObjectStorage objectStorage;
    private final EventImageValidator validator;
    private final MediaUrlResolver urlResolver;

    public EventImageService(ObjectStorage objectStorage,
                             EventImageValidator validator,
                             MediaUrlResolver urlResolver) {
        this.objectStorage = objectStorage;
        this.validator = validator;
        this.urlResolver = urlResolver;
    }

    public EventImageResponse upload(MultipartFile file, AuthenticatedUser user) {
        ValidatedImage image = validator.validate(file);
        String fileName = UUID.randomUUID().toString().toLowerCase(Locale.ROOT)
                + "." + image.extension();
        String objectKey = objectKey(user.userId(), fileName);
        objectStorage.put(objectKey, image.bytes(), image.contentType());
        return new EventImageResponse(
                objectKey,
                urlResolver.urlFor(objectKey),
                image.contentType(),
                image.bytes().length,
                image.width(),
                image.height());
    }

    public StoredContent load(UUID ownerId, String fileName) {
        return objectStorage.get(objectKey(ownerId, fileName));
    }

    public void delete(UUID ownerId, String fileName, AuthenticatedUser user) {
        if (!ownerId.equals(user.userId()) && !user.hasRole(RoleCodes.SUPER_ADMIN)) {
            throw new AccessDeniedException("Event images may be deleted only by their owner.");
        }
        objectStorage.delete(objectKey(ownerId, fileName));
    }

    private static String objectKey(UUID ownerId, String fileName) {
        if (ownerId == null || fileName == null || !FILE_NAME_PATTERN.matcher(fileName).matches()) {
            throw new MUDST_2026_Whatsss.event_registration.common.error.ApiException(
                    MUDST_2026_Whatsss.event_registration.common.error.ErrorCode.MEDIA_NOT_FOUND);
        }
        return "event-images/" + ownerId + "/" + fileName;
    }
}
