package com.planit.chat.websocket.message;

import com.planit.chat.dto.ChatMessageSendRequest;
import com.planit.chat.service.ChatMessageSendResult;
import com.planit.chat.service.ChatMessageSendService;
import com.planit.global.error.BusinessException;
import com.planit.global.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.security.Principal;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RegionalChatMessageControllerTest {

    private static final String USER_PUBLIC_ID =
            "01991f6e-7300-7b21-a3cc-1436db3df95e";
    private static final Long ROOM_ID = 3001L;
    private static final String SESSION_ID = "session-1";

    @DisplayName("메시지 처리 성공 결과를 요청 세션에 전송한다")
    @Test
    void publishesAcceptedResultAfterServiceReturns() {
        ChatMessageSendService service = mock(ChatMessageSendService.class);
        ChatMessageEventPublisher publisher = mock(ChatMessageEventPublisher.class);
        RegionalChatMessageController controller = new RegionalChatMessageController(
                service,
                publisher
        );
        Principal principal = () -> USER_PUBLIC_ID;
        ChatMessageSendRequest request = request();
        ChatMessageSendResult result = mock(ChatMessageSendResult.class);
        when(service.sendTextMessage(USER_PUBLIC_ID, ROOM_ID, request))
                .thenReturn(result);

        controller.sendMessage(principal, SESSION_ID, ROOM_ID, request);

        verify(publisher).publishAccepted(
                USER_PUBLIC_ID,
                SESSION_ID,
                ROOM_ID,
                result
        );
    }

    @DisplayName("메시지 처리 중 발생한 비즈니스 오류를 요청 세션에 전송한다")
    @Test
    void publishesBusinessFailureToRequestSession() {
        ChatMessageSendService service = mock(ChatMessageSendService.class);
        ChatMessageEventPublisher publisher = mock(ChatMessageEventPublisher.class);
        RegionalChatMessageController controller = new RegionalChatMessageController(
                service,
                publisher
        );
        Principal principal = () -> USER_PUBLIC_ID;
        ChatMessageSendRequest request = request();
        when(service.sendTextMessage(USER_PUBLIC_ID, ROOM_ID, request))
                .thenThrow(new BusinessException(
                        ErrorCode.REGIONAL_CHAT_MEMBER_REQUIRED
                ));

        controller.sendMessage(principal, SESSION_ID, ROOM_ID, request);

        verify(publisher).publishRejected(
                USER_PUBLIC_ID,
                SESSION_ID,
                ROOM_ID,
                request.clientMessageId().toString(),
                ErrorCode.REGIONAL_CHAT_MEMBER_REQUIRED
        );
    }

    private ChatMessageSendRequest request() {
        return new ChatMessageSendRequest(
                UUID.fromString("019b1234-5678-7000-8000-123456789abc"),
                "TEXT",
                "메시지",
                null
        );
    }
}
