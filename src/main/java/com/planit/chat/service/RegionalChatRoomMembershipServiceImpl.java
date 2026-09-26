package com.planit.chat.service;

import com.planit.chat.dto.RegionalChatRoomJoinResponse;
import com.planit.chat.dto.RegionalChatRoomLeaveResponse;
import com.planit.domain.ChatPolicyStatus;
import com.planit.domain.ChatPolicyVersion;
import com.planit.domain.RegionalChatRoom;
import com.planit.domain.RegionalChatRoomMember;
import com.planit.domain.User;
import com.planit.global.error.BusinessException;
import com.planit.global.error.ErrorCode;
import com.planit.repository.ChatPolicyConsentRepository;
import com.planit.repository.ChatPolicyVersionRepository;
import com.planit.repository.RegionalChatRoomMemberRepository;
import com.planit.repository.RegionalChatRoomRepository;
import com.planit.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneOffset;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class RegionalChatRoomMembershipServiceImpl
        implements RegionalChatRoomMembershipService {

    private final UserRepository userRepository;
    private final RegionalChatRoomRepository regionalChatRoomRepository;
    private final RegionalChatRoomMemberRepository memberRepository;
    private final ChatPolicyVersionRepository chatPolicyVersionRepository;
    private final ChatPolicyConsentRepository chatPolicyConsentRepository;

    @Override
    public RegionalChatRoomJoinResponse join(String userPublicId, Long roomId) {
        User user = findActiveUserForUpdate(userPublicId);
        RegionalChatRoom room = findRoom(roomId);
        validateCurrentPolicyConsent(user);

        RegionalChatRoomMember membership = memberRepository
                .findByUserAndRegionalChatRoom(user, room)
                .orElse(null);

        if (membership == null) {
            membership = memberRepository.save(new RegionalChatRoomMember(user, room));
        } else if (!membership.isActive()) {
            membership.rejoin();
        }

        return new RegionalChatRoomJoinResponse(
                room.getId().toString(),
                membership.getJoinedAt().toInstant(ZoneOffset.UTC)
        );
    }

    @Override
    public RegionalChatRoomLeaveResponse leave(String userPublicId, Long roomId) {
        User user = findActiveUserForUpdate(userPublicId);
        RegionalChatRoom room = findRoom(roomId);
        RegionalChatRoomMember membership = memberRepository
                .findByUserAndRegionalChatRoom(user, room)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.REGIONAL_CHAT_MEMBER_REQUIRED
                ));

        membership.leave();

        return new RegionalChatRoomLeaveResponse(
                room.getId().toString(),
                membership.getLeftAt().toInstant(ZoneOffset.UTC)
        );
    }

    private User findActiveUserForUpdate(String userPublicId) {
        try {
            return userRepository.findActiveByPublicIdForUpdate(UUID.fromString(userPublicId))
                    .orElseThrow(() -> new BusinessException(ErrorCode.AUTHENTICATION_REQUIRED));
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.AUTHENTICATION_REQUIRED, exception);
        }
    }

    private RegionalChatRoom findRoom(Long roomId) {
        return regionalChatRoomRepository.findById(roomId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.REGIONAL_CHAT_ROOM_NOT_FOUND
                ));
    }

    private void validateCurrentPolicyConsent(User user) {
        ChatPolicyVersion currentPolicy = chatPolicyVersionRepository
                .findByStatus(ChatPolicyStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.ACTIVE_CHAT_POLICY_UNAVAILABLE
                ));
        if (!chatPolicyConsentRepository.existsByUserAndChatPolicyVersion(
                user,
                currentPolicy
        )) {
            throw new BusinessException(ErrorCode.CHAT_POLICY_CONSENT_REQUIRED);
        }
    }
}
