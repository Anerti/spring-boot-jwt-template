package com.techindna.springbootjwttemplate.controller.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import com.techindna.springbootjwttemplate.conf.FacadeIT;
import com.techindna.springbootjwttemplate.dto.LoginInput;
import com.techindna.springbootjwttemplate.dto.MessageBody;
import com.techindna.springbootjwttemplate.entity.email.EmailDetails;
import com.techindna.springbootjwttemplate.entity.enums.HostStatus;
import com.techindna.springbootjwttemplate.entity.enums.UserRole;
import com.techindna.springbootjwttemplate.entity.enums.UserStatus;
import com.techindna.springbootjwttemplate.repository.AuthRepository;
import com.techindna.springbootjwttemplate.repository.HostRepository;
import com.techindna.springbootjwttemplate.repository.model.JHost;
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
class LoginIT extends FacadeIT {

    private final TestRestTemplate restTemplate;
    private final AuthRepository authRepository;
    private final HostRepository hostRepository;
    private final PasswordEncoder passwordEncoder;
    private final StringRedisTemplate redis;

    @MockitoBean private EmailService emailService;

    LoginIT(
            TestRestTemplate restTemplate,
            AuthRepository authRepository,
            HostRepository hostRepository,
            PasswordEncoder passwordEncoder,
            StringRedisTemplate redis) {
        this.restTemplate = restTemplate;
        this.authRepository = authRepository;
        this.hostRepository = hostRepository;
        this.passwordEncoder = passwordEncoder;
        this.redis = redis;
    }

    @BeforeEach
    void clean() {
        authRepository.deleteAll();
        hostRepository.deleteAll();
        redis.keys("verification:*").forEach(redis::delete);
        redis.keys("failed_logins:*").forEach(redis::delete);
    }

    @Test
    void valid_email_login_sends_verification_email() {
        saveUser(UserStatus.ACTIVE, true);

        ResponseEntity<MessageBody> response = login(validEmailInput());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage())
                .isEqualTo("A verification link has been sent to your email");

        Set<String> keys = redis.keys("verification:*");
        assertThat(keys).hasSize(1);
        String token = keys.iterator().next().substring("verification:".length());
        assertThat(redis.opsForValue().get("verification:" + token))
                .isEqualTo("jane.doe@example.com");

