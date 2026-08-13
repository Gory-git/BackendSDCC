package org.backendsdcc.repositories;

import org.backendsdcc.models.Receipt;
import org.backendsdcc.models.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ReceiptRepository extends JpaRepository<Receipt, Long>
{
    List<Receipt> findByUser(User user);

    @Query("SELECT DISTINCT r FROM Receipt r " +
            "LEFT JOIN FETCH r.purchases p " +
            "LEFT JOIN FETCH p.product " +
            "WHERE r.user = :user")
    List<Receipt> findByUserWithPurchases(@Param("user") User user);

    boolean existsByCode(String code);

    Optional<Receipt> findReceiptByCode(String code);

    List<Receipt> findReceiptsByUserAndDateBetween(User user, Instant dateMin, Instant dateMax);

    List<Receipt> findByUserEmailLike(String email);

    List<Receipt> findByUserEmailContains(String email);

    List<Receipt> findByCodeLike(String code);

    List<Receipt> findByCodeContains(String code);

    List<Receipt> findReceiptByAmountLessThan(BigDecimal amount);

    List<Receipt> findReceiptByAmountGreaterThan(BigDecimal amount);

    List<Receipt> findReceiptByAmountEquals(BigDecimal amount);

    List<Receipt> findReceiptByAmountBetween(BigDecimal amount1, BigDecimal amount2);
}
