package com.cactusds.backend.controller;

import com.cactusds.backend.comon.audit.AuditLogService;
import com.cactusds.backend.dto.AdminUserResponse;
import com.cactusds.backend.dto.NoteInterneRequest;
import com.cactusds.backend.dto.NoteInterneResponse;
import com.cactusds.backend.dto.RoleUpdateRequest;
import com.cactusds.backend.model.NoteInterne;
import com.cactusds.backend.repository.NoteInterneRepository;
import com.cactusds.backend.model.Role;
import com.cactusds.backend.model.User;
import com.cactusds.backend.repository.UserRepository;
import com.cactusds.backend.security.CurrentUserResolver;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/admin/users")
public class UserController {

    private final UserRepository userRepository;
    private final CurrentUserResolver currentUserResolver;
    private final NoteInterneRepository noteInterneRepository;
    private final AuditLogService auditLogService;

    public UserController(UserRepository userRepository, CurrentUserResolver currentUserResolver,
                          NoteInterneRepository noteInterneRepository, AuditLogService auditLogService) {
        this.userRepository = userRepository;
        this.currentUserResolver = currentUserResolver;
        this.noteInterneRepository = noteInterneRepository;
        this.auditLogService = auditLogService;
    }

    @GetMapping
    public List<AdminUserResponse> listAll() {
        return userRepository.findAll().stream().map(AdminUserResponse::from).toList();
    }

    @GetMapping("/{id}")
    public ResponseEntity<AdminUserResponse> getOne(@PathVariable Long id) {
        return userRepository.findById(id)
                .map(u -> ResponseEntity.ok(AdminUserResponse.from(u)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/notes")
    public List<NoteInterneResponse> listNotes(@PathVariable Long id) {
        if (!userRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return noteInterneRepository.findByUser_IdOrderByCreatedAtDesc(id).stream()
                .map(NoteInterneResponse::from).toList();
    }

    @PostMapping("/{id}/notes")
    public ResponseEntity<NoteInterneResponse> addNote(@PathVariable Long id, @Valid @RequestBody NoteInterneRequest req,
                                                       Authentication authentication) {
        User client = userRepository.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        User admin = currentUserResolver.resolve(authentication);
        NoteInterne note = NoteInterne.builder().user(client).auteur(admin).contenu(req.contenu().trim()).build();
        noteInterneRepository.save(note);
        return ResponseEntity.status(HttpStatus.CREATED).body(NoteInterneResponse.from(note));
    }

    @PutMapping("/{id}/role")
    public ResponseEntity<AdminUserResponse> updateRole(@PathVariable Long id,
                                                        @Valid @RequestBody RoleUpdateRequest req,
                                                        Authentication authentication) {
        User self = currentUserResolver.resolve(authentication);
        if (self.getId().equals(id)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "You cannot change your own role");
        }
        return userRepository.findById(id)
                .map(u -> {
                    if (u.getRole() == Role.ADMIN && req.role() != Role.ADMIN) {
                        long remainingAdmins = userRepository.findAll().stream().filter(x -> x.getRole() == Role.ADMIN).count();
                        if (remainingAdmins <= 1) {
                            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot demote the last remaining admin");
                        }
                    }
                    Role previousRole = u.getRole();
                    u.setRole(req.role());
                    userRepository.save(u);
                    auditLogService.log(self, "ROLE_CHANGE",
                            "Rôle de " + u.getEmail() + " changé de " + previousRole + " à " + req.role());
                    return ResponseEntity.ok(AdminUserResponse.from(u));
                })
                .orElse(ResponseEntity.notFound().build());
    }
}