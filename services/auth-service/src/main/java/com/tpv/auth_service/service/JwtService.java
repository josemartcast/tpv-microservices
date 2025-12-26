package com.tpv.auth_service.service;

import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;

@Service
public class JwtService {
    private final JwtEncoder encoder;
    private final String issuer;
    private final long expirationsMinutes;
    
    public JwtService(
    JwtEncoder encoder,
    @Value ("${app.jwt.issuer}") String issuer,
    @Value ("${app.jwt.expiration-minutes}") long expirationsMinutes){
        
        this.encoder = encoder;
        this.issuer = issuer;
        this.expirationsMinutes = expirationsMinutes;
    }
    
    public String generateToken(long userId, String username, List<String> roles){
        var header = JwsHeader.with(MacAlgorithm.HS256).build();
        Instant now = Instant.now();
              JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .issuedAt(now)
                .expiresAt(now.plusSeconds(expirationsMinutes * 60))
                .subject(String.valueOf(userId))
                .claim("username", username)
                .claim("roles", roles)
                .build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();

    }
            
}
