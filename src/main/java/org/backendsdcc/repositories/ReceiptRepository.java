package org.backendsdcc.repositories;

import org.backendsdcc.models.Receipt;
import org.backendsdcc.models.User;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Date;
import java.util.List;

public interface ReceiptRepository extends JpaRepository<Receipt, Long>
{
    Receipt findReceiptById(long id);

    Receipt findReceiptByCode(String code);

    List<Receipt> findReceiptByUser(User user);

    List<Receipt> findReceiptByDate(Date date);

    List<Receipt> findReceiptByAmountLessThan(float amount);

    List<Receipt> findReceiptByAmountGreaterThan(float amount);

    List<Receipt> findReceiptByAmountEquals(float amount);

    List<Receipt> findReceiptByAmountBetween(float amount1, float amount2);
}
