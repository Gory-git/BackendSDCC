package org.backendsdcc.repositories;

import org.backendsdcc.models.Product;
import org.backendsdcc.models.Purchase;
import org.backendsdcc.models.Receipt;
import org.backendsdcc.models.User;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Date;
import java.util.List;

public interface PurchaseRepository extends JpaRepository<Purchase, Long>
{
    Purchase findById(long id);

    List<Purchase> findByUser(User user);

    List<Purchase> findByReceipt(Receipt receipt);

    List<Purchase> findPurchaseByProduct(Product product);

    List<Purchase> findPurchaseByUserAndProduct(User user, Product product);

}