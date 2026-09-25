package MUDST_2026_Whatsss.event_registration.storage.service;

import MUDST_2026_Whatsss.event_registration.storage.config.StorageProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** Converts a private object key into the URL returned to browser clients. */
@Component
public class MediaUrlResolver {

    private final String publicBaseUrl;

    public MediaUrlResolver(StorageProperties properties) {
        this.publicBaseUrl = stripTrailingSlash(properties.getPublicBaseUrl());
    }

    public String urlFor(String objectKey) {
        if (!StringUtils.hasText(objectKey)) {
            return null;
        }
        if (StringUtils.hasText(publicBaseUrl)) {
            return publicBaseUrl + "/" + objectKey;
        }
        return "/api/v1/media/" + objectKey;
    }

    private static String stripTrailingSlash(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String result = value.trim();
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }
}
