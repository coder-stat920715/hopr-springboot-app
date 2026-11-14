package com.hopr.security;

import com.hopr.config.HoprProperties;
import com.hopr.entity.User;
import com.hopr.exception.UnauthorizedException;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceAndFilterTest {

    private HoprProperties props;
    private JwtService jwt;
    private User user;

    @BeforeEach
    void setUp() {
        props = new HoprProperties();
        jwt = new JwtService(props);
        Set<String> roles = new HashSet<>();
        roles.add("ROLE_USER");
        roles.add("ROLE_ADMIN");
        user = User.builder().id(42L).phone("+919876543210").roles(roles).build();
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void accessTokenRoundTrip() {
        Claims c = jwt.parse(jwt.createAccessToken(user));
        assertThat(jwt.isAccessToken(c)).isTrue();
        assertThat(jwt.isRefreshToken(c)).isFalse();
        assertThat(jwt.userId(c)).isEqualTo(42L);
        AuthUser au = jwt.toAuthUser(c);
        assertThat(au.id()).isEqualTo(42L);
        assertThat(au.phone()).isEqualTo("+919876543210");
        assertThat(au.roles()).contains("ROLE_USER", "ROLE_ADMIN");
        assertThat(au.isAdmin()).isTrue();
        assertThat(jwt.accessTtlSeconds()).isEqualTo(props.getJwt().getAccessTokenMinutes() * 60);
    }

    @Test
    void refreshTokenHasNoRoles() {
        Claims c = jwt.parse(jwt.createRefreshToken(user));
        assertThat(jwt.isRefreshToken(c)).isTrue();
        assertThat(jwt.isAccessToken(c)).isFalse();
        assertThat(jwt.toAuthUser(c).roles()).isEmpty();
    }

    @Test
    void invalidTokensAreRejected() {
        assertThatThrownBy(() -> jwt.parse("not-a-jwt")).isInstanceOf(UnauthorizedException.class);
        String token = jwt.createAccessToken(user);
        assertThatThrownBy(() -> jwt.parse(token + "x")).isInstanceOf(UnauthorizedException.class);
        assertThatThrownBy(() -> jwt.parse("")).isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void expiredTokenIsRejected() {
        props.getJwt().setAccessTokenMinutes(-5);
        JwtService shortLived = new JwtService(props);
        String token = shortLived.createAccessToken(user);
        assertThatThrownBy(() -> shortLived.parse(token)).isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void authUserAdminFlag() {
        assertThat(new AuthUser(1L, "p", Set.of("ROLE_USER")).isAdmin()).isFalse();
        assertThat(new AuthUser(1L, "p", null).isAdmin()).isFalse();
    }

    private void runFilter(String header) throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        if (header != null) {
            req.addHeader("Authorization", header);
        }
        new JwtAuthFilter(jwt).doFilter(req, new MockHttpServletResponse(), new MockFilterChain());
    }

    @Test
    void filterAuthenticatesValidAccessToken() throws Exception {
        runFilter("Bearer " + jwt.createAccessToken(user));
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal()).isInstanceOf(AuthUser.class);
        assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities()).hasSize(2);
    }

    @Test
    void filterIgnoresRefreshGarbageAndMissingHeaders() throws Exception {
        runFilter("Bearer " + jwt.createRefreshToken(user));
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        runFilter("Bearer garbage");
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        runFilter("Basic abc");
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        runFilter(null);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}
