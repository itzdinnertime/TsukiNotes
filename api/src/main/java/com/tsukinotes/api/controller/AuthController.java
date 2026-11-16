package com.tsukinotes.api.controller;

import com.tsukinotes.api.domain.Identity;
import com.tsukinotes.api.service.IdentityService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final IdentityService identityService;

    public AuthController(IdentityService identityService) {
        this.identityService = identityService;
    }

    @PostMapping("/signup")
    public Identity signUp(@RequestBody SignUpRequest request) {
        return identityService.signUp(request.name(), request.email(), request.password());
    }

    @PostMapping("/login")
    public Identity logIn(@RequestBody LogInRequest request) {
        return identityService.logIn(request.email(), request.password());
    }

    public record SignUpRequest(String name, String email, String password) {
    }

    public record LogInRequest(String email, String password) {
    }
}
