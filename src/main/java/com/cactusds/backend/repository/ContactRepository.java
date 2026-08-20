package com.cactusds.backend.repository;

import com.cactusds.backend.model.Contact;

import org.springframework.data.jpa.repository.JpaRepository;
public interface ContactRepository extends JpaRepository<Contact, Long> {
}
