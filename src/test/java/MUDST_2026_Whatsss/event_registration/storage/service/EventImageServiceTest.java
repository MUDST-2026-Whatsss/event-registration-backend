package MUDST_2026_Whatsss.event_registration.storage.service;

import MUDST_2026_Whatsss.event_registration.auth.security.AuthenticatedUser;
import MUDST_2026_Whatsss.event_registration.common.error.ApiException;
import MUDST_2026_Whatsss.event_registration.common.error.ErrorCode;
import MUDST_2026_Whatsss.event_registration.storage.config.StorageProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.util.unit.DataSize;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class EventImageServiceTest {

    @Test
    void uploadsValidatedImageUnderAuthenticatedOwnerPrefix() throws IOException {
        InMemoryObjectStorage storage = new InMemoryObjectStorage();
        EventImageService service = service(storage, defaultProperties());
        UUID ownerId = UUID.randomUUID();

        var response = service.upload(pngFile(4, 3), admin(ownerId));

        assertThat(response.objectKey()).startsWith("event-images/" + ownerId + "/").endsWith(".png");
        assertThat(response.imageUrl()).isEqualTo("/api/v1/media/" + response.objectKey());
        assertThat(response.contentType()).isEqualTo("image/png");
        assertThat(response.width()).isEqualTo(4);
        assertThat(response.height()).isEqualTo(3);
        assertThat(storage.content).containsKey(response.objectKey());
    }

    @Test
    void rejectsAFileWhoseClaimedContentTypeDoesNotMatchItsBytes() {
        EventImageService service = service(new InMemoryObjectStorage(), defaultProperties());
        MockMultipartFile fake = new MockMultipartFile(
                "file", "fake.png", "image/png", "not an image".getBytes());

        assertThatExceptionOfType(ApiException.class)
                .isThrownBy(() -> service.upload(fake, admin(UUID.randomUUID())))
                .satisfies(ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.INVALID_IMAGE_FILE));
    }

    @Test
    void rejectsImagesOverConfiguredDimensions() throws IOException {
        StorageProperties properties = defaultProperties();
        properties.setMaxImageWidth(2);
        EventImageService service = service(new InMemoryObjectStorage(), properties);

        assertThatExceptionOfType(ApiException.class)
                .isThrownBy(() -> service.upload(pngFile(3, 2), admin(UUID.randomUUID())))
                .satisfies(ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.INVALID_IMAGE_FILE));
    }

    @Test
    void preventsAnAdminFromDeletingAnotherAdminsImage() {
        EventImageService service = service(new InMemoryObjectStorage(), defaultProperties());
        UUID ownerId = UUID.randomUUID();
        String fileName = UUID.randomUUID() + ".webp";

        assertThatExceptionOfType(AccessDeniedException.class)
                .isThrownBy(() -> service.delete(ownerId, fileName, admin(UUID.randomUUID())));
    }

    private static EventImageService service(ObjectStorage storage, StorageProperties properties) {
        return new EventImageService(
                storage,
                new EventImageValidator(properties),
                new MediaUrlResolver(properties));
    }

    private static StorageProperties defaultProperties() {
        StorageProperties properties = new StorageProperties();
        properties.setMaxImageSize(DataSize.ofMegabytes(2));
        properties.setMaxImageWidth(6000);
        properties.setMaxImageHeight(6000);
        return properties;
    }

    private static AuthenticatedUser admin(UUID userId) {
        return new AuthenticatedUser(userId, "admin@example.test", "ADMIN", List.of("ADMIN"), List.of());
    }

    private static MockMultipartFile pngFile(int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return new MockMultipartFile("file", "cover.png", "image/png", output.toByteArray());
    }

    private static final class InMemoryObjectStorage implements ObjectStorage {

        private final Map<String, StoredContent> content = new HashMap<>();

        @Override
        public void put(String objectKey, byte[] bytes, String contentType) {
            content.put(objectKey, new StoredContent(bytes, contentType));
        }

        @Override
        public StoredContent get(String objectKey) {
            return content.get(objectKey);
        }

        @Override
        public void delete(String objectKey) {
            content.remove(objectKey);
        }
    }
}
