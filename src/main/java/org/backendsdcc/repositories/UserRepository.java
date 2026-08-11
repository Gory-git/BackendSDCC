package org.backendsdcc.repositories;

import org.backendsdcc.models.PaymentMethod;
import org.backendsdcc.models.User;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long>
{
    @Query("SELECT U FROM User U WHERE U.email like ?1")
    User findByEmailLike(String email);

    boolean existsByEmail(String email);

    User findByEmailIgnoreCaseAndPassword(String email, String password);

    Optional<User> findByEmail(String email);
}
