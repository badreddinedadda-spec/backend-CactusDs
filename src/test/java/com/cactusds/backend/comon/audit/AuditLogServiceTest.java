package com.cactusds.backend.comon.audit;

import com.cactusds.backend.model.AuditLog;
import com.cactusds.backend.model.User;
import com.cactusds.backend.repository.AuditLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class AuditLogServiceTest {

    private List<AuditLog> saved;
    private AuditLogService service;

    @BeforeEach
    void setUp() {
        saved = new ArrayList<>();
        AuditLogRepository repo = (AuditLogRepository) Proxy.newProxyInstance(
                AuditLogRepository.class.getClassLoader(), new Class[]{AuditLogRepository.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("save")) {
                        AuditLog entry = (AuditLog) args[0];
                        saved.add(entry);
                        return entry;
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
        service = new AuditLogService(repo);
    }

    @Test
    void savesTheActionAndDescriptionUnchanged() {
        User admin = new User();
        admin.setEmail("admin@cactusds.ma");
        admin.setFullName("Admin Principal");

        service.log(admin, "ROLE_CHANGE", "Rôle de jean@x.ma changé de CLIENT à ADMIN");

        assertEquals(1, saved.size());
        assertEquals("ROLE_CHANGE", saved.get(0).getAction());
        assertEquals("Rôle de jean@x.ma changé de CLIENT à ADMIN", saved.get(0).getDescription());
    }

    @Test
    void snapshotsTheActorsEmailAndName() {
        User admin = new User();
        admin.setEmail("admin@cactusds.ma");
        admin.setFullName("Admin Principal");

        service.log(admin, "FACTURE_STATUT_CHANGE", "peu importe");

        assertEquals("admin@cactusds.ma", saved.get(0).getAuteurEmail());
        assertEquals("Admin Principal", saved.get(0).getAuteurNom());
    }

    @Test
    void setsACreationTimestamp() {
        User admin = new User();
        admin.setEmail("admin@cactusds.ma");

        service.log(admin, "ROLE_CHANGE", "peu importe");

        assertNotNull(saved.get(0).getCreatedAt());
    }

    @Test
    void eachCallProducesItsOwnIndependentRow() {
        User admin = new User();
        admin.setEmail("admin@cactusds.ma");

        service.log(admin, "ROLE_CHANGE", "premier changement");
        service.log(admin, "ROLE_CHANGE", "second changement");

        assertEquals(2, saved.size());
        assertEquals("premier changement", saved.get(0).getDescription());
        assertEquals("second changement", saved.get(1).getDescription());
    }
}