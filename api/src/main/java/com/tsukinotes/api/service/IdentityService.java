package com.tsukinotes.api.service;

import com.tsukinotes.api.domain.Identity;
import com.tsukinotes.api.repository.IdentityRepository;
import org.springframework.stereotype.Service;

@Service
public class IdentityService {

    private final IdentityRepository identityRepository;

    public IdentityService(IdentityRepository identityRepository) {
        this.identityRepository = identityRepository;
    }

    public Identity findOrCreate(String name) {
        return identityRepository.findByName(name)
                .orElseGet(() -> {
                    Identity identity = new Identity();
                    identity.setName(name);
                    return identityRepository.save(identity);
                });
    }
}
