package org.backendsdcc.repositories;

import org.backendsdcc.models.Receipt;
import org.backendsdcc.models.User;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Date;
import java.util.List;

public interface ReceiptRepository extends JpaRepository<Receipt, Long>
{
    List<Receipt> findByUser(User user);

    Receipt findReceiptByCode(String code);

    List<Receipt> findReceiptsByUserAndDateBetween(User user, Date dateMin, Date dateMax);

    List<Receipt> findByUserEmailLike(String email);

    List<Receipt> findByUserEmailContains(String email);

    List<Receipt> findByCodeLike(String code);

    List<Receipt> findByCodeContains(String code);

    List<Receipt> findReceiptByAmountLessThan(float amount);

    List<Receipt> findReceiptByAmountGreaterThan(float amount);

    List<Receipt> findReceiptByAmountEquals(float amount);

    List<Receipt> findReceiptByAmountBetween(float amount1, float amount2);
}
