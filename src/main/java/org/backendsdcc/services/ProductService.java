package org.backendsdcc.services;

import org.backendsdcc.models.Product;
import org.backendsdcc.models.Receipt;
import org.backendsdcc.models.User;
import org.backendsdcc.repositories.ProductRepository;
import org.backendsdcc.repositories.ReceiptRepository;
import org.backendsdcc.support.dto.ProductDTO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class ProductService
{
    private final ReceiptRepository receiptRepository;
    private final ProductRepository productRepository;

    public ProductService(ReceiptRepository receiptRepository, ProductRepository productRepository)
    {
        this.receiptRepository = receiptRepository;
        this.productRepository = productRepository;
    }

    @Transactional(readOnly = true)
    public List<ProductDTO> getProducts(/* TODO condizione */)
    {
        return new ArrayList<ProductDTO>();
    }

    @Transactional(readOnly = true)
    public void addProduct(ProductDTO productDTO)
    {
    }

    @Transactional(readOnly = true)
    public ProductDTO getMostBoughtProductOfTheMonthForUser(User user, String monthAndYear)
    {
        List<Receipt> receipts = receiptRepository.findReceiptByUser(user);
        Map<Product, Integer> occurrence = new HashMap<>();
        for (Receipt receipt : receipts)
        {
            String date = receipt.getDate();
            StringTokenizer st = new StringTokenizer(date, "-");
            String day = st.nextToken();
            String month = st.nextToken();
            String year = st.nextToken();
            String mAY = month + "-" + year;
            if (mAY.equals(monthAndYear))
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

        if (max == -1)
            throw new RuntimeException("No product found");

        ProductDTO productDTO = new ProductDTO();
        productDTO.setCode(mostBought.getCode());
        productDTO.setName(mostBought.getName());

        return productDTO;
    }

}
