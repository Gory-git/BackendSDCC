package org.backendsdcc.repositories;

import org.backendsdcc.models.PaymentMethod;
import org.backendsdcc.models.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PaymentMethodRepository extends JpaRepository<PaymentMethod, Long>
{
    PaymentMethod findByCode(String code);

    List<PaymentMethod> findByUser(User user);
}
