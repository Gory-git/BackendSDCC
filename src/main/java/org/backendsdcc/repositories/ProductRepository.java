package org.backendsdcc.repositories;

import org.backendsdcc.models.Product;
import org.backendsdcc.models.Receipt;
import org.backendsdcc.models.User;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Date;
import java.util.List;

public interface ProductRepository extends JpaRepository<Product, Long>
{
    Product findByName(String name);

    Product findById(String id);

    List<Product> findByUser(User user);

    List<Product> findByReceipt(Receipt receipt);

}
