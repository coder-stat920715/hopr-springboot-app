package com.hopr.security;

import com.hopr.config.HoprProperties;
import com.hopr.entity.User;
import com.hopr.exception.UnauthorizedException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class JwtService {

    private static final String TYPE_ACCESS = "access";
    private static final String TYPE_REFRESH = "refresh";

    private final HoprProperties props;
    private final SecretKey key;

    public JwtService(HoprProperties props) {
        this.props = props;
        this.key = Keys.hmacShaKeyFor(props.getJwt().getSecret().getBytes(StandardCharsets.UTF_8));
    }

    public long accessTtlSeconds() {
        return Duration.ofMinutes(props.getJwt().getAccessTokenMinutes()).toSeconds();
    }

    public String createAccessToken(User user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .claim("phone", user.getPhone())
                .claim("roles", List.copyOf(user.getRoles()))
                .claim("typ", TYPE_ACCESS)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(Duration.ofMinutes(props.getJwt().getAccessTokenMinutes()))))
                .signWith(key)
                .compact();
    }

    public String createRefreshToken(User user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .claim("typ", TYPE_REFRESH)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(Duration.ofDays(props.getJwt().getRefreshTokenDays()))))
                .signWith(key)
                .compact();
    }

    public Claims parse(String token) {
        try {
            return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
        } catch (JwtException | IllegalArgumentException ex) {
            throw new UnauthorizedException("Invalid or expired token");
        }
    }

    public boolean isRefreshToken(Claims claims) {
        return TYPE_REFRESH.equals(claims.get("typ", String.class));
    }

    public boolean isAccessToken(Claims claims) {
        return TYPE_ACCESS.equals(claims.get("typ", String.class));
    }

    public Long userId(Claims claims) {
        return Long.valueOf(claims.getSubject());
    }

    @SuppressWarnings("unchecked")
    public AuthUser toAuthUser(Claims claims) {
        Object roles = claims.get("roles");
        Set<String> roleSet = new HashSet<>();
        if (roles instanceof List<?> list) {
            for (Object r : list) {
                roleSet.add(String.valueOf(r));
            }
        }
        return new AuthUser(userId(claims), claims.get("phone", String.class), roleSet);
    }
}
