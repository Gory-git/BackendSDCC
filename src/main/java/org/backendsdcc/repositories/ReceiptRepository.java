package org.backendsdcc.repositories;

import org.backendsdcc.models.Receipt;
import org.backendsdcc.models.User;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Date;

public interface ReceiptRepository extends JpaRepository<Receipt, Long>
{
    Receipt findReceiptById(long id);

    Receipt findReceiptByUser(User user);

    Receipt findReceiptByAmount(float amount);

    Receipt findReceiptByDate(Date date);
}
