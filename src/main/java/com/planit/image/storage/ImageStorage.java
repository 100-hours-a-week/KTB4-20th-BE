package com.planit.image.storage;

import java.time.Duration;

public interface ImageStorage {

    void put(
            String key,
            byte[] content,
            String contentType
    );

    String createReadUrl(String key);

    String createReadUrl(String key, Duration signatureDuration);

    void delete(String key);
}
