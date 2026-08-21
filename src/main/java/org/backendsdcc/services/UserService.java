package org.backendsdcc.services;

import org.apache.commons.text.similarity.FuzzyScore;
import org.apache.commons.text.similarity.JaroWinklerSimilarity;
import org.backendsdcc.models.User;
import org.backendsdcc.repositories.UserRepository;
import org.backendsdcc.support.dto.UserDTO;
import org.backendsdcc.support.exceptions.ConflictException;
import org.backendsdcc.support.exceptions.InvalidRequestException;
import org.backendsdcc.support.exceptions.NotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

@Service
public class UserService
{
    @Autowired
    private UserRepository userRepository;

    private JaroWinklerSimilarity jaroWinklerSimilarity = new JaroWinklerSimilarity();

    @Transactional(readOnly = true)
    public List<UserDTO> getAllUsers()
    {
        List<User> users = userRepository.findAll();
        return users.stream().map(UserService::convertToDTO).toList();
    }

    @Transactional(readOnly = true)
    public List<UserDTO> findByEmailLike(String email, float threshold) throws InvalidRequestException
    {
        if (email == null)
            throw new InvalidRequestException("User email not valid");
        if (threshold < 0 || threshold > 1)
            throw new InvalidRequestException("Threshold not valid");
        if (!getCurrentUser().getRole().equals("ROLE_ADMIN"))
            throw new InvalidRequestException("Unhauthorized");

        List<UserDTO> userDTOs = new ArrayList<>();
        List<User> usersWithDuplicates = userRepository.findByEmailLike("%"+email+"%");
        usersWithDuplicates.addAll(userRepository.findByEmailContains(email));
        // fuzzy search
        List<User> allUsers = userRepository.findAll(PageRequest.of(0, 500)).getContent();

        usersWithDuplicates.addAll(allUsers.stream()
                .filter(user -> jaroWinklerSimilarity.apply(user.getEmail(), email) > threshold)
                .toList());

        // remove duplicates
        List<User> uniqueUsers = new ArrayList<>(new HashSet<>(usersWithDuplicates));
        for (User user : uniqueUsers)
            userDTOs.add(convertToDTO(user));
        return userDTOs;
    }

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
