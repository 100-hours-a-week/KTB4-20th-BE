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

import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Entity
@Table(name = "chat_policy_consents")
public class ChatPolicyConsent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "chat_policy_version_id", nullable = false)
    private ChatPolicyVersion chatPolicyVersion;

    @Column(name = "consented_at", nullable = false)
    private LocalDateTime consentedAt;

    protected ChatPolicyConsent() {
    }

    public ChatPolicyConsent(
            User user,
            ChatPolicyVersion chatPolicyVersion
    ) {
        this.user = user;
        this.chatPolicyVersion = chatPolicyVersion;
        this.consentedAt = LocalDateTime.now(ZoneOffset.UTC);
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public ChatPolicyVersion getChatPolicyVersion() {
        return chatPolicyVersion;
    }

    public LocalDateTime getConsentedAt() {
        return consentedAt;
    }
}
