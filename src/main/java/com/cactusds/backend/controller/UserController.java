package com.cactusds.backend.controller;

import com.cactusds.backend.dto.AdminUserResponse;
import com.cactusds.backend.dto.RoleUpdateRequest;
import com.cactusds.backend.model.User;
import com.cactusds.backend.repository.UserRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/admin/users")
public class UserController {

    private final UserRepository userRepository;

    public UserController(UserRepository userRepository) {
        this.userRepository = userRepository;
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
        User self = currentUser(authentication);
        if (self.getId().equals(id)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "You cannot change your own role");
        }
        return userRepository.findById(id)
                .map(u -> {
                    u.setRole(req.role());
                    userRepository.save(u);
                    return ResponseEntity.ok(AdminUserResponse.from(u));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    private User currentUser(Authentication authentication) {
        String email = extractEmail(authentication);
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    }

    private String extractEmail(Authentication authentication) {
        Object principal = authentication.getPrincipal();
        if (principal instanceof OAuth2User oAuth2User) {
            return oAuth2User.getAttribute("email");
        }
        if (principal instanceof UserDetails userDetails) {
            return userDetails.getUsername();
        }
        return authentication.getName();
    }
}