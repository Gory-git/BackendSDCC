package org.backendsdcc.repositories;

import org.backendsdcc.models.Product;
import org.backendsdcc.models.Receipt;
import org.backendsdcc.models.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ProductRepository extends JpaRepository<Product, Long>
{
    Product findByName(String name);

    @Query(
            "SELECT P " +
            "FROM Product P, User U, Purchase PU, Receipt R " +
            "WHERE R.user = ?1 " +
            "AND PU.receipt = R " +
            "AND PU.product = P"
    )
    List<Product> findByUser(User user);

    @Query(
            "SELECT P " +
            "FROM Product P, Purchase PU, Receipt R " +
            "WHERE R = ?1 " +
            "AND PU.receipt = R " +
            "AND PU.product = P"
    )
    List<Product> findProductByReceipt(Receipt receipt);

    @Query(
            "SELECT PU.quantity " +
            "FROM Product P, Purchase PU, Receipt R " +
            "WHERE R = ?1 " +
            "AND P = ?2 " +
            "AND PU.receipt = R " +
            "AND PU.product = P"
    )
    int getProductQuantityByReceiptAndProduct(Receipt receipt, Product product);

    Product findByCode(String code);
}
