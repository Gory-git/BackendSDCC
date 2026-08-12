package org.backendsdcc.services;

import org.backendsdcc.models.User;
import org.backendsdcc.repositories.UserRepository;
import org.backendsdcc.support.dto.UserDTO;
import org.backendsdcc.support.exceptions.AlreadyExistsException;
import org.backendsdcc.support.exceptions.NotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class UserService
{
    @Autowired
    private UserRepository userRepository;

    @Transactional(readOnly = true)
    public UserDTO getCurrentUser() throws NotFoundException
    {
        Jwt jwt = getCurrentJwt();
        String sub = jwt.getSubject();
        User user = userRepository.findByCognitoSub(sub).orElseThrow(() -> new NotFoundException("User not found"));
        return convertToDTO(user);
    }

    @Transactional
    public UserDTO createUser() throws AlreadyExistsException
    {
        Jwt jwt = getCurrentJwt();
        if (userRepository.existsByEmail(jwt.getClaimAsString("email")))
            throw new AlreadyExistsException("User with this email already exists");
        if (userRepository.existsByCognitoSub(jwt.getSubject()))
            throw new AlreadyExistsException("User with this Cognito sub already exists");
        User u = new User();
        u.setEmail(jwt.getClaimAsString("email"));
        u.setName(jwt.getClaimAsString("given_name"));
        u.setSurname(jwt.getClaimAsString("family_name"));
        u.setPhone(jwt.getClaimAsString("phone_number"));
        u.setRole(determineRoleFromClaims(jwt));
        u.setCreatedAt(java.time.Instant.now());
        u.setUpdatedAt(java.time.Instant.now());
        u.setCognitoSub(jwt.getSubject());
        return convertToDTO(userRepository.save(u));
    }

    private static UserDTO convertToDTO(User user)
    {
        UserDTO dto = new UserDTO();
        dto.setName(user.getName());
        dto.setSurname(user.getSurname());
        dto.setEmail(user.getEmail());
        dto.setPhone(user.getPhone());
        dto.setBirthDate(user.getBirthDate());
        dto.setRole(user.getRole());
        return dto;
    }

    private String determineRoleFromClaims(Jwt jwt)
    {
        List<String> groups = jwt.getClaimAsStringList("cognito:groups");
        if (groups != null && groups.contains("ADMIN")) return "ADMIN";
        return "USER";
    }

    private Jwt getCurrentJwt() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            throw new IllegalStateException("Authenticated principal is not a Jwt");
        }
        return jwt;
    }

}
