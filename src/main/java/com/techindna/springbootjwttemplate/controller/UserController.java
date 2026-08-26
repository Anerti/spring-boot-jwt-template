package com.techindna.springbootjwttemplate.controller;

import com.techindna.springbootjwttemplate.dto.UpdateUserInput;
import com.techindna.springbootjwttemplate.entity.User;
import com.techindna.springbootjwttemplate.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/{userId}")
    public ResponseEntity<User> getUser(@PathVariable UUID userId, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.OK).body(userService.getUserById(userId, request));
    }

    @PatchMapping("/{userId}")
    public ResponseEntity<User> updateUser(
            @PathVariable UUID userId,
            @RequestBody UpdateUserInput input,
            HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.OK).body(userService.updateUser(userId, input, request));
    }
}
