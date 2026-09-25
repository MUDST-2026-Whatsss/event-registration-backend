package MUDST_2026_Whatsss.event_registration.storage.service;

/** Small provider-neutral boundary so the event domain is not coupled to the MinIO SDK. */
public interface ObjectStorage {

    void put(String objectKey, byte[] content, String contentType);

    StoredContent get(String objectKey);

    void delete(String objectKey);
}
