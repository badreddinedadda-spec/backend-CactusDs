package com.cactusds.backend.comon.security;

import com.cactusds.backend.model.AuthProvider;
import com.cactusds.backend.model.Role;
import com.cactusds.backend.model.User;
import com.cactusds.backend.repository.UserRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CustomOAuth2UserService extends DefaultOAuth2UserService {
    private final UserRepository userRepository;

    public CustomOAuth2UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }
    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(userRequest);

        String email = oAuth2User.getAttribute("email");
        String name = oAuth2User.getAttribute("name");

        User user = userRepository.findByEmail(email).orElseGet(User::new);
        boolean isNewUser = user.getId() == null;

        user.setEmail(email);
        user.setFullName(name);
        user.setEmailVerified(true); // Google has already verified this address
        if (isNewUser) {
            user.setRole(Role.CLIENT);
            user.setAuthProvider(AuthProvider.GOOGLE);
        }
        userRepository.save(user);

        SimpleGrantedAuthority authority = new SimpleGrantedAuthority("ROLE_" + user.getRole().name());
        return new DefaultOAuth2User(List.of(authority), oAuth2User.getAttributes(), "email");
    }
}