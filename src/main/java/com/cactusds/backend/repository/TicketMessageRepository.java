package com.cactusds.backend.repository;

import com.cactusds.backend.model.TicketMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TicketMessageRepository extends JpaRepository<TicketMessage, Long> {
    List<TicketMessage> findByTicketIdOrderByCreatedAtAscIdAsc(Long ticketId);
    long countByTicketId(Long ticketId);
}
