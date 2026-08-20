package org.backendsdcc.support.config;

import org.springframework.beans.factory.annotation.Value;
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
            // Firebase: leggi il claim "role" custom (se presente)
            String role = jwt.getClaimAsString("role");
            if (role != null && !role.isBlank()) {
                return List.of(new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()));
            }
            // Default: ROLE_USER
            return List.of(new SimpleGrantedAuthority("ROLE_USER"));
        });

        return converter;
    }

    @Bean
    public JwtDecoder jwtDecoder(@Value("${firebase.project-id}") String projectId) 
    {
        // Firebase issuer: https://securetoken.google.com/<project-id>
        String issuer = "https://securetoken.google.com/" + projectId;
        
        NimbusJwtDecoder jwtDecoder = (NimbusJwtDecoder) JwtDecoders.fromOidcIssuerLocation(issuer);
        
        // Validatore issuer di default
        OAuth2TokenValidator<Jwt> withIssuer = JwtValidators.createDefaultWithIssuer(issuer);
        
        // Validatore audience: Firebase usa projectId come audience
        OAuth2TokenValidator<Jwt> audienceValidator = new FirebaseAudienceValidator(projectId);
        
        jwtDecoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(withIssuer, audienceValidator));
        return jwtDecoder;
    }
    
    public static class FirebaseAudienceValidator implements OAuth2TokenValidator<Jwt>
    {
        private final String firebaseProjectId;
        public FirebaseAudienceValidator(String firebaseProjectId) { this.firebaseProjectId = firebaseProjectId; }

        @Override
        public OAuth2TokenValidatorResult validate(Jwt token) 
        {
            // Firebase mette il project ID in "aud"
            List<String> aud = token.getClaimAsStringList("aud");
            boolean ok = aud != null && aud.contains(firebaseProjectId);
            return ok ? OAuth2TokenValidatorResult.success() : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Invalid audience for Firebase", null));
        }
    }
}
