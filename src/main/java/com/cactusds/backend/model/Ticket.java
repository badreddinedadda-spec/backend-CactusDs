package com.cactusds.backend.model;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity @Table(name = "tickets")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class Ticket {
    public enum Statut    { OUVERT, EN_COURS, RESOLU, FERME }
    public enum Priorite  { BASSE, NORMALE, HAUTE, URGENTE }
    public enum Categorie { GENERAL, DOMAINE, SSL, VPS }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Categorie categorie = Categorie.GENERAL;

    @Column(nullable = false, length = 255)
    private String sujet;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    @Column(columnDefinition = "TEXT")
    private String reponseAdmin;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Statut statut = Statut.OUVERT;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Priorite priorite = Priorite.NORMALE;

    @Builder.Default
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Builder.Default
    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();
}
