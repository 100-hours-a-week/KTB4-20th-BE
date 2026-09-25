package com.planit.chat.websocket.subscription;

import com.planit.chat.presence.RegionalChatRoomPresenceRegistry;
import com.planit.global.error.ErrorCode;
import com.planit.repository.RegionalChatRoomMemberRepository;
import com.planit.repository.RegionalChatRoomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class RegionalChatRoomSubscriptionChannelInterceptor implements ChannelInterceptor {

    private static final Pattern ROOM_DESTINATION_PATTERN = Pattern.compile(
            "^/topic/regional-chat-rooms/(\\d+)/messages$"
    );
    private static final String ROOM_DESTINATION_PREFIX = "/topic/regional-chat-rooms/";
    private static final String APPROVED_ROOM_ID_HEADER =
            "planitApprovedRegionalChatRoomId";

    private final RegionalChatRoomRepository roomRepository;
    private final RegionalChatRoomMemberRepository memberRepository;
    private final RegionalChatRoomPresenceRegistry presenceRegistry;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) { //STOMP 메세지 헤더를 통해 구독/구독해제 동작을 구분하여 수행
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(
                message,
                StompHeaderAccessor.class
        );
        if (accessor == null) {
            return message;
        }

        if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            return handleSubscribe(message, accessor);
        }
        if (StompCommand.UNSUBSCRIBE.equals(accessor.getCommand())) {
            presenceRegistry.unregisterSubscription(
                    accessor.getSessionId(),
                    accessor.getSubscriptionId()
            );
        }
        return message;
    }

    @Override
    public void postSend(Message<?> message, MessageChannel channel, boolean sent) { //STOMP 메세지 헤더를 통해 SUBSCRIBE 관련 동작을 처리, 서버 내부적으로 메세지 전송 작업이 성공적으로 완료되었는지를 나타냄
        if (!sent) {
            return;
        }
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(
                message,
                StompHeaderAccessor.class
        );
        if (accessor == null || !StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            return;
        }

        Long roomId = (Long) accessor.getHeader(APPROVED_ROOM_ID_HEADER);
        if (roomId == null) {
            return;
        }
        presenceRegistry.register(
                roomId,
                accessor.getUser().getName(),
                accessor.getSessionId(),
                accessor.getSubscriptionId()
        );
        if (accessor.getReceipt() != null) {
            eventPublisher.publishEvent(new ChatSubscriptionReceiptEvent(
                    accessor.getSessionId(),
                    accessor.getReceipt()
            ));
        }
    }

    private Message<?> handleSubscribe(Message<?> message, StompHeaderAccessor accessor) {  //구독 관련 동작 처리
        String destination = accessor.getDestination() == null ? "" : accessor.getDestination();
        Matcher matcher = ROOM_DESTINATION_PATTERN.matcher(
                destination
        );
        if (!matcher.matches()) {
            if (destination.startsWith(ROOM_DESTINATION_PREFIX)) {
                throw new ChatSubscriptionException(ErrorCode.INVALID_REQUEST);
            }
            return message;
        }

        String sessionId = accessor.getSessionId();
        String subscriptionId = accessor.getSubscriptionId();
        Long roomId;
        try {
            roomId = Long.parseLong(matcher.group(1));
        } catch (NumberFormatException exception) {             //roomId 형식이 맞지 않을 경우 실패처리
            throw new ChatSubscriptionException(ErrorCode.INVALID_REQUEST);
        }

        if (sessionId == null || subscriptionId == null) {      //세션이나 구독 id값이 없을 경우 실패처리
            throw new ChatSubscriptionException(ErrorCode.INVALID_REQUEST);
        }

        if (!roomRepository.existsById(roomId)) {               //해당 채팅방이 존재하지 않을 경우 실패처리
            throw new ChatSubscriptionException(ErrorCode.REGIONAL_CHAT_ROOM_NOT_FOUND);
        }

        if (!hasActiveMembership(accessor.getUser().getName(), roomId)) {     //해당 채팅방에 참여하지 않은 경우 실패처리
            throw new ChatSubscriptionException(ErrorCode.REGIONAL_CHAT_MEMBER_REQUIRED);
        }

        accessor.setHeader(APPROVED_ROOM_ID_HEADER, roomId);
        return message;
    }

    private boolean hasActiveMembership(String userPublicId, Long roomId) {   // 채팅방 정상 구독 처리
        try {
            return memberRepository.existsActiveMembership(
                    UUID.fromString(userPublicId),
                    roomId
            );
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

}
