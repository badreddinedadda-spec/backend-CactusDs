package com.cactusds.backend.controller;

import com.cactusds.backend.dto.UpdateProfileRequest;
import com.cactusds.backend.dto.UserResponse;
import com.cactusds.backend.model.User;
import com.cactusds.backend.repository.UserRepository;
import com.cactusds.backend.security.CurrentUserResolver;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/client")
public class AccountController {

    private final CurrentUserResolver currentUserResolver;
    private final UserRepository userRepository;

    public AccountController(CurrentUserResolver currentUserResolver, UserRepository userRepository) {
        this.currentUserResolver = currentUserResolver;
        this.userRepository = userRepository;
    }

    @PutMapping("/profile")
    public ResponseEntity<UserResponse> updateProfile(@Valid @RequestBody UpdateProfileRequest req,
                                                      Authentication authentication) {
        User user = currentUserResolver.resolve(authentication);
        user.setFullName(req.fullName().trim());
        userRepository.save(user);
        return ResponseEntity.ok(new UserResponse(user.getId(), user.getEmail(), user.getFullName(),
                user.getRole().name(), user.getEmailVerified()));
    }
}