package org.backendsdcc.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.itextpdf.text.*;
import com.itextpdf.text.pdf.*;
import org.backendsdcc.models.Product;
import org.backendsdcc.models.Purchase;
import org.backendsdcc.models.Receipt;
import org.backendsdcc.repositories.*;
import org.backendsdcc.support.dto.ReceiptDTO;
import org.backendsdcc.support.dto.ReceiptLineDTO;
import org.backendsdcc.support.exceptions.NotFoundException;
import org.backendsdcc.support.pdf.PDF;
import org.backendsdcc.support.pdf.ReceiptPDFParser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.backendsdcc.support.exceptions.ConflictException;
import org.backendsdcc.support.exceptions.InvalidRequestException;

import java.io.IOException;

import org.springframework.web.multipart.MultipartFile;

@Service
public class PDFService
{
    private static final long MAX_SIZE_BYTES = 10 * 1024 * 1024; // 10 MB
    @Autowired
    private ReceiptRepository receiptRepository;
    @Autowired
    private PurchaseRepository purchaseRepository;
    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PDF pdfGenerator;
    @Autowired
    private S3Service s3Service;
    @Autowired
    private UserService userService;
    @Autowired
    private ObjectMapper objectMapper;


    @Transactional
    public void importReceiptFromPdf (MultipartFile file) throws IOException, InvalidRequestException, ConflictException, NotFoundException
    {
        if (file == null || file.isEmpty())
            throw new InvalidRequestException("File is empty or null");
        if (!"application/pdf".equalsIgnoreCase(file.getContentType()))
            throw new InvalidRequestException("Solo PDF ammessi");
        if (file.getSize() > MAX_SIZE_BYTES)
            throw new InvalidRequestException("File troppo grande");

        PdfReader reader = new PdfReader(file.getInputStream());

        if (!reader.getInfo().containsKey("Author") || reader.getInfo().containsKey("Author") && !reader.getInfo().get("Author").equals("SDCC_GEN"))
            throw new InvalidRequestException("Receipt incorrectly formatted");

        String receiptDataJson = reader.getInfo().get("X-Receipt-Data");
        reader.close();

        ReceiptDTO receiptParsed;
        try
        {
            receiptParsed = ReceiptPDFParser.parse(receiptDataJson, objectMapper);
        } catch (IllegalArgumentException e)
        {
            throw new InvalidRequestException("PDF parsing error: " + e.getMessage());
        }

        if (receiptRepository.findReceiptByCode(receiptParsed.getCode()).isPresent())
            throw new ConflictException("Receipt already exists");

        if (!userRepository.existsByEmail(receiptParsed.getUserEmail()))
            throw new NotFoundException("User not found: " + receiptParsed.getUserEmail());
        if (!userService.getCurrentUser().getEmail().equals(receiptParsed.getUserEmail()) && !userService.getCurrentUser().getRole().equals("ROLE_ADMIN"))
            throw new InvalidRequestException("You are not authorized to import a receipt for another user");

        Receipt receipt = new Receipt();
        receipt.setCode(receiptParsed.getCode());
        receipt.setDate(receiptParsed.getDate());
        receipt.setUser(userRepository.findByEmail(receiptParsed.getUserEmail())
            .orElseThrow(() -> new NotFoundException("User not found: " + receiptParsed.getUserEmail()))); // non propago un dato sbagliato, ma lancio un'eccezione. Se l'utente non esiste, non posso salvare lo scontrino.
        receipt.setTax(receiptParsed.getTax());
        receipt.setAmount(receiptParsed.getAmount());
        receipt.setPaymentMethod(receiptParsed.getPaymentMethod());
        receiptRepository.save(receipt);

        for (ReceiptLineDTO line : receiptParsed.getLines())
        {
            Product product = productRepository.findByCode(line.getProductCode())
                .orElseGet(() -> {
                    Product newProduct = new Product();
                    newProduct.setCode(line.getProductCode());
                    newProduct.setName(line.getProductName());
                    return productRepository.save(newProduct);
                });

            Purchase purchase = new Purchase();
            purchase.setReceipt(receipt);
            purchase.setProduct(product);
            purchase.setQuantity(line.getQuantity());
            purchase.setPrice(line.getPrice());
            purchaseRepository.save(purchase);
        }
    }

    @Transactional(readOnly = true)
    public String getPDFUrlFromReceiptCode(String code) throws DocumentException, NotFoundException, InvalidRequestException
    {
        if (code == null || code.isBlank())
            throw new InvalidRequestException("Invalid receipt code");
        return generateAndAttachPDF(code);
    }


    @Transactional
    public String generateAndAttachPDF(String code) throws NotFoundException, DocumentException, InvalidRequestException
    {
        Receipt receipt = receiptRepository.findReceiptByCode(code)
                .orElseThrow(() -> new NotFoundException("Receipt not found"));

        if (!userService.getCurrentUser().getRole().equals("ROLE_ADMIN") && !userService.getCurrentUser().getEmail().equals(receipt.getUser().getEmail()))
            throw new InvalidRequestException("You are not authorized to access this resource");

        byte[] pdfBytes = pdfGenerator.generatePDF(receipt, purchaseRepository.findByReceipt(receipt));

        String s3Key = s3Service.uploadPDF(pdfBytes, "receipts");

        receipt.setS3Key(s3Key);
        receiptRepository.save(receipt);
        return s3Service.generatePresignedUrl(s3Key, 15);
    }
}
