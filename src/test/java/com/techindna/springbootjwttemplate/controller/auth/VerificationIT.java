package com.techindna.springbootjwttemplate.controller.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.techindna.springbootjwttemplate.conf.FacadeIT;
import com.techindna.springbootjwttemplate.dto.VerifyRegistrationResponse;
import com.techindna.springbootjwttemplate.entity.enums.HostStatus;
import com.techindna.springbootjwttemplate.entity.enums.UserRole;
import com.techindna.springbootjwttemplate.entity.enums.UserStatus;
import com.techindna.springbootjwttemplate.repository.AuthRepository;
import com.techindna.springbootjwttemplate.repository.HostRepository;
import com.techindna.springbootjwttemplate.repository.model.JHost;
import com.techindna.springbootjwttemplate.repository.model.JUser;
import com.techindna.springbootjwttemplate.service.redis.VerificationCodeStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.TestConstructor.AutowireMode;

@TestConstructor(autowireMode = AutowireMode.ALL)
class VerificationIT extends FacadeIT {

    private static final String TOKEN = "3f4e5d6a-7b8c-9d0e-1f2a-3b4c5d6e7f8a";

    private final TestRestTemplate restTemplate;
    private final AuthRepository authRepository;
    private final HostRepository hostRepository;
    private final StringRedisTemplate redis;
    private final VerificationCodeStore verificationCodeStore;

    VerificationIT(
            TestRestTemplate restTemplate,
            AuthRepository authRepository,
            HostRepository hostRepository,
            StringRedisTemplate redis,
            VerificationCodeStore verificationCodeStore) {
        this.restTemplate = restTemplate;
        this.authRepository = authRepository;
        this.hostRepository = hostRepository;
        this.redis = redis;
        this.verificationCodeStore = verificationCodeStore;
    }

    @BeforeEach
    void clean() {
        hostRepository.deleteAll();
        authRepository.deleteAll();
        redis.keys("verification:*").forEach(redis::delete);
        redis.keys("pending_email:*").forEach(redis::delete);
    }

    @Test
    void registration_verify_marks_user_verified_and_returns_jwt() {
        JUser user = saveUser(false, UserStatus.ACTIVE, "jane.doe@example.com");
        verificationCodeStore.saveToken("jane.doe@example.com", TOKEN);

        ResponseEntity<VerifyRegistrationResponse> response = verify();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assert response.getBody() != null;
        assertThat(response.getBody().getToken()).isNotBlank();
        assertThat(response.getBody().getUser().username()).isEqualTo("jane_doe");
        assertThat(authRepository.findByEmail("jane.doe@example.com").orElseThrow().getVerified()).isTrue();
        assertThat(redis.opsForValue().get("verification:" + TOKEN)).isNull();

        assertThat(hostRepository.findByIpAddressAndUser_Id("127.0.0.1", user.getId())).isPresent();
    }

    @Test
    void unlock_verify_activates_account_and_resets_failed_logins() {
        JUser user = saveUser(true, UserStatus.LOCKED, "jane.doe@example.com");
        redis.opsForValue().set("failed_logins:" + user.getId().toString(), "5");
        verificationCodeStore.saveToken("jane.doe@example.com", TOKEN);

        ResponseEntity<VerifyRegistrationResponse> response = verify();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assert response.getBody() != null;
        assertThat(response.getBody().getToken()).isNotBlank();
        assertThat(authRepository.findByEmail("jane.doe@example.com").orElseThrow().getStatus())
                .isEqualTo(UserStatus.ACTIVE);
        assertThat(redis.opsForValue().get("failed_logins:" + user.getId().toString())).isNull();
    }

    @Test
    void change_email_verify_updates_email() {
        saveUser(true, UserStatus.ACTIVE, "old@example.com");
        verificationCodeStore.saveToken("old@example.com", TOKEN);
        verificationCodeStore.savePendingEmail(TOKEN, "new@example.com");

        ResponseEntity<VerifyRegistrationResponse> response = verify();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assert response.getBody() != null;
        assertThat(response.getBody().getUser().email()).isEqualTo("new@example.com");
        assertThat(authRepository.findByEmail("new@example.com")).isPresent();
        assertThat(authRepository.findByEmail("old@example.com")).isEmpty();
        assertThat(redis.opsForValue().get("pending_email:" + TOKEN)).isNull();
        assertThat(redis.opsForValue().get("verification:" + TOKEN)).isNull();
    }

    @Test
    void invalid_token_returns_unauthorized() {
        ResponseEntity<String> response = verifyError();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).contains("Invalid or expired token");
    }

    @Test
    void token_without_matching_user_returns_unauthorized() {
        verificationCodeStore.saveToken("ghost@example.com", TOKEN);

        ResponseEntity<String> response = verifyError();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).contains("Invalid or expired token");
    }

    @Test
    void consumed_token_cannot_be_reused() {
        saveUser(false, UserStatus.ACTIVE, "jane.doe@example.com");
        verificationCodeStore.saveToken("jane.doe@example.com", TOKEN);

        ResponseEntity<VerifyRegistrationResponse> first = verify();
        ResponseEntity<String> second = verifyError();

        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(second.getBody()).contains("Invalid or expired token");
    }

    @Test
    void banned_host_returns_forbidden() {
        saveUser(false, UserStatus.ACTIVE, "jane.doe@example.com");
        verificationCodeStore.saveToken("jane.doe@example.com", TOKEN);
        saveHost();

        ResponseEntity<String> response = verifyError();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).contains("is banned from accessing this account");
    }

    @Test
    void login_verify_returns_jwt_without_db_mutation() {
        saveUser(true, UserStatus.ACTIVE, "jane.doe@example.com");
        verificationCodeStore.saveToken("jane.doe@example.com", TOKEN);

        ResponseEntity<VerifyRegistrationResponse> response = verify();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assert response.getBody() != null;
        assertThat(response.getBody().getToken()).isNotBlank();
        assertThat(authRepository.findByEmail("jane.doe@example.com").orElseThrow().getVerified()).isTrue();
    }

    private JUser saveUser(boolean verified, UserStatus status, String email) {
        return authRepository.save(JUser.builder()
                .username("jane_doe")
                .password("encoded-placeholder")
                .firstName("Jane")
                .lastName("Doe")
                .email(email)
                .verified(verified)
                .role(UserRole.CUSTOMER)
                .status(status)
                .build());
    }

    private void saveHost() {
        JUser user = authRepository.findByEmail("jane.doe@example.com").orElseThrow();
        hostRepository.save(JHost.builder()
                .user(user)
                .ipAddress("127.0.0.1")
                .status(HostStatus.BANNED)
                .build());
    }

    private ResponseEntity<VerifyRegistrationResponse> verify() {
        return restTemplate.exchange(
                "/auth/verification/" + VerificationIT.TOKEN, HttpMethod.GET, HttpEntity.EMPTY, VerifyRegistrationResponse.class);
    }

    private ResponseEntity<String> verifyError() {
        return restTemplate.exchange(
                "/auth/verification/" + VerificationIT.TOKEN, HttpMethod.GET, HttpEntity.EMPTY, String.class);
    }
}
