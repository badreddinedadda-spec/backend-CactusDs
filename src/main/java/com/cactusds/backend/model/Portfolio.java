package com.cactusds.backend.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity @Table(name = "portfolio")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class Portfolio {

    public enum Categorie { WEB, MOBILE, DESIGN, MARKETING, MEDIA }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String titre;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Column(name = "lien_url", length = 500)
    private String lienUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Categorie categorie;

    @Column(length = 150)
    private String client;

    @Column(name = "date_projet")
    private LocalDate dateProjet;

    @Builder.Default
    @Column(name = "en_vedette", nullable = false)
    private Boolean enVedette = false;

    @Builder.Default
    @Column(nullable = false)
    private Integer ordre = 0;

    @Builder.Default
    @Column(nullable = false)
    private Boolean actif = true;

    @Builder.Default
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
