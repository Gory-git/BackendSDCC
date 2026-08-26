package org.backendsdcc.services;

import org.apache.commons.text.similarity.FuzzyScore;
import org.apache.commons.text.similarity.JaroWinklerSimilarity;
import org.backendsdcc.models.User;
import org.backendsdcc.repositories.UserRepository;
import org.backendsdcc.support.dto.UserDTO;
import org.backendsdcc.support.dto.UserUpdateDTO;
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
    public List<UserDTO> searchUsers(String query, float threshold) throws InvalidRequestException
    {
        if (query == null || query.isBlank())
            throw new InvalidRequestException("Query not valid");
        if (threshold < 0 || threshold > 1)
            throw new InvalidRequestException("Threshold not valid");
        if (!getCurrentUser().getRole().equals("ROLE_ADMIN"))
            throw new InvalidRequestException("Unhauthorized");

        List<User> usersWithDuplicates = new ArrayList<>(userRepository.searchByTerm(query));

        // fuzzy search: email, nome, cognome, codice fiscale
        List<User> allUsers = userRepository.findAll(PageRequest.of(0, 500)).getContent();

        usersWithDuplicates.addAll(allUsers.stream()
                .filter(user -> jaroWinklerSimilarity.apply(user.getEmail(), query) > threshold
                        || jaroWinklerSimilarity.apply(user.getName(), query) > threshold
                        || jaroWinklerSimilarity.apply(user.getSurname(), query) > threshold
                        || (user.getCodiceFiscale() != null && jaroWinklerSimilarity.apply(user.getCodiceFiscale(), query) > threshold))
                .toList());

        // remove duplicates
        List<User> uniqueUsers = new ArrayList<>(new HashSet<>(usersWithDuplicates));
        List<UserDTO> userDTOs = new ArrayList<>();
        for (User user : uniqueUsers)
            userDTOs.add(convertToDTO(user));
        return userDTOs;
    }

    @Transactional(readOnly = true)
    public UserDTO getCurrentUser() throws NotFoundException
    {
        return convertToDTO(getCurrentUserEntity());
    }

    @Transactional
    public UserDTO updateCurrentUser(UserUpdateDTO userUpdateDTO) throws NotFoundException, ConflictException, InvalidRequestException
    {
        User user = getCurrentUserEntity();

        String name = trimToNull(userUpdateDTO.getName());
        String surname = trimToNull(userUpdateDTO.getSurname());
        if (name == null || surname == null)
            throw new InvalidRequestException("Name and surname are required");

        // Stringa vuota normalizzata a null: telefono e codice fiscale sono colonne
        // unique, due utenti che "svuotano" il campo salvando "" violerebbero il vincolo.
        String phone = trimToNull(userUpdateDTO.getPhone());
        String codiceFiscale = trimToNull(userUpdateDTO.getCodiceFiscale());
        if (codiceFiscale != null)
            codiceFiscale = codiceFiscale.toUpperCase(Locale.ROOT);

        // Il controllo di unicità scatta solo se il valore è cambiato: così non serve
        // escludere sé stessi dalla query.
        if (phone != null && !phone.equals(user.getPhone()) && userRepository.existsByPhone(phone))
            throw new ConflictException("Phone already in use");
        if (codiceFiscale != null && !codiceFiscale.equals(user.getCodiceFiscale()) && userRepository.existsByCodiceFiscale(codiceFiscale))
            throw new ConflictException("Codice fiscale already in use");

        user.setName(name);
        user.setSurname(surname);
        user.setPhone(phone);
        user.setCodiceFiscale(codiceFiscale);
        // Scritto a mano come in createUser: l'entità ha @LastModifiedDate ma non
        // @EntityListeners(AuditingEntityListener.class), quindi l'auditing non scatta.
        user.setUpdatedAt(java.time.Instant.now());

        return convertToDTO(userRepository.save(user));
    }

    private User getCurrentUserEntity() throws NotFoundException
    {
        String sub = getCurrentJwt().getSubject();
        return userRepository.findByFirebaseUid(sub).orElseThrow(() -> new NotFoundException("User not found"));
    }

    private static String trimToNull(String value)
    {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    @Transactional
    public UserDTO createUser(UserDTO userDTO) throws ConflictException, InvalidRequestException
    {
        Jwt jwt = getCurrentJwt();

        String firebaseUid = jwt.getSubject();
        String email = jwt.getClaimAsString("email");

        if (userRepository.findByFirebaseUid(firebaseUid).isPresent())
            throw new ConflictException("User with this Firebase UID already exists");

        if (userRepository.existsByEmail(email))
            throw new ConflictException("User with this email already exists");

        String name = trimToNull(userDTO.getName());
        String surname = trimToNull(userDTO.getSurname());
        if (name == null || surname == null)
            throw new InvalidRequestException("Name and surname are required");

        String phone = trimToNull(userDTO.getPhone());
        String codiceFiscale = trimToNull(userDTO.getCodiceFiscale());
        if (codiceFiscale != null)
            codiceFiscale = codiceFiscale.toUpperCase(Locale.ROOT);

        if (phone != null && userRepository.existsByPhone(phone))
            throw new ConflictException("User with this phone already exists");

        if (codiceFiscale != null && userRepository.existsByCodiceFiscale(codiceFiscale))
            throw new ConflictException("User with this codice fiscale already exists");

        User u = new User();
        u.setEmail(email);
        u.setName(name);
        u.setSurname(surname);
        u.setPhone(phone);
        u.setCodiceFiscale(codiceFiscale);
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
        dto.setCodiceFiscale(user.getCodiceFiscale());
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
