package com.planit.chat.service;

import com.planit.chat.pagination.ChatMessageCursorStore;
import com.planit.domain.ChatMessage;
import com.planit.domain.ChatMessageStatus;
import com.planit.domain.ChatMessageType;
import com.planit.global.error.BusinessException;
import com.planit.global.error.ErrorCode;
import com.planit.image.config.ImageProperties;
import com.planit.repository.ChatMessageRepository;
import com.planit.repository.ChatMessageRepository.ChatMessageHistoryProjection;
import com.planit.repository.RegionalChatRoomMemberRepository;
import com.planit.repository.RegionalChatRoomRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

import java.net.URI;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ChatMessageHistoryServiceImplTest {

    private static final String USER_PUBLIC_ID = "01991f6e-7300-7b21-a3cc-1436db3df95e";
    private static final UUID USER_UUID = UUID.fromString(USER_PUBLIC_ID);
    private static final Long ROOM_ID = 3001L;

    private RegionalChatRoomRepository roomRepository;
    private RegionalChatRoomMemberRepository memberRepository;
    private ChatMessageRepository messageRepository;
    private ChatMessageHistoryServiceImpl service;

    @BeforeEach
    void setUp() {
        roomRepository = mock(RegionalChatRoomRepository.class);
        memberRepository = mock(RegionalChatRoomMemberRepository.class);
        messageRepository = mock(ChatMessageRepository.class);
        service = new ChatMessageHistoryServiceImpl(
                roomRepository,
                memberRepository,
                messageRepository,
                new ChatMessageCursorStore(),
                new ImageProperties(URI.create(
                        "http://localhost:8080/images/default-profile.svg"
                ))
        );
        when(roomRepository.existsById(ROOM_ID)).thenReturn(true);
        when(memberRepository.existsActiveMembership(USER_UUID, ROOM_ID)).thenReturn(true);
    }

    @Test
    void returnsLatestMessagesInAscendingDisplayOrderWithOlderCursor() {
        List<ChatMessageHistoryProjection> descending = new ArrayList<>();
        for (long id = 21; id >= 1; id--) {
            descending.add(projection(id));
        }
        when(messageRepository.findLatestHistory(
                eq(ROOM_ID),
                eq(ChatMessageStatus.VISIBLE),
                any(Pageable.class)
        )).thenReturn(descending);

        var response = service.getMessages(USER_PUBLIC_ID, ROOM_ID, null, null);

        assertThat(response.items()).hasSize(20);
        assertThat(response.items().get(0).messageId()).isEqualTo("2");
        assertThat(response.items().get(19).messageId()).isEqualTo("21");
        assertThat(response.page().hasNext()).isTrue();
        assertThat(response.page().nextCursor()).isNotBlank();
        assertThat(response.page().nextAfterMessageId()).isNull();
    }

    @Test
    void returnsNewerMessagesAndNextAfterMessageId() {
        ChatMessage baseMessage = mock(ChatMessage.class);
        LocalDateTime baseCreatedAt = createdAt(1L);
        when(baseMessage.getId()).thenReturn(1L);
        when(baseMessage.getCreatedAt()).thenReturn(baseCreatedAt);
        when(messageRepository.findByIdAndRegionalChatRoom_IdAndStatus(
                1L,
                ROOM_ID,
                ChatMessageStatus.VISIBLE
        )).thenReturn(Optional.of(baseMessage));
        List<ChatMessageHistoryProjection> ascending = new ArrayList<>();
        for (long id = 2; id <= 22; id++) {
            ascending.add(projection(id));
        }
        when(messageRepository.findNewerHistory(
                eq(ROOM_ID),
                eq(ChatMessageStatus.VISIBLE),
                eq(baseCreatedAt),
                eq(1L),
                any(Pageable.class)
        )).thenReturn(ascending);

        var response = service.getMessages(USER_PUBLIC_ID, ROOM_ID, null, 1L);

        assertThat(response.items()).hasSize(20);
        assertThat(response.items().get(0).messageId()).isEqualTo("2");
        assertThat(response.items().get(19).messageId()).isEqualTo("21");
        assertThat(response.page().nextCursor()).isNull();
        assertThat(response.page().nextAfterMessageId()).isEqualTo("21");
        assertThat(response.page().hasNext()).isTrue();
    }

    @Test
    void rejectsCursorAndAfterMessageIdTogether() {
        assertThatThrownBy(() -> service.getMessages(
                USER_PUBLIC_ID,
                ROOM_ID,
                "cursor",
                10001L
        )).isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_MESSAGE_HISTORY_QUERY)
        );
    }

    private ChatMessageHistoryProjection projection(long id) {
        ChatMessageHistoryProjection projection = mock(ChatMessageHistoryProjection.class);
        when(projection.getMessageId()).thenReturn(id);
        when(projection.getClientMessageId()).thenReturn(new UUID(0L, id));
        when(projection.getMessageType()).thenReturn(ChatMessageType.TEXT);
        when(projection.getTextContent()).thenReturn("메시지 " + id);
        when(projection.getSenderPublicId()).thenReturn(USER_UUID);
        when(projection.getSenderUsername()).thenReturn("플랜잇사용자");
        when(projection.getCreatedAt()).thenReturn(createdAt(id));
        return projection;
    }

    private LocalDateTime createdAt(long id) {
        return LocalDateTime.ofEpochSecond(id, 0, ZoneOffset.UTC);
    }
}
