package com.planit.mq.outbox;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MessageOutboxRepository extends JpaRepository<MessageOutbox, Long> {
}
