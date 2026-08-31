package com.cactusds.backend.model;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity @Table(name = "factures")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class Facture {

    public enum Statut { EMISE, PAYEE, ANNULEE }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 30)
    private String numero;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "periode_debut", nullable = false)
    private LocalDate periodeDebut;

    @Column(name = "periode_fin", nullable = false)
    private LocalDate periodeFin;

    @Column(name = "montant_total", nullable = false, precision = 10, scale = 2)
    private BigDecimal montantTotal;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Statut statut = Statut.EMISE;

    @Builder.Default
    @Column(name = "date_emission", updatable = false)
    private LocalDateTime dateEmission = LocalDateTime.now();

    @Builder.Default
    @Column(name = "relance_envoyee")
    private Boolean relanceEnvoyee = false;
}