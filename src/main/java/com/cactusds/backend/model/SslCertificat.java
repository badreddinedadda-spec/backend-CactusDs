package com.cactusds.backend.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity @Table(name = "ssl_certificats")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class SslCertificat {

    public enum Type   { GRATUIT, PAYANT }
    public enum Statut { ACTIF, EXPIRE, EN_ATTENTE }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "domaine_id", nullable = false, unique = true)
    private Domaine domaine;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Type type = Type.GRATUIT;

    @Column(name = "date_expiration")
    private LocalDate dateExpiration;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Statut statut = Statut.ACTIF;

    @Builder.Default
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}