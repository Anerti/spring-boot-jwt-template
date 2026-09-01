package com.techindna.springbootjwttemplate.controller.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import com.techindna.springbootjwttemplate.conf.FacadeIT;
import com.techindna.springbootjwttemplate.dto.MessageBody;
import com.techindna.springbootjwttemplate.dto.RegisterInput;
import com.techindna.springbootjwttemplate.entity.email.EmailDetails;
import com.techindna.springbootjwttemplate.entity.enums.UserRole;
import com.techindna.springbootjwttemplate.repository.AuthRepository;
import com.techindna.springbootjwttemplate.repository.model.JUser;
import com.techindna.springbootjwttemplate.service.mail.EmailService;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.TestConstructor.AutowireMode;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@TestConstructor(autowireMode = AutowireMode.ALL)
class RegisterIT extends FacadeIT {

    private final TestRestTemplate restTemplate;
    private final AuthRepository authRepository;
    private final PasswordEncoder passwordEncoder;
    private final StringRedisTemplate redis;

    @MockitoBean private EmailService emailService;

    RegisterIT(
            TestRestTemplate restTemplate,
            AuthRepository authRepository,
            PasswordEncoder passwordEncoder,
            StringRedisTemplate redis) {
        this.restTemplate = restTemplate;
        this.authRepository = authRepository;
        this.passwordEncoder = passwordEncoder;
        this.redis = redis;
    }

    @BeforeEach
    void clean() {
        authRepository.deleteAll();
        redis.keys("verification:*").forEach(redis::delete);
    }

    @Test
    void valid_registration_creates_unverified_user_and_sends_verification_email() {
        ResponseEntity<MessageBody> response = register(validInput());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assert response.getBody() != null;
        assertThat(response.getBody().getMessage())
                .isEqualTo("An email has been sent to verify your account");

        JUser persisted = authRepository.findByEmail("jane.doe@example.com").orElseThrow();
        assertThat(persisted.getUsername()).isEqualTo("jane_doe");
        assertThat(persisted.getFirstName()).isEqualTo("Jane");
        assertThat(persisted.getLastName()).isEqualTo("Doe");
        assertThat(persisted.getVerified()).isFalse();
        assertThat(persisted.getRole()).isEqualTo(UserRole.CUSTOMER);
        assertThat(persisted.getPassword()).isNotEqualTo("StrongPass12!");
        assertThat(passwordEncoder.matches("StrongPass12!", persisted.getPassword())).isTrue();

        Set<String> keys = redis.keys("verification:*");
        assertThat(keys).hasSize(1);
        String token = keys.iterator().next().substring("verification:".length());
        assertThat(redis.opsForValue().get("verification:" + token)).isEqualTo("jane.doe@example.com");

        ArgumentCaptor<EmailDetails> captor = ArgumentCaptor.forClass(EmailDetails.class);
        verify(emailService).sendMail(captor.capture());
        EmailDetails email = captor.getValue();
        assertThat(email.getRecipient()).isEqualTo("jane.doe@example.com");
        assertThat(email.getSubject()).isEqualTo("Email Verification");
        assertThat(email.getTemplate()).isEqualTo("mail/verification");
        assertThat(email.getVariables()).containsEntry("verificationUrl", "http://localhost:8080/auth/verification/" + token)
                .containsEntry("firstName", "Jane")
                .containsEntry("lastName", "Doe")
                .containsEntry("username", "jane_doe")
                .containsEntry("email", "jane.doe@example.com");
    }

