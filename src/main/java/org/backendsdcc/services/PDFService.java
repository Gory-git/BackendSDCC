package org.backendsdcc.services;

import com.itextpdf.text.*;
import com.itextpdf.text.pdf.*;
import org.backendsdcc.models.PaymentMethod;
import org.backendsdcc.models.Product;
import org.backendsdcc.models.Purchase;
import org.backendsdcc.models.Receipt;
import org.backendsdcc.repositories.*;
import static org.backendsdcc.support.pdf.PDF.generatePDF;

import org.backendsdcc.support.validators.DateValidator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Arrays;
import java.time.Instant;
import java.util.List;
import java.util.StringTokenizer;

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


    @Transactional(readOnly = true)
    public void saveReceiptFromUniformPDF (MultipartFile file) throws IOException
    {
        StringBuilder pdfContent = new StringBuilder();
        // Leggi il PDF
        PdfReader reader = new PdfReader(file.getInputStream());

        if (reader.getInfo().containsKey("author") &&  !reader.getInfo().get("author").equals("SDCC_GEN"))
        {
            throw new RuntimeException("Receipt incorrectly formatted");
        }

        // Itera attraverso le pagine del PDF
        for (int i = 1; i <= reader.getNumberOfPages(); i++) {
            pdfContent.append(Arrays.toString(reader.getPageContent(i)));
        }

        StringTokenizer stringTokenizer = new StringTokenizer(pdfContent.toString(), " _\n\t"+currencySymbol);

        stringTokenizer.nextToken();
        String receiptCode = stringTokenizer.nextToken();

        if (receiptRepository.findReceiptByCode(receiptCode) != null)
        {
            throw new RuntimeException("Receipt already exists");
        }

        String userEmail = stringTokenizer.nextToken();
        String stringDate = stringTokenizer.nextToken();


        Instant date = DateValidator.parse(stringDate);

        stringTokenizer.nextToken(); // product code
        stringTokenizer.nextToken(); // product name
        stringTokenizer.nextToken(); // product quantity
        stringTokenizer.nextToken(); // product price
        stringTokenizer.nextToken(); // total price

//        List<Product> products = new ArrayList<>();
//        List<Purchase> purchases = new ArrayList<>();

        Receipt receipt = new Receipt();
        receipt.setCode(receiptCode);
        receipt.setDate(date);
        receipt.setUser(userRepository.findByEmail(userEmail));

        while (stringTokenizer.hasMoreTokens())
        {
            String productCode = stringTokenizer.nextToken();
            if  (productCode.equals("Tax:"))
                break;
            String productName = stringTokenizer.nextToken();
            int quantity = Integer.parseInt(stringTokenizer.nextToken());
            BigDecimal price = new BigDecimal(stringTokenizer.nextToken());
            BigDecimal total = new BigDecimal(stringTokenizer.nextToken());

            if (productRepository.findByCode(productCode) == null)
            {
                Product product = new Product();
                product.setCode(productCode);
                product.setName(productName);
                productRepository.save(product);
            }
            Product product = productRepository.findByCode(productCode);
//            products.add(product);

            Purchase purchase = new Purchase();
            purchase.setReceipt(receipt);
            purchase.setProduct(product);
            purchase.setQuantity(quantity);
            purchase.setPrice(price);
            purchaseRepository.save(purchase);
//            purchases.add(purchase);
        }
        BigDecimal tax = new BigDecimal(stringTokenizer.nextToken());
        stringTokenizer.nextToken();
        BigDecimal amount = new BigDecimal(stringTokenizer.nextToken());
        stringTokenizer.nextToken();
        PaymentMethod paymentMethod = PaymentMethod.valueOf(stringTokenizer.nextToken());

        receipt.setTax(tax);
        receipt.setAmount(amount);
        receipt.setPaymentMethod(paymentMethod);

        receiptRepository.save(receipt);
    }

    @Transactional(readOnly = true)
    public Document getPDFFromReceiptCode(String code) throws DocumentException, IOException
    {
        return getPDFFromReceipt(receiptRepository.findReceiptByCode(code));
    }

    @Transactional(readOnly = true)
    public Document getPDFFromReceipt(Receipt receipt) throws DocumentException, IOException
    {
        List<Purchase> purchases = purchaseRepository.findByReceipt(receipt);
        return generatePDF(receipt, purchases);
    }

    // TODO lettura pdf generico
    // TODO conservazione pdf sul db
}
