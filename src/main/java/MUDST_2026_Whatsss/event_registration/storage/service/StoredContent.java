package MUDST_2026_Whatsss.event_registration.storage.service;

/** Immutable content returned from the private object-storage bucket. */
public record StoredContent(byte[] bytes, String contentType) {
}
