package com.planit.chat.service;

import com.planit.chat.dto.ChatMessageHistoryResponse.ChatMessageItemResponse;
import com.planit.chat.dto.ChatMessageHistoryResponse.ChatSenderResponse;
import com.planit.chat.dto.ChatMessageSendRequest;
import com.planit.domain.ChatMessage;
import com.planit.domain.ChatMessageType;
import com.planit.domain.ChatPolicyStatus;
import com.planit.domain.ChatPolicyVersion;
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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneOffset;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ChatMessageTransactionService {

    private final UserRepository userRepository;
    private final RegionalChatRoomRepository roomRepository;
    private final RegionalChatRoomMemberRepository memberRepository;
    private final ChatPolicyVersionRepository policyVersionRepository;
    private final ChatPolicyConsentRepository policyConsentRepository;
    private final ChatMessageRepository messageRepository;
    private final TextChatMessageRepository textMessageRepository;
    private final ChatTextNormalizer textNormalizer;
    private final ImageProperties imageProperties;

    @Transactional
    public ChatMessageSendResult createOrFind(
            String userPublicId,
            Long roomId,
            ChatMessageSendRequest request
    ) {
        UUID publicId = parsePublicId(userPublicId);
        User user = findUser(publicId);
        RegionalChatRoom room = roomRepository.findById(roomId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.REGIONAL_CHAT_ROOM_NOT_FOUND
                ));
        validateSendPermission(user, publicId, roomId);
        String normalizedText = validateAndNormalize(request);

        return messageRepository
                .findBySenderAndClientMessageId(user, request.clientMessageId())    //멱등성 검사
                .map(message -> existingResult(         //이미 전송 이력 존재 시 전송 실시 않고 결과만 다시 반환
                        message,
                        roomId,
                        normalizedText
                ))
                .orElseGet(() -> createMessage(
                        user,
                        room,
                        request.clientMessageId(),
                        normalizedText
                ));
    }

    @Transactional(readOnly = true)
    public ChatMessageSendResult findExistingAfterConflict(
            String userPublicId,
            Long roomId,
            ChatMessageSendRequest request
    ) {
        UUID publicId = parsePublicId(userPublicId);
        User user = findUser(publicId);
        String normalizedText = validateAndNormalize(request);
        ChatMessage message = messageRepository
                .findBySenderAndClientMessageId(user, request.clientMessageId())
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.INTERNAL_SERVER_ERROR
                ));
        return existingResult(message, roomId, normalizedText);
    }

    private void validateSendPermission(User user, UUID publicId, Long roomId) {
        if (!memberRepository.existsActiveMembership(publicId, roomId)) {
            throw new BusinessException(ErrorCode.REGIONAL_CHAT_MEMBER_REQUIRED);
        }
        ChatPolicyVersion activePolicy = policyVersionRepository
                .findByStatus(ChatPolicyStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.ACTIVE_CHAT_POLICY_UNAVAILABLE
                ));
        if (!policyConsentRepository.existsByUserAndChatPolicyVersion(
                user,
                activePolicy
        )) {
            throw new BusinessException(ErrorCode.CHAT_POLICY_CONSENT_REQUIRED);
        }
    }

    private String validateAndNormalize(ChatMessageSendRequest request) {
        if (request == null
                || request.clientMessageId() == null
                || request.clientMessageId().version() != 7
                || !ChatMessageType.TEXT.name().equals(request.messageType())
                || request.uploadKey() != null) {
            throw new BusinessException(ErrorCode.INVALID_CHAT_MESSAGE_PAYLOAD);
        }
        return textNormalizer.normalize(request.text());
    }

    private ChatMessageSendResult createMessage(
            User user,
            RegionalChatRoom room,
            UUID clientMessageId,
            String normalizedText
    ) {
        ChatMessage message = messageRepository.saveAndFlush(new ChatMessage(
                room,
                user,
                clientMessageId,
                ChatMessageType.TEXT
        ));
        textMessageRepository.saveAndFlush(new TextChatMessage(
                message,
                normalizedText
        ));
        return new ChatMessageSendResult(
                toResponse(message, normalizedText),
                true
        );
    }

    private ChatMessageSendResult existingResult(
            ChatMessage message,
            Long roomId,
            String normalizedText
    ) {
        TextChatMessage textMessage = textMessageRepository
                .findByChatMessage(message)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.CLIENT_MESSAGE_ID_REUSED
                ));
        if (!message.getRegionalChatRoom().getId().equals(roomId)
                || message.getMessageType() != ChatMessageType.TEXT
                || !textMessage.getTextContent().equals(normalizedText)) {
            throw new BusinessException(ErrorCode.CLIENT_MESSAGE_ID_REUSED);
        }
        return new ChatMessageSendResult(
                toResponse(message, textMessage.getTextContent()),
                false
        );
    }

    private ChatMessageItemResponse toResponse(
            ChatMessage message,
            String normalizedText
    ) {
        User sender = message.getSender();
        return new ChatMessageItemResponse(
                message.getId().toString(),
                message.getClientMessageId().toString(),
                message.getMessageType().name(),
                normalizedText,
                null,
                new ChatSenderResponse(
                        sender.getPublicId().toString(),
                        sender.getUsername(),
                        imageProperties.defaultProfileUrl().toString()
                ),
                message.getCreatedAt().toInstant(ZoneOffset.UTC)
        );
    }

    private User findUser(UUID publicId) {
        return userRepository.findByPublicIdAndDeletedAtIsNull(publicId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.AUTHENTICATION_REQUIRED
                ));
    }

    private UUID parsePublicId(String userPublicId) {
        try {
            return UUID.fromString(userPublicId);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new BusinessException(ErrorCode.AUTHENTICATION_REQUIRED, exception);
        }
    }
}
