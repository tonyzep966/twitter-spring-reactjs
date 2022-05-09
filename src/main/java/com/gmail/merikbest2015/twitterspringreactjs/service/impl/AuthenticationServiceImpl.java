package com.gmail.merikbest2015.twitterspringreactjs.service.impl;

import com.gmail.merikbest2015.twitterspringreactjs.exception.ApiRequestException;
import com.gmail.merikbest2015.twitterspringreactjs.exception.InputFieldException;
import com.gmail.merikbest2015.twitterspringreactjs.model.User;
import com.gmail.merikbest2015.twitterspringreactjs.repository.UserRepository;
import com.gmail.merikbest2015.twitterspringreactjs.repository.projection.user.AuthUserProjection;
import com.gmail.merikbest2015.twitterspringreactjs.repository.projection.user.UserCommonProjection;
import com.gmail.merikbest2015.twitterspringreactjs.security.JwtProvider;
import com.gmail.merikbest2015.twitterspringreactjs.security.UserPrincipal;
import com.gmail.merikbest2015.twitterspringreactjs.service.AuthenticationService;
import com.gmail.merikbest2015.twitterspringreactjs.service.email.MailSender;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthenticationServiceImpl implements AuthenticationService {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final MailSender mailSender;

    @Override
    public Long getAuthenticatedUserId() {
        return ((UserPrincipal) SecurityContextHolder.getContext().getAuthentication().getPrincipal()).getId();
    }

    @Override
    public User getAuthenticatedUser() {
        Principal principal = SecurityContextHolder.getContext().getAuthentication();
        return userRepository.findByEmail(principal.getName())
                .orElseThrow(() -> new ApiRequestException("User not found", HttpStatus.NOT_FOUND));
    }

    @Override
    public Map<String, Object> login(String email, String password) {
        try {
            authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(email, password));
            AuthUserProjection user = userRepository.findAuthUserByEmail(email)
                    .orElseThrow(() -> new ApiRequestException("User not found", HttpStatus.NOT_FOUND));
            String token = jwtProvider.createToken(email, "USER");
            Map<String, Object> response = new HashMap<>();
            response.put("user", user);
            response.put("token", token);
            return response;
        } catch (AuthenticationException e) {
            throw new ApiRequestException("Incorrect password or email", HttpStatus.FORBIDDEN);
        }
    }

    @Override
    public String registration(String email, String username, String birthday) {
        Optional<User> existingUser = userRepository.findByEmail(email);

        if (existingUser.isEmpty()) {
            User user = new User();
            user.setEmail(email);
            user.setUsername(username);
            user.setFullName(username);
            user.setBirthday(birthday);
            user.setRole("USER");
            // TonyZep966 - Bug fixed, to prevent from throwing null exception and get start when new user created.
            user.setActive(false);
            user.setLikeCount(0L);
            user.setMediaTweetCount(0L);
            user.setTweetCount(0L);
            user.setProfileCustomized(false);
            user.setProfileStarted(false);
            userRepository.save(user);
            return "User data checked.";
        }

        if (!existingUser.get().isActive()) {
            existingUser.get().setUsername(username);
            existingUser.get().setFullName(username);
            existingUser.get().setBirthday(birthday);
            existingUser.get().setRegistrationDate(LocalDateTime.now().withNano(0));
            existingUser.get().setRole("USER");
            userRepository.save(existingUser.get());
            return "User data checked.";
        }
        throw new ApiRequestException("Email has already been taken.", HttpStatus.FORBIDDEN);
    }

    @Override
    @Transactional
    public String sendRegistrationCode(String email) {
        UserCommonProjection user = userRepository.findCommonUserByEmail(email)
                .orElseThrow(() -> new ApiRequestException("User not found", HttpStatus.NOT_FOUND));
        // TonyZep966 - Fix a bug, where cannot see activation code in email
        String activationCode = UUID.randomUUID().toString().substring(0, 7);
        userRepository.updateActivationCode(activationCode, user.getId());
        String subject = "Registration code";
        String template = "registration-template";
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("fullName", user.getFullName());
        attributes.put("registrationCode", activationCode);
        mailSender.sendMessageHtml(user.getEmail(), subject, template, attributes);
        return "Registration code sent successfully";
    }

    @Override
    @Transactional
    public String activateUser(String code) {
        UserCommonProjection user = userRepository.findCommonUserByActivationCode(code)
                .orElseThrow(() -> new ApiRequestException("Activation code not found.", HttpStatus.NOT_FOUND));
        userRepository.updateActivationCode(null, user.getId());
        return "User successfully activated.";
    }

    @Override
    @Transactional
    public Map<String, Object> endRegistration(String email, String password) {
        if (password.length() < 8) {
            throw new ApiRequestException("Your password needs to be at least 8 characters", HttpStatus.BAD_REQUEST);
        }
        AuthUserProjection user = userRepository.findAuthUserByEmail(email)
                .orElseThrow(() -> new ApiRequestException("User not found", HttpStatus.NOT_FOUND));
        userRepository.updatePassword(passwordEncoder.encode(password), user.getId());
        userRepository.updateActiveUserProfile(user.getId());
        String token = jwtProvider.createToken(email, "USER");
        Map<String, Object> response = new HashMap<>();
        response.put("user", user);
        response.put("token", token);
        return response;
    }

    @Override
    public Map<String, Object> getUserByToken() {
        Principal principal = SecurityContextHolder.getContext().getAuthentication();
        AuthUserProjection user = userRepository.findAuthUserByEmail(principal.getName())
                .orElseThrow(() -> new ApiRequestException("User not found", HttpStatus.NOT_FOUND));
        String token = jwtProvider.createToken(user.getEmail(), "USER");
        Map<String, Object> response = new HashMap<>();
        response.put("user", user);
        response.put("token", token);
        return response;
    }

    @Override
    public String findEmail(String email) {
        userRepository.findCommonUserByEmail(email)
                .orElseThrow(() -> new ApiRequestException("Email not found", HttpStatus.NOT_FOUND));
        return "Reset password code is send to your E-mail";
    }

    @Override
    public AuthUserProjection findByPasswordResetCode(String code) {
        return userRepository.findByPasswordResetCode(code)
                .orElseThrow(() -> new ApiRequestException("Password reset code is invalid!", HttpStatus.BAD_REQUEST));
    }

    @Override
    @Transactional
    public String sendPasswordResetCode(String email) {
        UserCommonProjection user = userRepository.findCommonUserByEmail(email)
                .orElseThrow(() -> new ApiRequestException("Email not found", HttpStatus.NOT_FOUND));
        // TonyZep966 - Fix bug here too, for code not seen in email
        String resetCode = UUID.randomUUID().toString().substring(0, 7);
        userRepository.updatePasswordResetCode(resetCode, user.getId());
        String subject = "Password reset";
        String template = "password-reset-template";
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("fullName", user.getFullName());
        attributes.put("passwordResetCode", resetCode);
        mailSender.sendMessageHtml(user.getEmail(), subject, template, attributes);
        return "Reset password code is send to your E-mail";
    }

    @Override
    @Transactional
    public String passwordReset(String email, String password, String password2) {
        checkMatchPasswords(password, password2);
        UserCommonProjection user = userRepository.findCommonUserByEmail(email)
                .orElseThrow(() -> new InputFieldException(HttpStatus.NOT_FOUND, Map.of("email", "Email not found")));
        userRepository.updatePassword(passwordEncoder.encode(password), user.getId());
        userRepository.updatePasswordResetCode(null, user.getId());
        return "Password successfully changed!";
    }

    @Override
    @Transactional
    public String currentPasswordReset(String currentPassword, String password, String password2) {
        Long userId = getAuthenticatedUserId();
        String userPassword = userRepository.getUserPasswordById(userId);

        if (!passwordEncoder.matches(currentPassword, userPassword)) {
            processPasswordException("currentPassword", "The password you entered was incorrect.", HttpStatus.NOT_FOUND);
        }
        checkMatchPasswords(password, password2);
        userRepository.updatePassword(passwordEncoder.encode(password), userId);
        return "Your password has been successfully updated.";
    }

    private void checkMatchPasswords(String password, String password2) {
        if (password != null && !password.equals(password2)) {
            processPasswordException("password", "Passwords do not match.", HttpStatus.BAD_REQUEST);
        }
    }

    private void processPasswordException(String paramName, String exceptionMessage, HttpStatus status) {
        throw new InputFieldException(status, Map.of(paramName, exceptionMessage));
    }
}
