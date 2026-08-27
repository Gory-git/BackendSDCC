package org.backendsdcc.services;

import org.apache.commons.text.similarity.JaroWinklerSimilarity;
import org.backendsdcc.models.*;
import org.backendsdcc.repositories.*;
import org.backendsdcc.support.comparators.ReceiptAmountComparator;
import org.backendsdcc.support.comparators.ReceiptDateComparator;
import org.backendsdcc.support.dto.ReceiptDTO;
import org.backendsdcc.support.dto.ReceiptLineDTO;
import org.backendsdcc.support.dto.UserDTO;
import org.backendsdcc.support.validators.DateValidator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.apache.commons.text.similarity.FuzzyScore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.backendsdcc.support.exceptions.ConflictException;
import org.backendsdcc.support.exceptions.InvalidRequestException;
import org.backendsdcc.support.exceptions.NotFoundException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

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
    private UserService userService;
    @Autowired
    private S3Service s3Service;

    private static final Logger log = LoggerFactory.getLogger(ReceiptService.class);

    private JaroWinklerSimilarity jaroWinklerSimilarity = new JaroWinklerSimilarity();

    @Transactional(readOnly = true)
    public ReceiptDTO getReceipt(String code) throws NotFoundException
    {
        UserDTO currentUser = userService.getCurrentUser();
        String userEmail = currentUser.getEmail();
        String role = currentUser.getRole();
        Receipt receipt = receiptRepository.findReceiptByCode(code)
                .orElseThrow(() -> new NotFoundException("Receipt not found"));
        if (!receipt.getUser().getEmail().equals(userEmail) && !role.equals("ROLE_ADMIN"))
            throw new NotFoundException("Receipt not found");
        return convertToDTO(receipt);
    }

    private static ReceiptDTO convertToDTO(Receipt receipt)
    {
        ReceiptDTO receiptDTO = new ReceiptDTO();
        receiptDTO.setCode(receipt.getCode());
        receiptDTO.setAmount(receipt.getAmount());
        receiptDTO.setTax(receipt.getTax());
        receiptDTO.setDate(receipt.getDate());
        receiptDTO.setPaymentMethod(receipt.getPaymentMethod());
        receiptDTO.setUserEmail(receipt.getUser().getEmail());

        List<ReceiptLineDTO> lines = getReceiptLineDTOS(receipt);
        receiptDTO.setLines(lines);
        return receiptDTO;
    }

    private static List<ReceiptLineDTO> getReceiptLineDTOS(Receipt receipt)
    {
        List<Purchase> purchases = receipt.getPurchases();
        List<ReceiptLineDTO> lines = new ArrayList<>();
        for (Purchase purchase : purchases)
        {
            ReceiptLineDTO lineDTO = new ReceiptLineDTO();
            lineDTO.setProductCode(purchase.getProduct().getCode());
            lineDTO.setProductName(purchase.getProduct().getName());
            lineDTO.setQuantity(purchase.getQuantity());
            lineDTO.setPrice(purchase.getPrice());
            lines.add(lineDTO);
        }
        return lines;
    }

    @Transactional(readOnly = true)
    public List<ReceiptDTO> getAllReceiptsOrdered(boolean date) throws NotFoundException
    {
        UserDTO currentUser = userService.getCurrentUser();

        List<Receipt> receipts;
        if (currentUser.getRole().equals("ROLE_ADMIN"))
        {
            receipts = receiptRepository.findAllWithPurchases();
        }
        else
        {
            User user = userRepository.findByEmail(currentUser.getEmail())
                    .orElseThrow(() -> new NotFoundException("User not found"));
            receipts = receiptRepository.findByUserWithPurchases(user);
        }


        if (receipts == null || receipts.isEmpty())
            return new ArrayList<>();

        if (date)   // SORT BY DATE
            receipts.sort(new ReceiptDateComparator());
        else        // SORT BY AMOUNT
            receipts.sort(new ReceiptAmountComparator());

        List<ReceiptDTO> receiptDTOs = new ArrayList<>();
        for (Receipt receipt : receipts)
            receiptDTOs.add(convertToDTO(receipt));
        return receiptDTOs;
    }

    @Transactional
    public void saveReceipt(ReceiptDTO receiptDTO) throws ConflictException, InvalidRequestException, NotFoundException
    {
        if (receiptDTO == null)
            throw new InvalidRequestException("Receipt not valid");
        Receipt receipt = new Receipt();

        if (receiptDTO.getCode() == null)
            throw new InvalidRequestException("Receipt code not valid");
        if (receiptRepository.findReceiptByCode(receiptDTO.getCode()).isPresent())
            throw new ConflictException("A receipt with this code already exists");
        receipt.setCode(receiptDTO.getCode());

        if (receiptDTO.getAmount() == null || receiptDTO.getAmount().compareTo(BigDecimal.ZERO) <= 0)
            throw new InvalidRequestException("Receipt amount not valid");
        receipt.setAmount(receiptDTO.getAmount());

        if (receiptDTO.getTax() == null || receiptDTO.getTax().compareTo(BigDecimal.ZERO) <= 0 || receiptDTO.getTax().compareTo(receiptDTO.getAmount()) >= 0)
            throw new InvalidRequestException("Receipt taxes not valid");
        receipt.setTax(receiptDTO.getTax());

        if (receiptDTO.getUserEmail() == null)
            throw new InvalidRequestException("Receipt user email not valid");

        User currentUser = userRepository.findByEmail(userService.getCurrentUser().getEmail())
                .orElseThrow(() -> new InvalidRequestException("Current user not found"));

        if (!currentUser.getRole().equals("ROLE_ADMIN") && !currentUser.getEmail().equals(receiptDTO.getUserEmail()))
            throw new InvalidRequestException("You are not authorized to add a receipt for another user");

        receipt.setUser(userRepository.findByEmail(receiptDTO.getUserEmail())
            .orElseThrow(() -> new NotFoundException("No user with this email exists")));

        if (receiptDTO.getPaymentMethod() == null)
            throw new InvalidRequestException("Receipt payment method not valid");
        receipt.setPaymentMethod(receiptDTO.getPaymentMethod());

        Instant date = receiptDTO.getDate();
        if (!DateValidator.isValid(date))
            throw new InvalidRequestException("Receipt date not valid");
        receipt.setDate(date);

        receiptRepository.save(receipt);

        List<ReceiptLineDTO> lines = receiptDTO.getLines();

        if (lines == null || lines.isEmpty())
            throw new InvalidRequestException("No items found in receipt");

        BigDecimal total = BigDecimal.ZERO;
        for (ReceiptLineDTO lineDTO : lines)
        {
            String productCode = lineDTO.getProductCode();
            String productName = lineDTO.getProductName();
            Integer quantity = lineDTO.getQuantity();
            BigDecimal price = lineDTO.getPrice();

            if (productCode == null)
                throw new InvalidRequestException("Product code not found");
            if (quantity == null || quantity <= 0)
                throw new InvalidRequestException("Quantity not valid");
            if (price == null || price.compareTo(BigDecimal.ZERO) <= 0)
                throw new InvalidRequestException("Price not valid");

            Product product = productRepository.findByCode(productCode)
                .orElseThrow(() -> new NotFoundException("Product not found"));
            total = total.add(price.multiply(BigDecimal.valueOf(quantity)));


            if (total.compareTo(receipt.getAmount()) > 0)
                throw new InvalidRequestException("Receipt amount not valid: items exceed total");

            Purchase purchase = new Purchase();
            purchase.setReceipt(receipt);
            purchase.setPrice(price);
            purchase.setQuantity(quantity);
            purchase.setProduct(product);
            purchaseRepository.save(purchase);
        }
        total = total.add(receipt.getTax());

        if (total.compareTo(receipt.getAmount()) != 0)
            throw new InvalidRequestException("Receipt amount mismatch: items total does not match receipt amount");

    }

    @Transactional(readOnly = true)
    public List<ReceiptDTO> findByUserEmailLike(String email, float threshold) throws InvalidRequestException
    {
        if (email == null)
            throw new InvalidRequestException("User email not valid");
        if (threshold < 0 || threshold > 1)
            throw new InvalidRequestException("Threshold not valid");
        if (!userService.getCurrentUser().getRole().equals("ROLE_ADMIN"))
            throw new InvalidRequestException("Unhauthorized");

        List<ReceiptDTO> receiptDTOs = new ArrayList<>();
        List<Receipt> receiptsWithDuplicates = receiptRepository.findByUserEmailLike("%"+email+"%");
        receiptsWithDuplicates.addAll(receiptRepository.findByUserEmailContains(email));
        // fuzzy search
        List<Receipt> allReceipts = receiptRepository.findAll(PageRequest.of(0, 500)).getContent();

        receiptsWithDuplicates.addAll(allReceipts.stream()
                .filter(receipt -> jaroWinklerSimilarity.apply(receipt.getUser().getEmail(), email) > threshold)
                .toList());

        // remove duplicates
        List<Receipt> receipts = new ArrayList<>(new HashSet<>(receiptsWithDuplicates));
        for (Receipt receipt : receipts)
            receiptDTOs.add(convertToDTO(receipt));
        return receiptDTOs;
    }

    @Transactional(readOnly = true)
    public List<ReceiptDTO> findByCodeLike(String code, float threshold) throws InvalidRequestException
    {
        if (code == null)
            throw new InvalidRequestException("Code not valid");
        if (threshold < 0 || threshold > 1)
            throw new InvalidRequestException("Threshold not valid");

        User currentUser = userRepository.findByEmail(userService.getCurrentUser().getEmail())
                .orElseThrow(() -> new InvalidRequestException("Current user not found"));

        List<ReceiptDTO> receiptDTOs = new ArrayList<>();
        List<Receipt> receiptsWithDuplicates = receiptRepository.findByCodeLike("%"+code+"%");
        receiptsWithDuplicates.addAll(receiptRepository.findByCodeContains(code));

        // fuzzy search
        List<Receipt> allReceipts = receiptRepository.findAll(PageRequest.of(0, 500)).getContent();
        JaroWinklerSimilarity jaroWinklerSimilarity = new JaroWinklerSimilarity();

        receiptsWithDuplicates.addAll(allReceipts.stream()
                .filter(receipt -> jaroWinklerSimilarity.apply(receipt.getCode(), code) > threshold)
                .toList());

        // remove duplicates
        List<Receipt> receipts = new ArrayList<>(new HashSet<>(receiptsWithDuplicates));
        for (Receipt receipt : receipts)
            if (currentUser.getRole().equals("ROLE_ADMIN") || receipt.getUser().getEmail().equals(currentUser.getEmail()))
                receiptDTOs.add(convertToDTO(receipt));
        return receiptDTOs;
    }

    @Transactional(readOnly = true)
    public List<ReceiptDTO> findByAmountBetween(BigDecimal amountMin, BigDecimal amountMax) throws InvalidRequestException
    {
        if (amountMin == null || amountMax == null)
            throw new InvalidRequestException("Amount range not valid");
        if (amountMin.compareTo(BigDecimal.ZERO) < 0 || amountMax.compareTo(BigDecimal.ZERO) < 0)
            throw new InvalidRequestException("Amount range not valid");
        if (amountMin.compareTo(amountMax) > 0)
            throw new InvalidRequestException("Invalid amount range given");

        User currentUser = userRepository.findByEmail(userService.getCurrentUser().getEmail())
                .orElseThrow(() -> new InvalidRequestException("Current user not found"));

        List<Receipt> receipts = receiptRepository.findReceiptByAmountBetween(amountMin, amountMax);
        List<ReceiptDTO> receiptDTOs = new ArrayList<>();
        for (Receipt receipt : receipts)
            if (currentUser.getRole().equals("ROLE_ADMIN") || receipt.getUser().getEmail().equals(currentUser.getEmail()))
                receiptDTOs.add(convertToDTO(receipt));
        return receiptDTOs;
    }

    // ReceiptService.java
    @Transactional
    public void deleteReceipt(String code) throws NotFoundException
    {
        UserDTO currentUser = userService.getCurrentUser();
        Receipt receipt = receiptRepository.findReceiptByCode(code)
                .orElseThrow(() -> new NotFoundException("Receipt not found"));

        if (!receipt.getUser().getEmail().equals(currentUser.getEmail()) && !currentUser.getRole().equals("ROLE_ADMIN"))
            throw new NotFoundException("Receipt not found"); // stesso pattern di getReceipt: non rivelo che esiste

        purchaseRepository.deleteAll(purchaseRepository.findByReceipt(receipt));

        // Il PDF su S3 sopravviveva alla ricevuta. Un errore qui non deve però
        // impedire la cancellazione: un file orfano nel bucket è meno grave di
        // una ricevuta che l'utente non riesce a eliminare.
        String s3Key = receipt.getS3Key();
        if (s3Key != null && !s3Key.isBlank())
        {
            try
            {
                s3Service.deletePDF(s3Key);
            } catch (RuntimeException e)
            {
                log.warn("PDF non eliminato da S3 per la ricevuta {} (chiave {}): {}",
                        receipt.getCode(), s3Key, e.getMessage());
            }
        }

        receiptRepository.delete(receipt);
    }

    // TODO statistiche e query AI
}
