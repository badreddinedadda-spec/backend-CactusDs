package com.cactusds.backend.controller;

import com.cactusds.backend.dto.ContactRequest;
import com.cactusds.backend.dto.ContactResponse;
import com.cactusds.backend.model.Contact;
import com.cactusds.backend.repository.ContactRepository;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class ContactController {

    private final ContactRepository contactRepository;

    public ContactController(ContactRepository contactRepository) {
        this.contactRepository = contactRepository;
    }

    @PostMapping("/contact")
    public ResponseEntity<ContactResponse> submit(@Valid @RequestBody ContactRequest request) {
        Contact contact = Contact.builder()
                .nom(request.nom())
                .email(request.email())
                .telephone(request.telephone())
                .sujet(request.sujet())
                .message(request.message())
                .build();

        contactRepository.save(contact);
        return ResponseEntity.status(201).body(ContactResponse.from(contact));
    }

    @GetMapping("/admin/contacts")
    public List<ContactResponse> listAll() {
        return contactRepository.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(ContactResponse::from)
                .toList();
    }

    @GetMapping("/admin/contacts/{id}")
    public ResponseEntity<ContactResponse> getOne(@PathVariable Long id) {
        return contactRepository.findById(id)
                .map(contact -> {
                    if (!Boolean.TRUE.equals(contact.getLu())) {
                        contact.setLu(true);
                        contactRepository.save(contact);
                    }
                    return ResponseEntity.ok(ContactResponse.from(contact));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/admin/contacts/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (!contactRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        contactRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}