    @Test
    void registration_normalizes_email_to_lowercase() {
        ResponseEntity<MessageBody> response = register(new RegisterInput(
                "jane_doe2", "StrongPass12!", "StrongPass12!",
                "Jane", "Doe", "Jane.Doe@Example.COM"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(authRepository.findByEmail("jane.doe@example.com")).isPresent();
        assertThat(authRepository.findByEmail("Jane.Doe@Example.COM")).isEmpty();
        assertThat(redis.keys("verification:*")).hasSize(1);
    }

    @Test
    void duplicate_email_returns_conflict() {
        saveUser();

        ResponseEntity<String> response = registerError(new RegisterInput(
                "fresh_user", "StrongPass12!", "StrongPass12!",
                "Jane", "Doe", "taken@example.com"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).contains("You cannot use this email address");
        assertThat(redis.keys("verification:*")).isEmpty();
    }

    @Test
    void duplicate_username_returns_conflict() {
        saveUser();

        ResponseEntity<String> response = registerError(new RegisterInput(
                "taken_user", "StrongPass12!", "StrongPass12!",
                "Jane", "Doe", "fresh@example.com"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).contains("You cannot use this username");
        assertThat(redis.keys("verification:*")).isEmpty();
    }

    @Test
    void blank_email_is_unprocessable() {
        ResponseEntity<String> response = registerError(new RegisterInput(
                "jane_doe", "StrongPass12!", "StrongPass12!",
                "Jane", "Doe", ""));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(response.getBody()).contains("email is required and cannot be blank");
    }

    @Test
    void invalid_email_format_is_unprocessable() {
        ResponseEntity<String> response = registerError(new RegisterInput(
                "jane_doe", "StrongPass12!", "StrongPass12!",
                "Jane", "Doe", "not-an-email"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(response.getBody()).contains("Email not-an-email is not valid");
    }

    @Test
    void blank_password_is_unprocessable() {
        ResponseEntity<String> response = registerError(new RegisterInput(
                "jane_doe", "", "",
                "Jane", "Doe", "jane.doe@example.com"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(response.getBody()).contains("password is required and cannot be blank");
    }

    @Test
    void short_password_is_unprocessable() {
        ResponseEntity<String> response = registerError(new RegisterInput(
                "jane_doe", "Short1!", "Short1!",
                "Jane", "Doe", "jane.doe@example.com"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(response.getBody()).contains("Password must be at least 12 characters");
    }

    @Test
    void password_without_uppercase_is_unprocessable() {
        ResponseEntity<String> response = registerError(new RegisterInput(
                "jane_doe", "lowercase12!", "lowercase12!",
                "Jane", "Doe", "jane.doe@example.com"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(response.getBody()).contains("Password must contain at least one uppercase character");
    }

    @Test
    void password_without_digit_is_unprocessable() {
        ResponseEntity<String> response = registerError(new RegisterInput(
                "jane_doe", "Password!abc", "Password!abc",
                "Jane", "Doe", "jane.doe@example.com"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(response.getBody()).contains("Password must contain at least one digit");
    }

    @Test
    void password_without_special_character_is_unprocessable() {
        ResponseEntity<String> response = registerError(new RegisterInput(
                "jane_doe", "Password123abc", "Password123abc",
                "Jane", "Doe", "jane.doe@example.com"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(response.getBody()).contains("Password must contain at least one special character");
    }

    @Test
    void mismatched_passwords_are_unprocessable() {
        ResponseEntity<String> response = registerError(new RegisterInput(
                "jane_doe", "StrongPass12!", "Different12!",
                "Jane", "Doe", "jane.doe@example.com"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("Passwords do not match");
    }

    @Test
    void blank_confirm_password_is_unprocessable() {
        ResponseEntity<String> response = registerError(new RegisterInput(
                "jane_doe", "StrongPass12!", "",
                "Jane", "Doe", "jane.doe@example.com"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(response.getBody()).contains("confirmPassword is required and cannot be blank");
    }

    @Test
    void invalid_first_name_is_unprocessable() {
        ResponseEntity<String> response = registerError(new RegisterInput(
                "jane_doe", "StrongPass12!", "StrongPass12!",
                "john", "Doe", "jane.doe@example.com"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(response.getBody()).contains("firstName must start with a capital letter");
    }

    @Test
    void invalid_last_name_is_unprocessable() {
        ResponseEntity<String> response = registerError(new RegisterInput(
                "jane_doe", "StrongPass12!", "StrongPass12!",
                "Jane", "D0e", "jane.doe@example.com"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(response.getBody()).contains("lastName must start with a capital letter");
    }

    @Test
    void blank_username_is_unprocessable() {
        ResponseEntity<String> response = registerError(new RegisterInput(
                "", "StrongPass12!", "StrongPass12!",
                "Jane", "Doe", "jane.doe@example.com"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(response.getBody()).contains("username is required and cannot be blank");
    }

    @Test
    void invalid_username_is_unprocessable() {
        ResponseEntity<String> response = registerError(new RegisterInput(
                "a", "StrongPass12!", "StrongPass12!",
                "Jane", "Doe", "jane.doe@example.com"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(response.getBody()).contains("Username a is invalid");
    }

    private void saveUser() {
        authRepository.save(JUser.builder()
                .username("taken_user")
                .password("encoded-placeholder")
                .firstName("John")
                .lastName("Doe")
                .email("taken@example.com")
                .verified(true)
                .role(UserRole.CUSTOMER)
                .build());
    }

    private RegisterInput validInput() {
        return new RegisterInput(
                "jane_doe", "StrongPass12!", "StrongPass12!",
                "Jane", "Doe", "jane.doe@example.com");
    }

    private ResponseEntity<MessageBody> register(RegisterInput request) {
        return restTemplate.exchange(
                "/auth/register", HttpMethod.POST, new HttpEntity<>(request), MessageBody.class);
    }

    private ResponseEntity<String> registerError(RegisterInput request) {
        return restTemplate.exchange(
                "/auth/register", HttpMethod.POST, new HttpEntity<>(request), String.class);
    }
}
