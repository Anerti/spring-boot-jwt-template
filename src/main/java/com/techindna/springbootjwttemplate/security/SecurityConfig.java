package com.techindna.springbootjwttemplate.security;

import com.techindna.springbootjwttemplate.security.jwt.JwtAuthenticationFilter;
import com.techindna.springbootjwttemplate.exception.ErrorBody;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http, JwtAuthenticationFilter jwtAuthFilter) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(
                        session ->
                                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(
                        auth ->
                                auth.requestMatchers(HttpMethod.GET, "/syn")
                                        .permitAll()
                                        .requestMatchers(HttpMethod.POST, "/auth/register")
                                        .permitAll()
                                        .requestMatchers(HttpMethod.GET, "/auth/verification/**")
                                        .permitAll()
                                        .requestMatchers(HttpMethod.POST, "/auth/login")
                                        .permitAll()
                                        .requestMatchers(HttpMethod.POST, "/auth/resend-link")
                                        .permitAll()
                                        .requestMatchers(HttpMethod.POST, "/auth/unlock")
                                        .permitAll()
                                        .requestMatchers(HttpMethod.POST, "/auth/change-password")
                                        .authenticated()
                                        .requestMatchers(HttpMethod.POST, "/auth/change-email")
                                        .authenticated()
                                        .requestMatchers(HttpMethod.GET, "/geoip")
                                        .permitAll()
                                        .requestMatchers(HttpMethod.GET, "/geoip/**")
                                        .permitAll()
                                        .requestMatchers(HttpMethod.GET, "/users")
                                        .authenticated()
                                        .requestMatchers(HttpMethod.GET, "/users/**")
                                        .authenticated()
                                        .requestMatchers(HttpMethod.PATCH, "/users/**")
                                        .authenticated()
                                        .requestMatchers(HttpMethod.DELETE, "/users/**")
                                        .authenticated()
                                        .requestMatchers(HttpMethod.GET, "/hosts")
                                        .authenticated()
                                        .requestMatchers(HttpMethod.GET, "/hosts/**")
                                        .authenticated()
                                        .requestMatchers(HttpMethod.PATCH, "/hosts/**")
                                        .authenticated()
                                        .anyRequest()
                                        .authenticated())
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .exceptionHandling(
                        exceptions ->
                                exceptions
                                        .authenticationEntryPoint(
                                                (request, response, authException) ->
                                                        ErrorBody.send(
                                                                response,
                                                                HttpStatus.UNAUTHORIZED,
                                                                "Authentication required."))
                                        .accessDeniedHandler(
                                                (request, response, accessDeniedException) ->
                                                        ErrorBody.send(
                                                                response,
                                                                HttpStatus.FORBIDDEN,
                                                                "Insufficient privileges.")));

        return http.build();
    }
}
