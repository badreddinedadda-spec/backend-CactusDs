package com.cactusds.backend.controller;

import com.cactusds.backend.dto.UpdateProfileRequest;
import com.cactusds.backend.dto.UserResponse;
import com.cactusds.backend.model.User;
import com.cactusds.backend.repository.UserRepository;
import com.cactusds.backend.security.CurrentUserResolver;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AccountControllerTest {

    private final List<User> saved = new ArrayList<>();
    private User me;
    private User other;
    private AccountController controller;
    private CurrentUserResolver resolver;
    private UserRepository repo;

    @BeforeEach
    void setUp() {
        me = User.builder().id(1L).email("me@cactusds.ma").fullName("Old Name").build();
        other = User.builder().id(2L).email("other@cactusds.ma").fullName("Other Person").build();
        repo = (UserRepository) Proxy.newProxyInstance(
                UserRepository.class.getClassLoader(), new Class[]{UserRepository.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "findByEmail":
                            if (me.getEmail().equals(args[0])) return Optional.of(me);
                            if (other.getEmail().equals(args[0])) return Optional.of(other);
                            return Optional.empty();
                        case "save": saved.add((User) args[0]); return args[0];
                        case "toString": return "fake-repo";
                        case "hashCode": return 1;
                        case "equals": return proxy == args[0];
                        default: throw new UnsupportedOperationException(method.getName());
                    }
                });
        resolver = new CurrentUserResolver(repo);
        controller = new AccountController(resolver, repo);
    }

    private Authentication authAs(String email) {
        var principal = new org.springframework.security.core.userdetails.User(email, "x", List.of());
        return new UsernamePasswordAuthenticationToken(principal, null, List.of());
    }

    @Test
    void updatesOnlyTheCallersOwnNameAndTrimsIt() {
        ResponseEntity<UserResponse> res =
                controller.updateProfile(new UpdateProfileRequest("  Badr Dadda  "), authAs("me@cactusds.ma"));

        assertEquals(200, res.getStatusCode().value());
        assertEquals("Badr Dadda", res.getBody().fullName());
        assertEquals("Badr Dadda", me.getFullName());
        assertEquals("Other Person", other.getFullName());
        assertEquals(1, saved.size());
        assertTrue(saved.get(0) == me);
    }

    @Test
    void responseNeverChangesEmailOrRole() {
        UserResponse body = controller.updateProfile(new UpdateProfileRequest("New"), authAs("me@cactusds.ma")).getBody();
        assertEquals("me@cactusds.ma", body.email());
        assertEquals("CLIENT", body.role());
    }

    @Test
    void requestValidationRejectsBlankAndTooLongNames() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            Validator v = factory.getValidator();
            assertTrue(v.validate(new UpdateProfileRequest("   ")).size() > 0);
            assertTrue(v.validate(new UpdateProfileRequest(null)).size() > 0);
            assertTrue(v.validate(new UpdateProfileRequest("x".repeat(151))).size() > 0);
            assertFalse(v.validate(new UpdateProfileRequest("x".repeat(150))).size() > 0);
        }
    }
}