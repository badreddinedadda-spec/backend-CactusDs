package com.cactusds.backend.repository;
import com.cactusds.backend.model.SslCertificat;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface SslCertificatRepository extends JpaRepository<SslCertificat, Long> {
    Optional<SslCertificat> findByDomaineId(Long domaineId);
}