package com.planit.chat.service;

import com.planit.chat.dto.RegionalChatRoomJoinResponse;
import com.planit.chat.dto.RegionalChatRoomLeaveResponse;

public interface RegionalChatRoomMembershipService {

    RegionalChatRoomJoinResponse join(String userPublicId, Long roomId);

    RegionalChatRoomLeaveResponse leave(String userPublicId, Long roomId);
}
