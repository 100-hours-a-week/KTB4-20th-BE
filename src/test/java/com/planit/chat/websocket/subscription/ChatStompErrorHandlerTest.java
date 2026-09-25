package com.planit.chat.websocket.subscription;

import com.planit.chat.error.ChatAuthenticationException;
import com.planit.chat.error.ChatErrorCode;
import com.planit.global.error.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

class ChatStompErrorHandlerTest {

    private final JsonMapper objectMapper = JsonMapper.builder().build();
    private final ChatStompErrorHandler errorHandler =
            new ChatStompErrorHandler(objectMapper);

    @Test
    void convertsSubscriptionFailureToErrorFrameAndPreservesReceipt() throws Exception {
        StompHeaderAccessor clientAccessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        clientAccessor.setReceipt("subscribe-3001");
        clientAccessor.setLeaveMutable(true);
        Message<byte[]> clientMessage = MessageBuilder.createMessage(
                new byte[0],
                clientAccessor.getMessageHeaders()
        );
        ChatSubscriptionException cause = new ChatSubscriptionException(
                ErrorCode.REGIONAL_CHAT_MEMBER_REQUIRED
        );

        Message<byte[]> result = errorHandler.handleClientMessageProcessingError(
                clientMessage,
                new MessageDeliveryException(clientMessage, "구독 처리 실패", cause)
        );

        StompHeaderAccessor resultAccessor = StompHeaderAccessor.wrap(result);
        JsonNode payload = objectMapper.readTree(result.getPayload());
        assertThat(resultAccessor.getCommand()).isEqualTo(StompCommand.ERROR);
        assertThat(resultAccessor.getReceiptId()).isEqualTo("subscribe-3001");
        assertThat(resultAccessor.getMessage())
                .isEqualTo("REGIONAL_CHAT_MEMBER_REQUIRED");
        assertThat(payload.get("code").asText())
                .isEqualTo("REGIONAL_CHAT_MEMBER_REQUIRED");
        assertThat(payload.get("message").asText())
                .isEqualTo(ErrorCode.REGIONAL_CHAT_MEMBER_REQUIRED.getMessage());
    }

    @Test
    void convertsAuthenticationFailureToErrorFrame() throws Exception {
        StompHeaderAccessor clientAccessor = StompHeaderAccessor.create(
                StompCommand.CONNECT
        );
        clientAccessor.setLeaveMutable(true);
        Message<byte[]> clientMessage = MessageBuilder.createMessage(
                new byte[0],
                clientAccessor.getMessageHeaders()
        );
        ChatAuthenticationException cause = new ChatAuthenticationException(
                ChatErrorCode.INVALID_ACCESS_TOKEN
        );

        Message<byte[]> result = errorHandler.handleClientMessageProcessingError(
                clientMessage,
                new MessageDeliveryException(clientMessage, "인증 처리 실패", cause)
        );

        StompHeaderAccessor resultAccessor = StompHeaderAccessor.wrap(result);
        JsonNode payload = objectMapper.readTree(result.getPayload());
        assertThat(resultAccessor.getCommand()).isEqualTo(StompCommand.ERROR);
        assertThat(resultAccessor.getMessage()).isEqualTo("INVALID_ACCESS_TOKEN");
        assertThat(payload.get("code").asText()).isEqualTo("INVALID_ACCESS_TOKEN");
        assertThat(payload.get("message").asText())
                .isEqualTo(ChatErrorCode.INVALID_ACCESS_TOKEN.getMessage());
    }
}
