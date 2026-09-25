package MUDST_2026_Whatsss.event_registration.storage.service;

/** Image bytes and server-derived metadata after validation. */
record ValidatedImage(
        byte[] bytes,
        String contentType,
        String extension,
        int width,
        int height) {
}
