package com.cactusds.backend.repository;
import com.cactusds.backend.model.Ticket;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface TicketRepository extends JpaRepository<Ticket, Long> {
    List<Ticket> findByUserIdOrderByCreatedAtDesc(Long userId);
    Optional<Ticket> findByIdAndUserId(Long id, Long userId);
}
