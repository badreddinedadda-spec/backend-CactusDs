package com.cactusds.backend.dto;

import com.cactusds.backend.model.CategorieOffre;
import com.cactusds.backend.model.Commande;
import com.cactusds.backend.model.Offre;
import com.cactusds.backend.model.User;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class CommandeResponseTest {

    private static Commande order(CategorieOffre categorie) {
        Offre offre = Offre.builder().id(3L).nom("VPS Pro").categorie(categorie).build();
        User user = User.builder().id(7L).email("client@cactusds.test").build();
        return Commande.builder().id(4L).user(user).offre(offre)
                .statut(Commande.Statut.ACTIVE).duree(Commande.Duree.ANNUEL)
                .prixTotal(new BigDecimal("2400.00")).build();
    }

    @Test
    void exposesOfferIdCategoryNameAndFamily() {
        CommandeResponse r = CommandeResponse.from(order(
                CategorieOffre.builder().nom("VPS & Cloud").famille(CategorieOffre.Famille.SERVEUR_CLOUD).build()));
        assertEquals(3L, r.offreId());
        assertEquals("VPS & Cloud", r.categorieNom());
        assertEquals("SERVEUR_CLOUD", r.famille());
    }

    @Test
    void webHostingFamily() {
        CommandeResponse r = CommandeResponse.from(order(
                CategorieOffre.builder().nom("Hébergement web").famille(CategorieOffre.Famille.SITE_WEB).build()));
        assertEquals("SITE_WEB", r.famille());
    }

    @Test
    void existingFieldsAreUnchanged() {
        CommandeResponse r = CommandeResponse.from(order(CategorieOffre.builder().nom("X").build()));
        assertEquals(4L, r.id());
        assertEquals(7L, r.userId());
        assertEquals("VPS Pro", r.offreNom());
        assertEquals("ACTIVE", r.statut());
        assertEquals("ANNUEL", r.duree());
        assertEquals("client@cactusds.test", r.clientEmail());
    }

    @Test
    void missingCategoryOrFamilyIsNullNotAnError() {
        CommandeResponse noCategory = CommandeResponse.from(order(null));
        assertNull(noCategory.categorieNom());
        assertNull(noCategory.famille());
        CommandeResponse noFamily = CommandeResponse.from(order(CategorieOffre.builder().nom("X").famille(null).build()));
        assertNull(noFamily.famille());
        assertEquals("X", noFamily.categorieNom());
    }
}