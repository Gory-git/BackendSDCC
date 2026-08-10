package org.backendsdcc.services;

import org.backendsdcc.models.Product;
import org.backendsdcc.models.Receipt;
import org.backendsdcc.models.User;
import org.backendsdcc.repositories.ProductRepository;
import org.backendsdcc.repositories.ReceiptRepository;
import org.backendsdcc.repositories.UserRepository;
import org.backendsdcc.support.dto.ProductDTO;
import org.backendsdcc.support.validators.DateValidator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
public class ProductService
{
    private final ReceiptRepository receiptRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    public ProductService(ReceiptRepository receiptRepository, ProductRepository productRepository, UserRepository userRepository)
    {
        this.receiptRepository = receiptRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<ProductDTO> getProducts(/* TODO condizione */)
    {
        return new ArrayList<ProductDTO>();
    }

    @Transactional(readOnly = true)
    public void addProduct(ProductDTO productDTO)
    {
        Product product = new Product();

        if (productRepository.findByCode(productDTO.getCode()) != null)
            throw new RuntimeException("Product already exists");
        product.setCode(productDTO.getCode());
        if (productDTO.getName() == null)
            throw new RuntimeException("Invalid product name");
        product.setName(productDTO.getName());

        productRepository.save(product);
    }

    @Transactional(readOnly = true)
    public ProductDTO getMostBoughtProductOfTheMonth(String userEmail, Instant date)
    {
        if (!DateValidator.isValid(date))
            throw new RuntimeException("Invalid date given");
        Calendar cal = Calendar.getInstance();
        cal.setTime(Date.from(date));
        cal.set(cal.get(Calendar.YEAR) + 1900, cal.get(Calendar.MONTH), 1);
        Instant dateMin = cal.toInstant();
        Instant dateMax = cal.toInstant();

        return getMostBoughtProductOfTimeSpan(userEmail, dateMin, dateMax);
    }

    @Transactional(readOnly = true)
    public ProductDTO getMostBoughtProductOfTimeSpan(String userEmail, Instant dateMin, Instant dateMax)
    {
        if (!DateValidator.isValid(dateMin))
            throw new RuntimeException("Invalid min date given");
        if (!DateValidator.isValid(dateMax))
            throw new RuntimeException("Invalid max date given");
        if (userEmail == null)
            throw new RuntimeException("Invalid user");
        if (userRepository.findByEmail(userEmail) == null)
            throw new RuntimeException("User not found");
        User user = userRepository.findByEmail(userEmail);

        List<Receipt> receipts = receiptRepository.findReceiptsByUserAndDateBetween(user, dateMin, dateMax);
        Map<Product, Integer> occurrence = new HashMap<>();
        for (Receipt receipt : receipts)
        {
            List<Product> products = productRepository.findProductByReceipt(receipt);
            for  (Product product : products)
            {
                Integer quantity = productRepository.getProductQuantityByReceiptAndProduct(receipt, product);
                if (!occurrence.containsKey(product))
                {
                    occurrence.put(product, 0);
                }
                occurrence.put(product, occurrence.get(product) + quantity);
            }
        }
        Product mostBought = null;
        int max = -1;

        for (Product product : occurrence.keySet())
        {
            if (occurrence.get(product) > max)
            {
                mostBought = product;
                max = occurrence.get(product);
            }
        }

        if (max == -1 || mostBought == null)
            throw new RuntimeException("No product found");

        ProductDTO productDTO = new ProductDTO();
        productDTO.setCode(mostBought.getCode());
        productDTO.setName(mostBought.getName());

        return productDTO;
    }

}
