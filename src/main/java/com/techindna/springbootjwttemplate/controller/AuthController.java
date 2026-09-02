package com.techindna.springbootjwttemplate.controller;

import com.techindna.springbootjwttemplate.dto.ChangeEmailInput;
import com.techindna.springbootjwttemplate.dto.ChangePasswordInput;
import com.techindna.springbootjwttemplate.dto.LoginInput;
import com.techindna.springbootjwttemplate.dto.MessageBody;
import com.techindna.springbootjwttemplate.dto.RegisterInput;
import com.techindna.springbootjwttemplate.dto.UnlockAccountInput;
import com.techindna.springbootjwttemplate.dto.VerifyRegistrationResponse;
import com.techindna.springbootjwttemplate.service.auth.ChangeEmailService;
import com.techindna.springbootjwttemplate.service.auth.ChangePasswordService;
import com.techindna.springbootjwttemplate.service.auth.LoginService;
import com.techindna.springbootjwttemplate.service.auth.RegisterService;
import com.techindna.springbootjwttemplate.service.auth.ResendVerificationService;
import com.techindna.springbootjwttemplate.service.auth.UnlockAccountService;
import com.techindna.springbootjwttemplate.service.auth.VerificationService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final ResendVerificationService resendVerificationService;
    private final RegisterService registerService;
    private final LoginService loginService;
    private final ChangePasswordService changePasswordService;
    private final ChangeEmailService changeEmailService;
    private final UnlockAccountService unlockAccountService;
    private final VerificationService verificationService;

    @PostMapping("/register")
    public ResponseEntity<MessageBody> register(@RequestBody RegisterInput request, HttpServletRequest servletRequest) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(registerService.register(request, servletRequest));
    }

    @PostMapping("/login")
    public ResponseEntity<MessageBody> login(@RequestBody LoginInput request, HttpServletRequest servletRequest) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(loginService.login(request, servletRequest));
    }

    @GetMapping("/verification/{token}")
    public ResponseEntity<VerifyRegistrationResponse> verify(
            @PathVariable UUID token, HttpServletRequest servletRequest) {
        return ResponseEntity.status(HttpStatus.OK).body(verificationService.verify(token, servletRequest));
    }

    @PostMapping("/resend-link")
    public ResponseEntity<MessageBody> resendVerificationLink(
            @RequestParam String email, HttpServletRequest servletRequest) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(resendVerificationService.resendVerificationLink(email, servletRequest));
    }

    @PostMapping("/change-password")
    public ResponseEntity<MessageBody> changePassword(
            @RequestBody ChangePasswordInput request, HttpServletRequest servletRequest, Authentication auth) {
        return ResponseEntity.status(HttpStatus.OK)
                .body(changePasswordService.changePassword(request, servletRequest, auth));
    }

    @PostMapping("/change-email")
    public ResponseEntity<MessageBody> changeEmail(
            @RequestBody ChangeEmailInput request, HttpServletRequest servletRequest, Authentication auth) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(changeEmailService.changeEmail(request, servletRequest, auth));
    }

    @PostMapping("/unlock")
    public ResponseEntity<MessageBody> unlockAccount(
            @RequestBody UnlockAccountInput request, HttpServletRequest servletRequest) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(unlockAccountService.unlockAccount(request, servletRequest));
    }
}
