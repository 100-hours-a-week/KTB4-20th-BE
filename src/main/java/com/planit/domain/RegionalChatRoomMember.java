package com.planit.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Entity
@Table(
        name = "regional_chat_room_members",
        uniqueConstraints = @UniqueConstraint(
                name = "UK_REGIONAL_CHAT_ROOM_MEMBERS_USER_ROOM",
                columnNames = {"user_id", "regional_chat_room_id"}
        )
)
public class RegionalChatRoomMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "regional_chat_room_id", nullable = false)
    private RegionalChatRoom regionalChatRoom;

    @Column(name = "joined_at", nullable = false)
    private LocalDateTime joinedAt;

    @Column(name = "left_at")
    private LocalDateTime leftAt;

    protected RegionalChatRoomMember() {
    }

    public RegionalChatRoomMember(User user, RegionalChatRoom regionalChatRoom) {
        this.user = user;
        this.regionalChatRoom = regionalChatRoom;
        this.joinedAt = LocalDateTime.now(ZoneOffset.UTC);
    }

    public void rejoin() {
        joinedAt = LocalDateTime.now(ZoneOffset.UTC);
        leftAt = null;
    }

    public void leave() {
        if (isActive()) {
            leftAt = LocalDateTime.now(ZoneOffset.UTC);
        }
    }

    public boolean isActive() {
        return leftAt == null;
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public RegionalChatRoom getRegionalChatRoom() {
        return regionalChatRoom;
    }

    public LocalDateTime getJoinedAt() {
        return joinedAt;
    }

    public LocalDateTime getLeftAt() {
        return leftAt;
    }
}
