package org.backendsdcc.repositories;

import org.backendsdcc.models.User;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long>
{
    @Query("SELECT U FROM User U WHERE U.email like ?1")
    User findByEmailLike(String email);

    boolean existsByEmail(String email);

    User findByEmailIgnoreCaseAndPassword(String email, String password);

    User findByEmail(String email);
}
