package org.backendsdcc.services;

import org.backendsdcc.models.PaymentMethod;
import org.backendsdcc.models.Product;
import org.backendsdcc.models.Receipt;
import org.backendsdcc.models.User;
import org.backendsdcc.repositories.*;
import org.backendsdcc.support.comparators.ReceiptAmountComparator;
import org.backendsdcc.support.comparators.ReceiptDateComparator;
import org.backendsdcc.support.dto.ProductDTO;
import org.backendsdcc.support.dto.ReceiptDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.ArrayList;
import java.util.List;

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
    private PaymentMethodRepository paymentMethodRepository;

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

        List<ProductDTO> productDTOs = new ArrayList<>();
        List<Integer> quantities = new ArrayList<>();
        List<Product> products = productRepository.findProductByReceipt(receipt);

        for (Product product : products)
        {
            ProductDTO productDTO = new ProductDTO();
            productDTO.setCode(product.getCode());
            productDTO.setName(product.getName());
            productDTOs.add(productDTO);
            quantities.add(productRepository.getProductQuantityByReceiptAndProduct(receipt, product));
        }

        receiptDTO.setProducts(productDTOs);
        receiptDTO.setQuantities(quantities);

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

        if (receiptDTO.getAmount() <= 0)
            throw new RuntimeException("Receipt amount not valid");
        receipt.setAmount(receiptDTO.getAmount());

        if (receiptDTO.getUserEmail() == null)
            throw new RuntimeException("Receipt user email not valid");
        if (!userRepository.existsByEmail(receiptDTO.getUserEmail()))
            throw new RuntimeException("No user with this email exists");
        receipt.setUser(userRepository.findByEmail(receiptDTO.getUserEmail()));

        if (receiptDTO.getPaymentMethod() == null)
            throw new RuntimeException("Receipt payment method not valid");
        if (!receiptDTO.getPaymentMethod().equals("CONTANTI") && !receiptDTO.getPaymentMethod().equals("BONIFICO") && receiptDTO.getPaymentMethod().length() != 4)
            throw new RuntimeException("Receipt payment method not valid");
        if (receiptDTO.getPaymentMethod().length() == 4)
        {
            boolean find = false;
            List<PaymentMethod> paymentMethods = paymentMethodRepository.findByUser(receipt.getUser());
            for (PaymentMethod paymentMethod : paymentMethods)
                if (paymentMethod.getCode().equals(receiptDTO.getPaymentMethod()))
                {
                    find = true;
                    break;
                }
            if (!find)
                throw new RuntimeException("Unknown payment method");
        }
        receipt.setPaymentMethod(receiptDTO.getPaymentMethod());


    }
}
