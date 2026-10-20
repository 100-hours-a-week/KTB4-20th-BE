package com.planit.websocket.subscription;

public record StompSubscriptionReceiptEvent(
        String sessionId,
        String receiptId
) {
}
