package com.cactusds.backend.controller;

import com.cactusds.backend.comon.security.UserSessionService;
import com.cactusds.backend.model.User;
import com.cactusds.backend.security.CurrentUserResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Lives under /api/client/**, so SecurityConfig already requires an authenticated CLIENT or
 * ADMIN: the same two endpoints serve the admin "Paramètres > Sessions" screen and the client
 * "Sécurité" screen — each only ever sees their own sessions.
 */
@RestController
@RequestMapping("/api/client/sessions")
public class SessionController {

    private final CurrentUserResolver currentUserResolver;
    private final UserSessionService userSessionService;

    public SessionController(CurrentUserResolver currentUserResolver, UserSessionService userSessionService) {
        this.currentUserResolver = currentUserResolver;
        this.userSessionService = userSessionService;
    }

    @GetMapping
    public ResponseEntity<?> list(Authentication authentication, HttpServletRequest request) {
        User user = currentUserResolver.resolve(authentication);
        return ResponseEntity.ok(userSessionService.listActive(user, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> revoke(@PathVariable Long id, Authentication authentication, HttpServletRequest request) {
        User user = currentUserResolver.resolve(authentication);
        UserSessionService.RevokeResult result = userSessionService.revoke(user, id, request);
        return switch (result) {
            case OK -> ResponseEntity.ok(Map.of("status", "ok"));
            case NOT_FOUND -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "not_found"));
            case IS_CURRENT_SESSION -> ResponseEntity.badRequest().body(Map.of("error", "is_current_session"));
        };
    }
}