package com.planit.chat.service;

import com.planit.chat.dto.ChatMessageSendRequest;
import com.planit.domain.ChatMessage;
import com.planit.domain.ChatPolicyStatus;
import com.planit.domain.ChatPolicyVersion;
import com.planit.domain.ImageFile;
import com.planit.domain.RegionalChatRoom;
import com.planit.domain.TextChatMessage;
import com.planit.domain.User;
import com.planit.global.error.BusinessException;
import com.planit.global.error.ErrorCode;
import com.planit.image.config.ImageProperties;
import com.planit.repository.ChatMessageRepository;
import com.planit.repository.ChatPolicyConsentRepository;
import com.planit.repository.ChatPolicyVersionRepository;
import com.planit.repository.RegionalChatRoomMemberRepository;
import com.planit.repository.RegionalChatRoomRepository;
import com.planit.repository.TextChatMessageRepository;
import com.planit.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.URI;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatMessageTransactionServiceTest {

    private static final UUID USER_PUBLIC_ID = UUID.fromString(
            "01991f6e-7300-7b21-a3cc-1436db3df95e"
    );
    private static final UUID CLIENT_MESSAGE_ID = UUID.fromString(
            "019b1234-5678-7000-8000-123456789abc"
    );
    private static final Long ROOM_ID = 3001L;

    private UserRepository userRepository;
    private RegionalChatRoomRepository roomRepository;
    private RegionalChatRoomMemberRepository memberRepository;
    private ChatPolicyVersionRepository policyVersionRepository;
    private ChatPolicyConsentRepository policyConsentRepository;
    private ChatMessageRepository messageRepository;
    private TextChatMessageRepository textMessageRepository;
    private ChatMessageTransactionService service;
    private User user;
    private RegionalChatRoom room;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        roomRepository = mock(RegionalChatRoomRepository.class);
        memberRepository = mock(RegionalChatRoomMemberRepository.class);
        policyVersionRepository = mock(ChatPolicyVersionRepository.class);
        policyConsentRepository = mock(ChatPolicyConsentRepository.class);
        messageRepository = mock(ChatMessageRepository.class);
        textMessageRepository = mock(TextChatMessageRepository.class);
        service = new ChatMessageTransactionService(
                userRepository,
                roomRepository,
                memberRepository,
                policyVersionRepository,
                policyConsentRepository,
                messageRepository,
                textMessageRepository,
                new ChatTextNormalizer(),
                new ImageProperties(URI.create("https://example.com/profile.svg"))
        );
        user = new User(mock(ImageFile.class), USER_PUBLIC_ID, "플랜잇사용자");
        room = mock(RegionalChatRoom.class);
        ChatPolicyVersion policy = mock(ChatPolicyVersion.class);

        when(room.getId()).thenReturn(ROOM_ID);
        when(userRepository.findByPublicIdAndDeletedAtIsNull(USER_PUBLIC_ID))
                .thenReturn(Optional.of(user));
        when(roomRepository.findById(ROOM_ID)).thenReturn(Optional.of(room));
        when(memberRepository.existsActiveMembership(USER_PUBLIC_ID, ROOM_ID))
                .thenReturn(true);
        when(policyVersionRepository.findByStatus(ChatPolicyStatus.ACTIVE))
                .thenReturn(Optional.of(policy));
        when(policyConsentRepository.existsByUserAndChatPolicyVersion(user, policy))
                .thenReturn(true);
        when(messageRepository.saveAndFlush(any(ChatMessage.class)))
                .thenAnswer(invocation -> {
                    ChatMessage message = invocation.getArgument(0);
                    ReflectionTestUtils.setField(message, "id", 10001L);
                    return message;
                });
        when(textMessageRepository.saveAndFlush(any(TextChatMessage.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void savesNormalizedTextMessage() {
        ChatMessageSendResult result = service.createOrFind(
                USER_PUBLIC_ID.toString(),
                ROOM_ID,
                request("  경주\t 맛집  ")
        );

        assertThat(result.created()).isTrue();
        assertThat(result.message().messageId()).isEqualTo("10001");
        assertThat(result.message().text()).isEqualTo("경주 맛집");
        assertThat(result.message().sender().publicId())
                .isEqualTo(USER_PUBLIC_ID.toString());
        ArgumentCaptor<TextChatMessage> textCaptor = ArgumentCaptor.forClass(
                TextChatMessage.class
        );
        verify(textMessageRepository).saveAndFlush(textCaptor.capture());
        assertThat(textCaptor.getValue().getTextContent()).isEqualTo("경주 맛집");
    }

    @Test
    void returnsExistingResultWithoutBroadcastCandidateForSamePayload() {
        ChatMessage existing = new ChatMessage(
                room,
                user,
                CLIENT_MESSAGE_ID,
                com.planit.domain.ChatMessageType.TEXT
        );
        ReflectionTestUtils.setField(existing, "id", 10001L);
        when(messageRepository.findBySenderAndClientMessageId(user, CLIENT_MESSAGE_ID))
                .thenReturn(Optional.of(existing));
        when(textMessageRepository.findByChatMessage(existing))
                .thenReturn(Optional.of(new TextChatMessage(existing, "경주 맛집")));

        ChatMessageSendResult result = service.createOrFind(
                USER_PUBLIC_ID.toString(),
                ROOM_ID,
                request("경주  맛집")
        );

        assertThat(result.created()).isFalse();
        assertThat(result.message().messageId()).isEqualTo("10001");
        verify(messageRepository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsReusedClientMessageIdWithDifferentText() {
        ChatMessage existing = new ChatMessage(
                room,
                user,
                CLIENT_MESSAGE_ID,
                com.planit.domain.ChatMessageType.TEXT
        );
        ReflectionTestUtils.setField(existing, "id", 10001L);
        when(messageRepository.findBySenderAndClientMessageId(user, CLIENT_MESSAGE_ID))
                .thenReturn(Optional.of(existing));
        when(textMessageRepository.findByChatMessage(existing))
                .thenReturn(Optional.of(new TextChatMessage(existing, "기존 내용")));

        assertThatThrownBy(() -> service.createOrFind(
                USER_PUBLIC_ID.toString(),
                ROOM_ID,
                request("다른 내용")
        )).isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.CLIENT_MESSAGE_ID_REUSED);
    }

    @Test
    void rejectsUserWithoutActivePolicyConsent() {
        when(policyConsentRepository.existsByUserAndChatPolicyVersion(any(), any()))
                .thenReturn(false);

        assertThatThrownBy(() -> service.createOrFind(
                USER_PUBLIC_ID.toString(),
                ROOM_ID,
                request("메시지")
        )).isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.CHAT_POLICY_CONSENT_REQUIRED);
    }

    private ChatMessageSendRequest request(String text) {
        return new ChatMessageSendRequest(
                CLIENT_MESSAGE_ID,
                "TEXT",
                text,
                null
        );
    }
}
