package com.cactusds.backend.comon.security;

import com.cactusds.backend.model.User;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CustomOAuth2UserServiceTest {

    @Test
    void googleSeedsTheNameOfANewAccount() {
        User u = new User();
        CustomOAuth2UserService.applyGoogleName(u, "Google Name");
        assertEquals("Google Name", u.getFullName());
    }

    @Test
    void googleSeedsABlankName() {
        User u = new User();
        u.setFullName("  ");
        CustomOAuth2UserService.applyGoogleName(u, "Google Name");
        assertEquals("Google Name", u.getFullName());
    }

    @Test
    void googleNeverOverwritesAnEditedName() {
        User u = new User();
        u.setFullName("Name I chose");
        CustomOAuth2UserService.applyGoogleName(u, "Google Name");
        assertEquals("Name I chose", u.getFullName());
    }
}