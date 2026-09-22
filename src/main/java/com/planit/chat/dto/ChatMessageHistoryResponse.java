package com.planit.chat.dto;

import java.time.Instant;
import java.util.List;

public record ChatMessageHistoryResponse(
        List<ChatMessageItemResponse> items,
        ChatMessagePageResponse page
) {

    public ChatMessageHistoryResponse {
        items = List.copyOf(items);
    }

    public record ChatMessageItemResponse(
            String messageId,
            String clientMessageId,
            String messageType,
            String text,
            ChatImageResponse image,
            ChatSenderResponse sender,
            Instant createdAt
    ) {
    }

    public record ChatImageResponse(
            String imageFileId,
            String url,
            String thumbnailUrl,
            String mimeType
    ) {
    }

    public record ChatSenderResponse(
            String publicId,
            String userName,
            String profileImageUrl
    ) {
    }

    public record ChatMessagePageResponse(
            String nextCursor,
            String nextAfterMessageId,
            boolean hasNext
    ) {
    }
}
