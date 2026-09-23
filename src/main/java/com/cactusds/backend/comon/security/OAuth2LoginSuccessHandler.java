package com.cactusds.backend.comon.security;

import com.cactusds.backend.model.User;
import com.cactusds.backend.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {
    private final UserRepository userRepository;
    private final TwoFactorService twoFactorService;
    private final UserSessionService userSessionService;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    public OAuth2LoginSuccessHandler(UserRepository userRepository, TwoFactorService twoFactorService,
                                     UserSessionService userSessionService) {
        this.userRepository = userRepository;
        this.twoFactorService = twoFactorService;
        this.userSessionService = userSessionService;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        String email = authentication.getPrincipal() instanceof OAuth2User oAuth2User
                ? oAuth2User.getAttribute("email") : null;
        User user = email == null ? null : userRepository.findByEmail(email).orElse(null);

        if (user != null && twoFactorService.isEnabled(user)) {
            twoFactorService.startPendingLogin(request, user);
            response.sendRedirect(frontendUrl + "/hosting/login?twofa=1");
            return;
        }
        if (user != null) {
            userSessionService.recordLogin(request, user);
        }
        response.sendRedirect(frontendUrl);
    }
}