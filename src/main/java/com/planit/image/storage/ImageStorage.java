package com.planit.image.storage;

public interface ImageStorage {

    void put(
            String key,
            byte[] content,
            String contentType
    );

    String createReadUrl(String key);

    void delete(String key);
}
