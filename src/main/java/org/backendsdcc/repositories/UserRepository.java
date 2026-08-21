package org.backendsdcc.repositories;

import org.backendsdcc.models.PaymentMethod;
import org.backendsdcc.models.User;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long>
{
    List<User> findByEmailLike(String email);

    List<User> findByEmailContains(String email);

    boolean existsByEmail(String email);

    boolean existsByFirebaseUid(String firebaseUid);

    Optional<User> findByFirebaseUid(String firebaseUid);

    Optional<User> findByEmail(String email);

    boolean existsByEmailIgnoreCase(String email);

    @Query("SELECT u FROM User u WHERE " +
            "u.email LIKE %:term% OR " +
            "u.name LIKE %:term% OR " +
            "u.surname LIKE %:term% OR " +
            "u.codiceFiscale LIKE %:term%")
    List<User> searchByTerm(@Param("term") String term);
}
