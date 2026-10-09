package com.cactusds.backend.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity @Table(name = "ticket_messages", indexes = @Index(name = "idx_ticket_messages_ticket", columnList = "ticket_id, created_at"))
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class TicketMessage {
    public enum Auteur { CLIENT, SUPPORT }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ticket_id", nullable = false)
    @ToString.Exclude @EqualsAndHashCode.Exclude
    private Ticket ticket;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Auteur auteur;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    @Builder.Default
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}