package com.planit.chat.websocket.subscription;

public record ChatSubscriptionReceiptEvent(
        String sessionId,
        String receiptId
) {
}
