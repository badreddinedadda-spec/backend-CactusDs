package com.cactusds.backend.model;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Entity @Table(name = "offres")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class Offre {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "categorie_id", nullable = false)
    private CategorieOffre categorie;

    @Column(nullable = false, length = 150)
    private String nom;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "prix_mensuel", nullable = false, precision = 10, scale = 2)
    private BigDecimal prixMensuel;

    @Column(name = "prix_annuel", precision = 10, scale = 2)
    private BigDecimal prixAnnuel;

    @Column(name = "espace_disque_go", nullable = false)
    private Integer espaceDisqueGo;

    @Column(name = "bande_passante_go")
    private Integer bandePassanteGo;

    @Builder.Default
    @Column(name = "nb_domaines")
    private Integer nbDomaines = 1;

    @Builder.Default
    @Column(name = "nb_emails")
    private Integer nbEmails = 5;

    @Builder.Default
    @Column(name = "ssl_inclus")
    private Boolean sslInclus = false;

    @Builder.Default
    @Column(nullable = false)
    private Boolean actif = true;

    @Builder.Default
    @Column(name = "ordre_affichage")
    private Integer ordreAffichage = 0;

    @ElementCollection
    @CollectionTable(name = "offre_specifications", joinColumns = @JoinColumn(name = "offre_id"))
    @MapKeyColumn(name = "spec_key", length = 100)
    @Column(name = "spec_value", length = 255)
    @Builder.Default
    private Map<String, String> specifications = new LinkedHashMap<>();

    @Builder.Default
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}