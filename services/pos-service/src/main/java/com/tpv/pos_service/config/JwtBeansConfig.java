
package com.tpv.pos_service.config;

import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

@Configuration
public class JwtBeansConfig {
  @Bean  
  JwtDecoder jwtDecoder(@Value("${security.jwt.secret}") String secret){
    var key = new SecretKeySpec(secret.getBytes(), "HmacSHA256");
    return NimbusJwtDecoder.withSecretKey(key).build();
}
}
