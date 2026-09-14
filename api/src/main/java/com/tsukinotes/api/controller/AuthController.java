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

    @PostMapping("/identity")
    public Identity pickName(@RequestBody NameRequest request) {
        return identityService.findOrCreate(request.getName());
    }

    public static class NameRequest {
        private String name;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }
}
