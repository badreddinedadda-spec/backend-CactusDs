package com.cactusds.backend.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity @Table(name = "domaines")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class Domaine {

    public enum Statut { ACTIF, EXPIRE, EN_ATTENTE }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 200)
    private String nom;

    @Column(nullable = false, length = 10)
    private String extension;

    @Column(name = "date_expiration")
    private LocalDate dateExpiration;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Statut statut = Statut.ACTIF;

    @Builder.Default
    @Column(name = "renouvellement_auto")
    private Boolean renouvellementAuto = true;

    @Builder.Default
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @OneToOne(mappedBy = "domaine", fetch = FetchType.LAZY)
    private SslCertificat sslCertificat;

    public String getNomComplet() { return nom + extension; }
}