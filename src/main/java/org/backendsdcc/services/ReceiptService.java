package org.backendsdcc.services;

import org.backendsdcc.models.*;
import org.backendsdcc.repositories.*;
import org.backendsdcc.support.comparators.ReceiptAmountComparator;
import org.backendsdcc.support.comparators.ReceiptDateComparator;
import org.backendsdcc.support.dto.ProductDTO;
import org.backendsdcc.support.dto.ReceiptDTO;
import org.backendsdcc.support.dto.ReceiptLineDTO;
import org.backendsdcc.support.validators.DateValidator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.apache.commons.text.similarity.FuzzyScore;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ReceiptService
{
    @Autowired
    private ReceiptRepository receiptRepository;
    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private PurchaseRepository purchaseRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ProductService productService;

    @Transactional(readOnly = true)
    public ReceiptDTO getReceipt(String code)
    {
        Receipt receipt = receiptRepository.findReceiptByCode(code);
        if  (receipt == null)
        {
            throw new RuntimeException("Receipt not found");
        }
        ReceiptDTO receiptDTO = new ReceiptDTO();

        receiptDTO.setCode(receipt.getCode());
        receiptDTO.setAmount(receipt.getAmount());
        receiptDTO.setDate(receipt.getDate());
        receiptDTO.setTax(receipt.getTax());
        receiptDTO.setUserEmail(receipt.getUser().getEmail());
        receiptDTO.setPaymentMethod(receipt.getPaymentMethod());

        List<ReceiptLineDTO> lines = new ArrayList<>();
        List<Purchase> purchases = purchaseRepository.findByReceipt(receipt);

        for (Purchase purchase : purchases)
        {
            ReceiptLineDTO lineDTO = new ReceiptLineDTO();
            
            ProductDTO productDTO = new ProductDTO();
            productDTO.setCode(purchase.getProduct().getCode());
            productDTO.setName(purchase.getProduct().getName());
            
            lineDTO.setProduct(productDTO);
            lineDTO.setQuantity(purchase.getQuantity());
            lineDTO.setPrice(purchase.getPrice());
            
            lines.add(lineDTO);
        }

        receiptDTO.setLines(lines);

        return receiptDTO;
    }

    @Transactional(readOnly = true)
    public List<ReceiptDTO> getAllReceiptsOrdered(User user, boolean date)
    {
        List<Receipt> receipts = receiptRepository.findByUser(user);
        if (receipts == null || receipts.isEmpty())
            throw new RuntimeException("No receipt found");

        if (date)   // SORT BY DATE
            receipts.sort(new ReceiptDateComparator());
        else        // SORT BY AMOUNT
            receipts.sort(new ReceiptAmountComparator());

        List<ReceiptDTO> receiptDTOs = new ArrayList<>();
        for (Receipt receipt : receipts)
            receiptDTOs.add(getReceipt(receipt.getCode()));
        return receiptDTOs;
    }

    @Transactional(readOnly = true)
    public void saveReceipt(ReceiptDTO receiptDTO)
    {
        if (receiptDTO == null)
            throw new RuntimeException("Receipt not valid");
        Receipt receipt = new Receipt();

        if (receiptDTO.getCode() == null)
            throw new RuntimeException("Receipt code not valid");
        if (receiptRepository.findReceiptByCode(receiptDTO.getCode()) != null)
            throw new RuntimeException("A receipt with this code already exists");
        receipt.setCode(receiptDTO.getCode());

        if (receiptDTO.getAmount().compareTo(BigDecimal.ZERO) <= 0)
            throw new RuntimeException("Receipt amount not valid");
        receipt.setAmount(receiptDTO.getAmount());

        if (receiptDTO.getTax().compareTo(BigDecimal.ZERO) <= 0 || receiptDTO.getTax().compareTo(receiptDTO.getAmount()) >= 0)
            throw new RuntimeException("Receipt taxes not valid");
        receipt.setAmount(receiptDTO.getTax());

        if (receiptDTO.getUserEmail() == null)
            throw new RuntimeException("Receipt user email not valid");
        if (!userRepository.existsByEmail(receiptDTO.getUserEmail()))
            throw new RuntimeException("No user with this email exists");
        receipt.setUser(userRepository.findByEmail(receiptDTO.getUserEmail()));

        if (receiptDTO.getPaymentMethod() == null)
            throw new RuntimeException("Receipt payment method not valid");
        receipt.setPaymentMethod(receiptDTO.getPaymentMethod());

        Instant date = receiptDTO.getDate();
        if (!DateValidator.isValid(date))
            throw new RuntimeException("Receipt date not valid");
        receipt.setDate(date);

        List<ReceiptLineDTO> lines = receiptDTO.getLines();

        if (lines == null || lines.isEmpty())
            throw new RuntimeException("No items found in receipt");

        BigDecimal total = BigDecimal.ZERO;
        for (ReceiptLineDTO lineDTO : lines)
        {
            ProductDTO productDTO = lineDTO.getProduct();
            Integer quantity = lineDTO.getQuantity();
            BigDecimal price = lineDTO.getPrice();

            if (productDTO == null)
                throw new RuntimeException("Product not found");
            if (quantity == null || quantity <= 0)
                throw new RuntimeException("Quantity not valid");
            if (price == null || price.compareTo(BigDecimal.ZERO) <= 0)
                throw new RuntimeException("Price not valid");

            if (productRepository.findByCode(productDTO.getCode()) == null)
                productService.addProduct(productDTO);
            
            Product product = productRepository.findByCode(productDTO.getCode());
            if (product != null)
            {
                total = total.add(price.multiply(BigDecimal.valueOf(quantity)));

                if (total.compareTo(receipt.getAmount()) > 0)
                    throw new RuntimeException("Receipt amount not valid: items exceed total");

                Purchase purchase = new Purchase();
                purchase.setReceipt(receipt);
                purchase.setPrice(price);
                purchase.setQuantity(quantity);
                purchase.setProduct(product);
                purchaseRepository.save(purchase);
            }
        }

        if (total.compareTo(receipt.getAmount()) != 0)
            throw new RuntimeException("Receipt amount mismatch: items total does not match receipt amount");

        receiptRepository.save(receipt);

    }

    @Transactional(readOnly = true)
    public List<ReceiptDTO> findByUserEmailLIke(String email, float threshold)
    {
        if (email == null)
            throw new RuntimeException("User email not valid");
        if (threshold < 0 || threshold > 1)
            throw new RuntimeException("Threshold not valid");
        List<ReceiptDTO> receiptDTOs = new ArrayList<>();
        List<Receipt> receiptsWithDuplicates = receiptRepository.findByUserEmailLike("%"+email+"%");
        receiptsWithDuplicates.addAll(receiptRepository.findByUserEmailContains(email));
        // fuzzy search
        List<Receipt> allReceipts = receiptRepository.findAll();
        FuzzyScore fuzzyScore = new FuzzyScore(Locale.ITALIAN);

        receiptsWithDuplicates.addAll(allReceipts.stream()
                .filter(receipt -> fuzzyScore.fuzzyScore(receipt.getUser().getEmail(), email) > threshold)
                .toList());

        // remove duplicates
        List<Receipt> receipts = new ArrayList<>(new HashSet<>(receiptsWithDuplicates));
        for (Receipt receipt : receipts)
            receiptDTOs.add(getReceipt(receipt.getCode()));
        return receiptDTOs;
    }

    @Transactional(readOnly = true)
    public List<ReceiptDTO> findByCodeLike(String code, float threshold)
    {
        if (code == null)
            throw new RuntimeException("Code not valid");
        if (threshold < 0 || threshold > 1)
            throw new RuntimeException("Threshold not valid");
        List<ReceiptDTO> receiptDTOs = new ArrayList<>();
        List<Receipt> receiptsWithDuplicates = receiptRepository.findByCodeLike("%"+code+"%");
        receiptsWithDuplicates.addAll(receiptRepository.findByCodeContains(code));

        // fuzzy search
        List<Receipt> allReceipts = receiptRepository.findAll();
        FuzzyScore fuzzyScore = new FuzzyScore(Locale.ITALIAN);

        receiptsWithDuplicates.addAll(allReceipts.stream()
                .filter(receipt -> fuzzyScore.fuzzyScore(receipt.getCode(), code) > threshold)
                .toList());

        // remove duplicates
        List<Receipt> receipts = new ArrayList<>(new HashSet<>(receiptsWithDuplicates));
        for (Receipt receipt : receipts)
            receiptDTOs.add(getReceipt(receipt.getCode()));
        return receiptDTOs;
    }

    // TODO statistiche e query AI
}
