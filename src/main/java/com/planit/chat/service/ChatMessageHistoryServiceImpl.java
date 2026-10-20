package com.planit.chat.service;

import com.planit.chat.dto.ChatMessageHistoryResponse;
import com.planit.chat.dto.ChatMessageHistoryResponse.ChatMessageItemResponse;
import com.planit.chat.dto.ChatMessageHistoryResponse.ChatMessagePageResponse;
import com.planit.chat.dto.ChatMessageHistoryResponse.ChatSenderResponse;
import com.planit.chat.pagination.ChatMessageCursorStore;
import com.planit.chat.pagination.ChatMessageCursorStore.CursorBoundary;
import com.planit.domain.ChatMessage;
import com.planit.domain.ChatMessageStatus;
import com.planit.global.error.BusinessException;
import com.planit.global.error.ErrorCode;
import com.planit.image.config.ImageProperties;
import com.planit.repository.ChatMessageRepository;
import com.planit.repository.ChatMessageRepository.ChatMessageHistoryProjection;
import com.planit.repository.RegionalChatRoomMemberRepository;
import com.planit.repository.RegionalChatRoomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ChatMessageHistoryServiceImpl implements ChatMessageHistoryService {

    private static final int PAGE_SIZE = 20;
    private static final int QUERY_SIZE = PAGE_SIZE + 1;
    private static final String WITHDRAWN_USER_NAME = "탈퇴한 사용자";

    private final RegionalChatRoomRepository roomRepository;
    private final RegionalChatRoomMemberRepository memberRepository;
    private final ChatMessageRepository messageRepository;
    private final ChatMessageCursorStore cursorStore;
    private final ImageProperties imageProperties;

    @Override
    public ChatMessageHistoryResponse getMessages(
            String userPublicId,
            Long roomId,
            String cursor,
            Long afterMessageId
    ) {
        UUID publicId = parsePublicId(userPublicId);
        validateQuery(cursor, afterMessageId);
        validateAccess(publicId, roomId);

        if (afterMessageId != null) {
            return getNewerMessages(roomId, afterMessageId);
        }
        return getLatestOrOlderMessages(userPublicId, roomId, cursor);
    }

    private ChatMessageHistoryResponse getLatestOrOlderMessages(
            String userPublicId,
            Long roomId,
            String cursor
    ) {
        List<ChatMessageHistoryProjection> queried;
        if (cursor == null) {
            queried = messageRepository.findLatestHistory(
                    roomId,
                    ChatMessageStatus.VISIBLE,
                    PageRequest.of(0, QUERY_SIZE)
            );
        } else {
            CursorBoundary boundary = cursorStore.resolve(cursor, userPublicId, roomId);
            queried = messageRepository.findOlderHistory(
                    roomId,
                    ChatMessageStatus.VISIBLE,
                    boundary.beforeCreatedAt(),
                    boundary.beforeMessageId(),
                    PageRequest.of(0, QUERY_SIZE)
            );
        }

        boolean hasNext = queried.size() > PAGE_SIZE;
        List<ChatMessageHistoryProjection> pageRows = firstPage(queried);
        String nextCursor = null;
        if (hasNext && !pageRows.isEmpty()) {
            ChatMessageHistoryProjection oldest = pageRows.get(pageRows.size() - 1);
            nextCursor = cursorStore.issue(
                    userPublicId,
                    roomId,
                    oldest.getCreatedAt(),
                    oldest.getMessageId()
            );
        }

        List<ChatMessageHistoryProjection> ascending = new ArrayList<>(pageRows);
        Collections.reverse(ascending);
        return response(ascending, nextCursor, null, hasNext);
    }

    private ChatMessageHistoryResponse getNewerMessages(Long roomId, Long afterMessageId) {
        ChatMessage baseMessage = messageRepository
                .findByIdAndRegionalChatRoom_IdAndStatus(
                        afterMessageId,
                        roomId,
                        ChatMessageStatus.VISIBLE
                )
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.INVALID_AFTER_MESSAGE_ID
                ));
        List<ChatMessageHistoryProjection> queried = messageRepository.findNewerHistory(
                roomId,
                ChatMessageStatus.VISIBLE,
                baseMessage.getCreatedAt(),
                baseMessage.getId(),
                PageRequest.of(0, QUERY_SIZE)
        );
        boolean hasNext = queried.size() > PAGE_SIZE;
        List<ChatMessageHistoryProjection> pageRows = firstPage(queried);
        String nextAfterMessageId = hasNext && !pageRows.isEmpty()
                ? pageRows.get(pageRows.size() - 1).getMessageId().toString()
                : null;
        return response(pageRows, null, nextAfterMessageId, hasNext);
    }

    private List<ChatMessageHistoryProjection> firstPage(
            List<ChatMessageHistoryProjection> queried
    ) {
        return queried.subList(0, Math.min(PAGE_SIZE, queried.size()));
    }

    private ChatMessageHistoryResponse response(
            List<ChatMessageHistoryProjection> rows,
            String nextCursor,
            String nextAfterMessageId,
            boolean hasNext
    ) {
        return new ChatMessageHistoryResponse(
                rows.stream().map(this::toResponse).toList(),
                new ChatMessagePageResponse(nextCursor, nextAfterMessageId, hasNext)
        );
    }

    private ChatMessageItemResponse toResponse(ChatMessageHistoryProjection row) {
        boolean withdrawn = row.getSenderDeletedAt() != null;
        return new ChatMessageItemResponse(
                row.getMessageId().toString(),
                row.getClientMessageId().toString(),
                row.getMessageType().name(),
                row.getTextContent(),
                null,
                new ChatSenderResponse(
                        withdrawn ? null : row.getSenderPublicId().toString(),
                        withdrawn ? WITHDRAWN_USER_NAME : row.getSenderUsername(),
                        withdrawn
                                ? null
                                : imageProperties.defaultProfileUrl().toString()
                ),
                row.getCreatedAt().toInstant(ZoneOffset.UTC)
        );
    }

    private void validateQuery(String cursor, Long afterMessageId) {
        if (cursor != null && afterMessageId != null) {
            throw new BusinessException(ErrorCode.INVALID_MESSAGE_HISTORY_QUERY);
        }
    }

    private void validateAccess(UUID userPublicId, Long roomId) {
        if (!roomRepository.existsById(roomId)) {
            throw new BusinessException(ErrorCode.REGIONAL_CHAT_ROOM_NOT_FOUND);
        }
        if (!memberRepository.existsActiveMembership(userPublicId, roomId)) {
            throw new BusinessException(ErrorCode.REGIONAL_CHAT_MEMBER_REQUIRED);
        }
    }

    private UUID parsePublicId(String userPublicId) {
        try {
            return UUID.fromString(userPublicId);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new BusinessException(ErrorCode.AUTHENTICATION_REQUIRED, exception);
        }
    }
}
