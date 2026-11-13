package com.hopr.security;

import java.util.Set;

/** Authenticated principal extracted from the JWT access token. */
public record AuthUser(Long id, String phone, Set<String> roles) {
    public boolean isAdmin() {
        return roles != null && roles.contains("ROLE_ADMIN");
    }
}
