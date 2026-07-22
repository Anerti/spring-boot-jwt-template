package com.techindna.springbootjwttemplate.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PingController {

    @GetMapping("/syn")
    public ResponseEntity<String> syn() {
        return ResponseEntity.ok("syn-ack");
    }
}
