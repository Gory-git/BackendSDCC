package org.backendsdcc.services;

import org.backendsdcc.models.User;
import org.backendsdcc.repositories.UserRepository;
import org.backendsdcc.support.dto.UserDTO;
import org.backendsdcc.support.exceptions.ConflictException;
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
        User user = userRepository.findByFirebaseUid(sub).orElseThrow(() -> new NotFoundException("User not found"));
        return convertToDTO(user);
    }

    @Transactional
    public UserDTO createUser(UserDTO userDTO) throws ConflictException
    {
        Jwt jwt = getCurrentJwt();

        String firebaseUid = jwt.getSubject();
        String email = jwt.getClaimAsString("email");

        if (userRepository.findByFirebaseUid(firebaseUid).isPresent())
            throw new ConflictException("User with this Firebase UID already exists");

        if (userRepository.existsByEmail(email))
            throw new ConflictException("User with this email already exists");

        User u = new User();
        u.setEmail(email);
        u.setName(userDTO.getName());
        u.setSurname(userDTO.getSurname());
        u.setPhone(userDTO.getPhone());
        u.setRole(determineRoleFromClaims(jwt));
        u.setCreatedAt(java.time.Instant.now());
        u.setUpdatedAt(java.time.Instant.now());
        u.setFirebaseUid(firebaseUid);
        return convertToDTO(userRepository.save(u));
    }

    private static UserDTO convertToDTO(User user)
    {
        UserDTO dto = new UserDTO();
        dto.setName(user.getName());
        dto.setSurname(user.getSurname());
        dto.setEmail(user.getEmail());
        dto.setPhone(user.getPhone());
        dto.setRole(user.getRole());
        return dto;
    }

    private String determineRoleFromClaims(Jwt jwt)
    {
        // Firebase usa custom claims; se hai impostato un claim "role" custom nel token
        String customRole = jwt.getClaimAsString("role");
        if (customRole != null && customRole.equalsIgnoreCase("admin")) return "ROLE_ADMIN";
        return "ROLE_USER";
    }

    private Jwt getCurrentJwt() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            throw new IllegalStateException("Authenticated principal is not a Jwt");
        }
        return jwt;
    }

}
