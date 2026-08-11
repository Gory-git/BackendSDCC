package org.backendsdcc.services;

import com.itextpdf.text.*;
import com.itextpdf.text.pdf.*;
import com.itextpdf.text.pdf.parser.PdfTextExtractor;
import jakarta.persistence.EntityNotFoundException;
import org.backendsdcc.models.Product;
import org.backendsdcc.models.Purchase;
import org.backendsdcc.models.Receipt;
import org.backendsdcc.repositories.*;
import static org.backendsdcc.support.pdf.PDF.generatePDF;
import org.backendsdcc.support.dto.ReceiptDTO;
import org.backendsdcc.support.dto.ReceiptLineDTO;
import org.backendsdcc.support.pdf.ReceiptPDFParser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.List;

import org.springframework.web.multipart.MultipartFile;

@Service
public class PDFService
{
    private final static String currencySymbol = "€";
    @Autowired
    private ReceiptRepository receiptRepository;
    @Autowired
    private PurchaseRepository purchaseRepository;
    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private UserRepository userRepository;


    @Transactional
    public void saveReceiptFromUniformPDF (MultipartFile file) throws IOException
    {
        StringBuilder pdfContent = new StringBuilder();
        // Leggi il PDF
        PdfReader reader = new PdfReader(file.getInputStream());

        if (reader.getInfo().containsKey("Author") && !reader.getInfo().get("Author").equals("SDCC_GEN"))
            throw new RuntimeException("Receipt incorrectly formatted");

        // Itera attraverso le pagine del PDF
        for (int i = 1; i <= reader.getNumberOfPages(); i++)
            pdfContent.append(PdfTextExtractor.getTextFromPage(reader, i));

        reader.close();

        ReceiptDTO receiptParsed;
        try
        {
            receiptParsed = ReceiptPDFParser.parse(pdfContent.toString());
        } catch (IllegalArgumentException e)
        {
            throw new RuntimeException("PDF parsing error: " + e.getMessage());
        }

        if (receiptRepository.findReceiptByCode(receiptParsed.getCode()).isPresent())
            throw new RuntimeException("Receipt already exists");

        Receipt receipt = new Receipt();
        receipt.setCode(receiptParsed.getCode());
        receipt.setDate(receiptParsed.getDate());
        receipt.setUser(userRepository.findByEmail(receiptParsed.getUserEmail())
            .orElseThrow(() -> new RuntimeException("User not found: " + receiptParsed.getUserEmail()))); // non propago un dato sbagliato, ma lancio un'eccezione. Se l'utente non esiste, non posso salvare lo scontrino.
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
    public byte[] getPDFFromReceiptCode(String code) throws DocumentException, IOException
    {
        return getPDFFromReceipt(receiptRepository.findReceiptByCode(code)
            .orElseThrow(() -> new EntityNotFoundException("Receipt not found: " + code)));
    }

    @Transactional(readOnly = true)
    public byte[] getPDFFromReceipt(Receipt receipt) throws DocumentException, IOException
    {
        List<Purchase> purchases = purchaseRepository.findByReceipt(receipt);
        return generatePDF(receipt, purchases);
    }

    // TODO lettura pdf generico
    // TODO conservazione pdf sul db
}
