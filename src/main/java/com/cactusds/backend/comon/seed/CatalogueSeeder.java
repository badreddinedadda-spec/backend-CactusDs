package com.cactusds.backend.comon.seed;

import com.cactusds.backend.model.CategorieOffre;
import com.cactusds.backend.model.Offre;
import com.cactusds.backend.repository.CategorieOffreRepository;
import com.cactusds.backend.repository.OffreRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class CatalogueSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(CatalogueSeeder.class);

    private final CategorieOffreRepository categorieOffreRepository;
    private final OffreRepository offreRepository;

    public CatalogueSeeder(CategorieOffreRepository categorieOffreRepository, OffreRepository offreRepository) {
        this.categorieOffreRepository = categorieOffreRepository;
        this.offreRepository = offreRepository;
    }

    @Override
    public void run(String... args) {
        if (categorieOffreRepository.count() > 0 || offreRepository.count() > 0) {
            return;
        }
        log.info("Catalogue vide détecté — insertion d'un catalogue de démarrage.");

        CategorieOffre siteWeb = categorieOffreRepository.save(CategorieOffre.builder()
                .nom("Hébergement Web")
                .slug("hebergement-web")
                .description("Hébergement mutualisé pour sites vitrines, blogs et e-commerce.")
                .icone("🌐")
                .famille(CategorieOffre.Famille.SITE_WEB)
                .ordreAffichage(1)
                .actif(true)
                .build());

        CategorieOffre cloud = categorieOffreRepository.save(CategorieOffre.builder()
                .nom("Serveurs VPS Cloud")
                .slug("vps-cloud")
                .description("Serveurs privés virtuels pour les projets qui ont besoin de ressources dédiées.")
                .icone("☁️")
                .famille(CategorieOffre.Famille.SERVEUR_CLOUD)
                .ordreAffichage(2)
                .actif(true)
                .build());

        offreRepository.save(Offre.builder()
                .categorie(siteWeb).nom("Essentiel")
                .description("Pour un site vitrine ou un premier projet.")
                .prixMensuel(new BigDecimal("29.00")).prixAnnuel(new BigDecimal("290.00"))
                .espaceDisqueGo(10).bandePassanteGo(100)
                .nbDomaines(1).nbEmails(5).sslInclus(true).actif(true).ordreAffichage(1)
                .build());

        offreRepository.save(Offre.builder()
                .categorie(siteWeb).nom("Entreprise")
                .description("Pour les entreprises qui gèrent plusieurs sites.")
                .prixMensuel(new BigDecimal("59.00")).prixAnnuel(new BigDecimal("590.00"))
                .espaceDisqueGo(50).bandePassanteGo(500)
                .nbDomaines(5).nbEmails(25).sslInclus(true).actif(true).ordreAffichage(2)
                .build());

        offreRepository.save(Offre.builder()
                .categorie(siteWeb).nom("Performance")
                .description("Pour l'e-commerce et les sites à fort trafic.")
                .prixMensuel(new BigDecimal("99.00")).prixAnnuel(new BigDecimal("990.00"))
                .espaceDisqueGo(150).bandePassanteGo(2000)
                .nbDomaines(10).nbEmails(50).sslInclus(true).actif(true).ordreAffichage(3)
                .build());

        offreRepository.save(Offre.builder()
                .categorie(cloud).nom("VPS Starter")
                .description("2 vCPU, 4 Go RAM — pour les applications légères.")
                .prixMensuel(new BigDecimal("149.00")).prixAnnuel(new BigDecimal("1490.00"))
                .espaceDisqueGo(80).bandePassanteGo(null)
                .nbDomaines(10).nbEmails(50).sslInclus(true).actif(true).ordreAffichage(1)
                .build());

        offreRepository.save(Offre.builder()
                .categorie(cloud).nom("VPS Pro")
                .description("4 vCPU, 8 Go RAM — pour les applications à fort trafic.")
                .prixMensuel(new BigDecimal("299.00")).prixAnnuel(new BigDecimal("2990.00"))
                .espaceDisqueGo(160).bandePassanteGo(null)
                .nbDomaines(20).nbEmails(100).sslInclus(true).actif(true).ordreAffichage(2)
                .build());

        log.info("Catalogue de démarrage inséré : 2 catégories, 5 offres.");
    }
}