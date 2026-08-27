package org.backendsdcc.services;

import org.apache.commons.text.similarity.JaroWinklerSimilarity;
import org.backendsdcc.models.Product;
import org.backendsdcc.models.Purchase;
import org.backendsdcc.models.Receipt;
import org.backendsdcc.models.User;
import org.backendsdcc.repositories.ProductRepository;
import org.backendsdcc.repositories.PurchaseRepository;
import org.backendsdcc.repositories.ReceiptRepository;
import org.backendsdcc.repositories.UserRepository;
import org.backendsdcc.support.dto.ProductDTO;
import org.backendsdcc.support.dto.UserDTO;
import org.backendsdcc.support.exceptions.ConflictException;
import org.backendsdcc.support.exceptions.InvalidRequestException;
import org.backendsdcc.support.exceptions.NotFoundException;
import org.backendsdcc.support.validators.DateValidator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
public class ProductService
{
    @Autowired
    private ReceiptRepository receiptRepository;
    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PurchaseRepository purchaseRepository;
    @Autowired
    private UserService userService;

    private final JaroWinklerSimilarity jaroWinklerSimilarity = new JaroWinklerSimilarity();

    private static ProductDTO convertToDTO(Product product)
    {
        ProductDTO productDTO = new ProductDTO();
        productDTO.setCode(product.getCode());
        productDTO.setName(product.getName());
        return productDTO;
    }

    @Transactional(readOnly = true)
    public List<ProductDTO> getAllProducts()
    {
        List<Product> products = productRepository.findAll();
        List<ProductDTO> productDTOs = new ArrayList<>();
        for (Product product : products)
            productDTOs.add(convertToDTO(product));
        return productDTOs;
    }

    @Transactional
    public void addProduct(ProductDTO productDTO) throws ConflictException, InvalidRequestException
    {
        Product product = new Product();

        if (productRepository.existsByCode(productDTO.getCode()))
            throw new ConflictException("Product already exists");
        product.setCode(productDTO.getCode());
        if (productDTO.getName() == null)
            throw new InvalidRequestException("Invalid product name");
        product.setName(productDTO.getName());

        productRepository.save(product);
    }

    @Transactional(readOnly = true)
    public ProductDTO getProductByCode(String code) throws NotFoundException
    {
        Product product = productRepository.findByCode(code)
            .orElseThrow(() -> new NotFoundException("Product not found"));
        return convertToDTO(product);
    }

    @Transactional(readOnly = true)
    public ProductDTO getMostBoughtProductOfTheMonth(String userEmail, Instant date)
    {
        if (!DateValidator.isValid(date))
            throw new InvalidRequestException("Invalid date given");

        Instant dateMin = date.minusSeconds(30 * 24 * 60 * 60); // 30 days ago

        return getMostBoughtProductOfTimeSpan(userEmail, dateMin, date);
    }

    @Transactional(readOnly = true)
    public ProductDTO getMostBoughtProductOfTimeSpan(String userEmail, Instant dateMin, Instant dateMax)
    {
        if (!DateValidator.isValid(dateMin))
            throw new InvalidRequestException("Invalid min date given");
        if (!DateValidator.isValid(dateMax))
            throw new InvalidRequestException("Invalid max date given");
        if (dateMin.isAfter(dateMax))
            throw new InvalidRequestException("Invalid date range given");
        if (userEmail == null || userEmail.isBlank())
            throw new InvalidRequestException("Invalid user");

        // Gli acquisti di una persona li vede quella persona e un amministratore.
        // Il controllo sta qui e non su ProductController perche' questo metodo
        // ha tre chiamanti diversi (ProductController, UserController e i tool
        // del chatbot) e non tutti passano da una regola /admin/**. Nota che
        // deve restare codice normale e non un @PreAuthorize: getMostBoughtProductOfTheMonth
        // chiama questo metodo dall'interno della stessa classe, e le annotazioni
        // di Spring non scattano sulle chiamate che non passano dal proxy.
        UserDTO currentUser = userService.getCurrentUser();
        if (!userEmail.equalsIgnoreCase(currentUser.getEmail()) && !"ROLE_ADMIN".equals(currentUser.getRole()))
            throw new InvalidRequestException("Unauthorized");

        User user = userRepository.findByEmail(userEmail)
            .orElseThrow(() -> new NotFoundException("User not found"));;

        List<Receipt> receipts = receiptRepository.findReceiptsByUserAndDateBetween(user, dateMin, dateMax);
        Map<Product, Integer> occurrence = new HashMap<>();
        List<Purchase> purchases = new ArrayList<>();
        for (Receipt receipt : receipts)
        {
            purchases.addAll(receipt.getPurchases());

        }
        for  (Purchase purchase : purchases)
        {
            Product product = purchase.getProduct();
            int quantity = purchase.getQuantity();
            if (!occurrence.containsKey(product))
                occurrence.put(product, 0);
            occurrence.put(product, occurrence.get(product) + quantity);
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
            throw new NotFoundException("No product found");

        return convertToDTO(mostBought);
    }

    @Transactional(readOnly = true)
    public List<ProductDTO> findByNameOrCodeLike(String query, float threshold) throws InvalidRequestException
    {
        if (query == null || query.isBlank())
            throw new InvalidRequestException("Query not valid");
        if (threshold < 0 || threshold > 1)
            throw new InvalidRequestException("Threshold not valid");

        List<Product> matches = new ArrayList<>();
        matches.addAll(productRepository.findByNameLike("%" + query + "%"));
        matches.addAll(productRepository.findByNameContains(query));
        matches.addAll(productRepository.findByCodeLike("%" + query + "%"));
        matches.addAll(productRepository.findByCodeContains(query));

        // fuzzy search
        List<Product> allProducts = productRepository.findAll(PageRequest.of(0, 500)).getContent();
        matches.addAll(allProducts.stream()
                .filter(p -> jaroWinklerSimilarity.apply(p.getName(), query) > threshold
                        || jaroWinklerSimilarity.apply(p.getCode(), query) > threshold)
                .toList());

        // remove duplicates
        List<Product> uniqueProducts = new ArrayList<>(new HashSet<>(matches));
        List<ProductDTO> result = new ArrayList<>();
        for (Product product : uniqueProducts)
            result.add(convertToDTO(product));
        return result;
    }

    @Transactional
    public void deleteProduct(String code) throws NotFoundException, ConflictException
    {
        Product product = productRepository.findByCode(code)
                .orElseThrow(() -> new NotFoundException("Product not found"));

        if (!purchaseRepository.findPurchaseByProduct(product).isEmpty())
            throw new ConflictException("Prodotto già presente in una o più ricevute: impossibile eliminarlo");

        productRepository.delete(product);
    }

}
