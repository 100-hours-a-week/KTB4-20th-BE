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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RegionalChatRoomMembershipServiceImplTest {

    private static final UUID USER_PUBLIC_ID = UUID.fromString(
            "01991f6e-7300-7b21-a3cc-1436db3df95e"
    );

    private UserRepository userRepository;
    private RegionalChatRoomRepository roomRepository;
    private RegionalChatRoomMemberRepository memberRepository;
    private ChatPolicyVersionRepository policyVersionRepository;
    private ChatPolicyConsentRepository policyConsentRepository;
    private RegionalChatRoomMembershipServiceImpl service;
    private User user;
    private RegionalChatRoom room;
    private ChatPolicyVersion policy;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        roomRepository = mock(RegionalChatRoomRepository.class);
        memberRepository = mock(RegionalChatRoomMemberRepository.class);
        policyVersionRepository = mock(ChatPolicyVersionRepository.class);
        policyConsentRepository = mock(ChatPolicyConsentRepository.class);
        service = new RegionalChatRoomMembershipServiceImpl(
                userRepository,
                roomRepository,
                memberRepository,
                policyVersionRepository,
                policyConsentRepository
        );

        user = mock(User.class);
        room = mock(RegionalChatRoom.class);
        policy = mock(ChatPolicyVersion.class);
        when(room.getId()).thenReturn(3001L);
        when(userRepository.findActiveByPublicIdForUpdate(USER_PUBLIC_ID))
                .thenReturn(Optional.of(user));
        when(roomRepository.findById(3001L)).thenReturn(Optional.of(room));
        when(policyVersionRepository.findByStatus(ChatPolicyStatus.ACTIVE))
                .thenReturn(Optional.of(policy));
        when(policyConsentRepository.existsByUserAndChatPolicyVersion(user, policy))
                .thenReturn(true);
    }

    @Test
    void joinsRoomAfterCurrentPolicyConsent() {
        when(memberRepository.findByUserAndRegionalChatRoom(user, room))
                .thenReturn(Optional.empty());
        when(memberRepository.save(any(RegionalChatRoomMember.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        RegionalChatRoomJoinResponse response = service.join(
                USER_PUBLIC_ID.toString(),
                3001L
        );

        assertThat(response.roomId()).isEqualTo("3001");
        assertThat(response.joinedAt()).isNotNull();
        verify(memberRepository).save(any(RegionalChatRoomMember.class));
    }

    @Test
    void returnsActiveMembershipWithoutDuplicateInsert() {
        RegionalChatRoomMember membership = new RegionalChatRoomMember(user, room);
        when(memberRepository.findByUserAndRegionalChatRoom(user, room))
                .thenReturn(Optional.of(membership));

        RegionalChatRoomJoinResponse response = service.join(
                USER_PUBLIC_ID.toString(),
                3001L
        );

        assertThat(response.joinedAt()).isNotNull();
        verify(memberRepository, never()).save(any());
    }

    @Test
    void rejoinsPastMembershipWithoutNewRow() {
        RegionalChatRoomMember membership = new RegionalChatRoomMember(user, room);
        membership.leave();
        when(memberRepository.findByUserAndRegionalChatRoom(user, room))
                .thenReturn(Optional.of(membership));

        service.join(USER_PUBLIC_ID.toString(), 3001L);

        assertThat(membership.isActive()).isTrue();
        verify(memberRepository, never()).save(any());
    }

    @Test
    void rejectsJoinWithoutCurrentPolicyConsent() {
        when(policyConsentRepository.existsByUserAndChatPolicyVersion(user, policy))
                .thenReturn(false);

        assertThatThrownBy(() -> service.join(USER_PUBLIC_ID.toString(), 3001L))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.CHAT_POLICY_CONSENT_REQUIRED);

        verify(memberRepository, never()).save(any());
    }

    @Test
    void leavesActiveMembership() {
        RegionalChatRoomMember membership = new RegionalChatRoomMember(user, room);
        when(memberRepository.findByUserAndRegionalChatRoom(user, room))
                .thenReturn(Optional.of(membership));

        RegionalChatRoomLeaveResponse response = service.leave(
                USER_PUBLIC_ID.toString(),
                3001L
        );

        assertThat(membership.isActive()).isFalse();
        assertThat(response.leftAt()).isNotNull();
    }

    @Test
    void returnsPastLeaveWithoutChangingItAgain() {
        RegionalChatRoomMember membership = new RegionalChatRoomMember(user, room);
        membership.leave();
        var originalLeftAt = membership.getLeftAt();
        when(memberRepository.findByUserAndRegionalChatRoom(user, room))
                .thenReturn(Optional.of(membership));

        RegionalChatRoomLeaveResponse response = service.leave(
                USER_PUBLIC_ID.toString(),
                3001L
        );

        assertThat(response.leftAt()).isEqualTo(originalLeftAt.toInstant(java.time.ZoneOffset.UTC));
    }

    @Test
    void rejectsLeaveWithoutMembershipHistory() {
        when(memberRepository.findByUserAndRegionalChatRoom(user, room))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.leave(USER_PUBLIC_ID.toString(), 3001L))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.REGIONAL_CHAT_MEMBER_REQUIRED);
    }
}
