package com.cactusds.backend.controller;

import com.cactusds.backend.dto.AdminUserResponse;
import com.cactusds.backend.dto.RoleUpdateRequest;
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

    public UserController(UserRepository userRepository, CurrentUserResolver currentUserResolver) {
        this.userRepository = userRepository;
        this.currentUserResolver = currentUserResolver;
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
                    u.setRole(req.role());
                    userRepository.save(u);
                    return ResponseEntity.ok(AdminUserResponse.from(u));
                })
                .orElse(ResponseEntity.notFound().build());
    }
}