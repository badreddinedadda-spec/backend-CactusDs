package com.cactusds.backend.model;

import jakarta.persistence.*;
import lombok.*;
import java.util.List;

@Entity @Table(name = "categories_offre")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class CategorieOffre {
    public enum Famille { SITE_WEB, SERVEUR_CLOUD }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String nom;

    @Column(nullable = false, unique = true, length = 60)
    private String slug;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(length = 8)
    private String icone;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Famille famille = Famille.SITE_WEB;

    @Builder.Default
    @Column(name = "ordre_affichage")
    private Integer ordreAffichage = 0;

    @Builder.Default
    @Column(nullable = false)
    private Boolean actif = true;

    @OneToMany(mappedBy = "categorie", fetch = FetchType.LAZY)
    private List<Offre> offres;
}
