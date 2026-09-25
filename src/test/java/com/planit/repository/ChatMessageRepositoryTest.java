package com.planit.repository;

import com.planit.domain.ChatMessage;
import com.planit.domain.ChatMessageStatus;
import com.planit.domain.ChatMessageType;
import com.planit.domain.ImagePurpose;
import com.planit.domain.RegionalChatRoom;
import com.planit.domain.TextChatMessage;
import com.planit.domain.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class ChatMessageRepositoryTest {

    @Autowired
    private ChatMessageRepository messageRepository;

    @Autowired
    private TextChatMessageRepository textMessageRepository;

    @Autowired
    private RegionalChatRoomRepository roomRepository;

    @Autowired
    private ImageFileRepository imageFileRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void queriesTextMessageHistoryProjection() {
        RegionalChatRoom room = roomRepository.findById(1L).orElseThrow();
        User sender = userRepository.save(new User(
                imageFileRepository.findByImagePurposeAndDeletedAtIsNull(
                        ImagePurpose.DEFAULT_PROFILE
                ).orElseThrow(),
                UUID.randomUUID(),
                "메시지테스트"
        ));
        UUID clientMessageId = UUID.randomUUID();
        ChatMessage message = messageRepository.save(new ChatMessage(
                room,
                sender,
                clientMessageId,
                ChatMessageType.TEXT
        ));
        textMessageRepository.save(new TextChatMessage(message, "경주 맛집 추천해주세요."));

        var rows = messageRepository.findLatestHistory(
                room.getId(),
                ChatMessageStatus.VISIBLE,
                PageRequest.of(0, 21)
        );

        assertThat(rows).anySatisfy(row -> {
            assertThat(row.getMessageId()).isEqualTo(message.getId());
            assertThat(row.getClientMessageId()).isEqualTo(clientMessageId);
            assertThat(row.getMessageType()).isEqualTo(ChatMessageType.TEXT);
            assertThat(row.getTextContent()).isEqualTo("경주 맛집 추천해주세요.");
            assertThat(row.getSenderPublicId()).isEqualTo(sender.getPublicId());
            assertThat(row.getSenderUsername()).isEqualTo("메시지테스트");
        });
    }
}
