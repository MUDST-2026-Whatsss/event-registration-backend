package MUDST_2026_Whatsss.event_registration.storage.service;

import MUDST_2026_Whatsss.event_registration.common.error.ApiException;
import MUDST_2026_Whatsss.event_registration.common.error.ErrorCode;
import MUDST_2026_Whatsss.event_registration.storage.config.StorageProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Iterator;

/** Validates image bytes without trusting the browser filename or Content-Type header. */
@Component
public class EventImageValidator {

    private static final byte[] PNG_SIGNATURE = {
            (byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a
    };

    private final long maximumBytes;
    private final int maximumWidth;
    private final int maximumHeight;

    public EventImageValidator(StorageProperties properties) {
        this.maximumBytes = properties.getMaxImageSize().toBytes();
        this.maximumWidth = properties.getMaxImageWidth();
        this.maximumHeight = properties.getMaxImageHeight();
    }

    ValidatedImage validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw invalid("Select a non-empty JPEG, PNG, or WebP image.");
        }
        if (file.getSize() > maximumBytes) {
            throw new ApiException(ErrorCode.PAYLOAD_TOO_LARGE);
        }

        final byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException ex) {
            throw invalid("The image could not be read.");
        }
        if (bytes.length == 0 || bytes.length > maximumBytes) {
            throw new ApiException(bytes.length == 0
                    ? ErrorCode.INVALID_IMAGE_FILE
                    : ErrorCode.PAYLOAD_TOO_LARGE);
        }

        ImageFormat format = detectFormat(bytes);
        Dimensions dimensions = format == ImageFormat.WEBP
                ? readWebpDimensions(bytes)
                : readImageIoDimensions(bytes);

        if (dimensions.width() < 1 || dimensions.height() < 1
                || dimensions.width() > maximumWidth || dimensions.height() > maximumHeight) {
            throw invalid("Image dimensions exceed the configured limit.");
        }

        return new ValidatedImage(bytes, format.contentType, format.extension,
                dimensions.width(), dimensions.height());
    }

    private static ImageFormat detectFormat(byte[] bytes) {
        if (startsWith(bytes, PNG_SIGNATURE)) {
            return ImageFormat.PNG;
        }
        if (bytes.length >= 3
                && unsigned(bytes[0]) == 0xff
                && unsigned(bytes[1]) == 0xd8
                && unsigned(bytes[2]) == 0xff) {
            return ImageFormat.JPEG;
        }
        if (bytes.length >= 30
                && ascii(bytes, 0, "RIFF")
                && ascii(bytes, 8, "WEBP")) {
            return ImageFormat.WEBP;
        }
        throw invalid("Only JPEG, PNG, and WebP images are accepted.");
    }

    private static Dimensions readImageIoDimensions(byte[] bytes) {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            if (input == null) {
                throw invalid("The image could not be decoded.");
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw invalid("The image could not be decoded.");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                return new Dimensions(reader.getWidth(0), reader.getHeight(0));
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException ex) {
            if (ex instanceof ApiException apiException) {
                throw apiException;
            }
            throw invalid("The image could not be decoded.");
        }
    }

    /** Reads dimensions from the three WebP bitstream variants without decoding all pixels. */
    private static Dimensions readWebpDimensions(byte[] bytes) {
        if (ascii(bytes, 12, "VP8X") && bytes.length >= 30) {
            return new Dimensions(1 + littleEndian24(bytes, 24), 1 + littleEndian24(bytes, 27));
        }
        if (ascii(bytes, 12, "VP8 ") && bytes.length >= 30
                && unsigned(bytes[23]) == 0x9d
                && unsigned(bytes[24]) == 0x01
                && unsigned(bytes[25]) == 0x2a) {
            int width = littleEndian16(bytes, 26) & 0x3fff;
            int height = littleEndian16(bytes, 28) & 0x3fff;
            return new Dimensions(width, height);
        }
        if (ascii(bytes, 12, "VP8L") && bytes.length >= 25 && unsigned(bytes[20]) == 0x2f) {
            int b21 = unsigned(bytes[21]);
            int b22 = unsigned(bytes[22]);
            int b23 = unsigned(bytes[23]);
            int b24 = unsigned(bytes[24]);
            int width = 1 + b21 + ((b22 & 0x3f) << 8);
            int height = 1 + ((b22 & 0xc0) >> 6) + (b23 << 2) + ((b24 & 0x0f) << 10);
            return new Dimensions(width, height);
        }
        throw invalid("The WebP image header is invalid.");
    }

    private static boolean startsWith(byte[] bytes, byte[] prefix) {
        if (bytes.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (bytes[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    private static boolean ascii(byte[] bytes, int offset, String value) {
        if (bytes.length < offset + value.length()) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            if (unsigned(bytes[offset + i]) != value.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    private static int littleEndian16(byte[] bytes, int offset) {
        return unsigned(bytes[offset]) | (unsigned(bytes[offset + 1]) << 8);
    }

    private static int littleEndian24(byte[] bytes, int offset) {
        return unsigned(bytes[offset])
                | (unsigned(bytes[offset + 1]) << 8)
                | (unsigned(bytes[offset + 2]) << 16);
    }

    private static int unsigned(byte value) {
        return value & 0xff;
    }

    private static ApiException invalid(String message) {
        return new ApiException(ErrorCode.INVALID_IMAGE_FILE, message);
    }

    private enum ImageFormat {
        JPEG("image/jpeg", "jpg"),
        PNG("image/png", "png"),
        WEBP("image/webp", "webp");

        private final String contentType;
        private final String extension;

        ImageFormat(String contentType, String extension) {
            this.contentType = contentType;
            this.extension = extension;
        }
    }

    private record Dimensions(int width, int height) {
    }
}