        ArgumentCaptor<EmailDetails> captor = ArgumentCaptor.forClass(EmailDetails.class);
        verify(emailService).sendMail(captor.capture());
        assertThat(captor.getValue().getRecipient()).isEqualTo("jane.doe@example.com");
        assertThat(captor.getValue().getSubject()).isEqualTo("Login Verification");
        assertThat(captor.getValue().getTemplate()).isEqualTo("mail/login-verification");
    }

    @Test
    void valid_username_login_sends_verification_email() {
        saveUser(UserStatus.ACTIVE, true);

        ResponseEntity<MessageBody> response = login(validUsernameInput());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage())
                .isEqualTo("A verification link has been sent to your email");

        assertThat(redis.keys("verification:*")).hasSize(1);
        verify(emailService).sendMail(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void non_existent_email_returns_unauthorized() {
        saveUser(UserStatus.ACTIVE, true);

        ResponseEntity<String> response = loginError(
                new LoginInput(null, "nobody@example.com", "StrongPass12!"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).contains("Invalid credentials");
        assertThat(redis.keys("verification:*")).isEmpty();
    }

    @Test
    void wrong_password_returns_unauthorized() {
        saveUser(UserStatus.ACTIVE, true);

        ResponseEntity<String> response = loginError(
                new LoginInput(null, "jane.doe@example.com", "WrongPassword1!"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).contains("Invalid credentials");
        assertThat(response.getBody()).contains("4 attempt(s) left");
    }

    @Test
    void blank_email_and_username_are_unprocessable() {
        ResponseEntity<String> response = loginError(new LoginInput("", "", "StrongPass12!"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(response.getBody()).contains("Username or email is required and cannot be blank");
    }

    @Test
    void invalid_email_format_is_unprocessable() {
        ResponseEntity<String> response = loginError(
                new LoginInput(null, "not-an-email", "StrongPass12!"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(response.getBody()).contains("Email not-an-email is not valid");
    }

    @Test
    void invalid_username_format_is_unprocessable() {
        ResponseEntity<String> response = loginError(
                new LoginInput("a", null, "StrongPass12!"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(response.getBody()).contains("Username a is invalid");
    }

    @Test
    void blank_password_is_unprocessable() {
        ResponseEntity<String> response = loginError(
                new LoginInput(null, "jane.doe@example.com", ""));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(response.getBody()).contains("password is required and cannot be blank");
    }

    @Test
    void unverified_account_returns_forbidden() {
        saveUser(UserStatus.ACTIVE, false);

        ResponseEntity<String> response = loginError(validEmailInput());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).contains("Account has not been verified");
        assertThat(redis.keys("verification:*")).isEmpty();
    }

    @Test
    void locked_account_returns_forbidden() {
        saveUser(UserStatus.LOCKED, true);

        ResponseEntity<String> response = loginError(validEmailInput());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).contains("Account locked");
        assertThat(redis.keys("verification:*")).isEmpty();
    }

    @Test
    void fifth_failed_attempt_locks_account() {
        JUser user = saveUser(UserStatus.ACTIVE, true);

        for (int i = 0; i < 5; i++) {
            loginError(new LoginInput(null, "jane.doe@example.com", "WrongPassword1!"));
        }

        JUser locked = authRepository.findById(user.getId()).orElseThrow();
        assertThat(locked.getStatus()).isEqualTo(UserStatus.LOCKED);

        ResponseEntity<String> finalAttempt = loginError(
                new LoginInput(null, "jane.doe@example.com", "WrongPassword1!"));
        assertThat(finalAttempt.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(finalAttempt.getBody()).contains("Account locked");
    }

    @Test
    void banned_host_returns_forbidden() {
        JUser user = saveUser(UserStatus.ACTIVE, true);
        saveBannedHost(user);

        ResponseEntity<String> response = loginError(validEmailInput());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).contains("is banned from accessing this account");
        assertThat(redis.keys("verification:*")).isEmpty();
    }

    @Test
    void email_login_normalizes_to_lowercase() {
        saveUser(UserStatus.ACTIVE, true);

        ResponseEntity<MessageBody> response = login(
                new LoginInput(null, "Jane.Doe@Example.COM", "StrongPass12!"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage())
                .isEqualTo("A verification link has been sent to your email");
    }

    private JUser saveUser(UserStatus status, boolean verified) {
        return authRepository.save(JUser.builder()
                .username("jane_doe")
                .password(passwordEncoder.encode("StrongPass12!"))
                .firstName("Jane")
                .lastName("Doe")
                .email("jane.doe@example.com")
                .verified(verified)
                .role(UserRole.CUSTOMER)
                .status(status)
                .build());
    }

    private void saveBannedHost(JUser user) {
        hostRepository.save(JHost.builder()
                .user(user)
                .ipAddress("127.0.0.1")
                .status(HostStatus.BANNED)
                .build());
    }

    private LoginInput validEmailInput() {
        return new LoginInput(null, "jane.doe@example.com", "StrongPass12!");
    }

    private LoginInput validUsernameInput() {
        return new LoginInput("jane_doe", null, "StrongPass12!");
    }

    private ResponseEntity<MessageBody> login(LoginInput request) {
        return restTemplate.exchange(
                "/auth/login", HttpMethod.POST, new HttpEntity<>(request), MessageBody.class);
    }

    private ResponseEntity<String> loginError(LoginInput request) {
        return restTemplate.exchange(
                "/auth/login", HttpMethod.POST, new HttpEntity<>(request), String.class);
    }
}
