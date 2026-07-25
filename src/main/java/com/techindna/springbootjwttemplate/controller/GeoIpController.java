package com.techindna.springbootjwttemplate.controller;

import com.techindna.springbootjwttemplate.entity.GeoIpResponse;
import com.techindna.springbootjwttemplate.service.GeoIpService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class GeoIpController {

    private final GeoIpService geoIpService;

    @GetMapping("/geoip")
    public ResponseEntity<GeoIpResponse> getGeoIp(HttpServletRequest servletRequest) {
        return ResponseEntity.status(HttpStatus.OK)
                .body(geoIpService.lookup(geoIpService.extractClientIp(servletRequest)));
    }

    @GetMapping("/geoip/{ip}")
    public ResponseEntity<GeoIpResponse> getGeoIpByIp(@PathVariable String ip) {
        return ResponseEntity.status(HttpStatus.OK).body(geoIpService.lookup(ip));
    }


}
