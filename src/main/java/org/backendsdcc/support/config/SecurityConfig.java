package org.backendsdcc.support.config;

import org.springframework.beans.factory.annotation.Value;
import org.backendsdcc.models.User;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.core.OAuth2Error;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception 
    {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/auth/**", "/public/**").permitAll()
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
                );

        return http.build();
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();

        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            // preferisco leggermi direttamente il claim e trasformarlo in ROLE_*
            List<String> groups = jwt.getClaimAsStringList("cognito:groups");
            if (groups == null) {
                String single = jwt.getClaimAsString("cognito:groups");
                groups = single == null ? Collections.emptyList() : List.of(single);
            }
            return groups.stream()
                    .map(g -> new SimpleGrantedAuthority("ROLE_" + g))
                    .collect(Collectors.toList());
        });

        return converter;
    }

    @Bean
    public JwtDecoder jwtDecoder(@Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuer,
                                 @Value("${cognito.clientId}") String clientId) 
    {
        NimbusJwtDecoder jwtDecoder = (NimbusJwtDecoder) JwtDecoders.fromOidcIssuerLocation(issuer);

        OAuth2TokenValidator<Jwt> withIssuer = JwtValidators.createDefaultWithIssuer(issuer);
        OAuth2TokenValidator<Jwt> audienceValidator = new AudienceValidator(clientId);
        OAuth2TokenValidator<Jwt> tokenUseValidator = new TokenUseValidator("access"); // optional: ensure token_use == "access"

        jwtDecoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(withIssuer, audienceValidator, tokenUseValidator));
        return jwtDecoder;
    }
    
    public static class AudienceValidator implements OAuth2TokenValidator<Jwt>
    {
        private final String requiredClientId;
        public AudienceValidator(String requiredClientId) { this.requiredClientId = requiredClientId; }

        @Override
        public OAuth2TokenValidatorResult validate(Jwt token) 
        {
            // Cognito access tokens can carry "client_id" or "aud"
            String clientIdClaim = token.getClaimAsString("client_id");
            List<String> aud = token.getClaimAsStringList("aud");
            boolean ok = requiredClientId.equals(clientIdClaim) || (aud != null && aud.contains(requiredClientId));
            return ok ? OAuth2TokenValidatorResult.success() : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Invalid audience", null));
        }
    }
    
    public static class TokenUseValidator implements OAuth2TokenValidator<Jwt>
    {
        private final String requiredTokenUse;
        public TokenUseValidator(String requiredTokenUse) { this.requiredTokenUse = requiredTokenUse; }

        @Override
        public OAuth2TokenValidatorResult validate(Jwt token) 
        {
            String tokenUse = token.getClaimAsString("token_use");
            return requiredTokenUse.equals(tokenUse)
                    ? OAuth2TokenValidatorResult.success()
                    : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Wrong token_use: " + tokenUse, null));
        }
    }
/*
public void ensureLocalUserExists(Jwt jwt) 
{
    String sub = jwt.getSubject(); // cognito sub
    userRepository.findByCognitoSub(sub).orElseGet(
    () ->{
        User u = new User();
        u.setCognitoSub(sub);
        u.setEmail(jwt.getClaimAsString("email"));
        u.setName(jwt.getClaimAsString("given_name"));
        u.setRole(determineRoleFromClaims(jwt)); // optional
        return userRepository.save(u);
    });
}
 */
}
