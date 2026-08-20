package com.cactusds.backend.controller;

import com.cactusds.backend.dto.ContactRequest;
import com.cactusds.backend.model.Contact;
import com.cactusds.backend.repository.ContactRepository;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/contact")
public class ContactController {

    private final ContactRepository contactRepository;

    public ContactController(ContactRepository contactRepository) {
        this.contactRepository = contactRepository;
    }

    @PostMapping
    public ResponseEntity<?> submit(@Valid @RequestBody ContactRequest request) {
        Contact contact = Contact.builder()
                .nom(request.nom())
                .email(request.email())
                .telephone(request.telephone())
                .sujet(request.sujet())
                .message(request.message())
                .build();

        contactRepository.save(contact);

        return ResponseEntity.status(201).build();
    }
}