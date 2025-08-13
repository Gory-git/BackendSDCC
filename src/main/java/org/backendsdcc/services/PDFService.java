package org.backendsdcc.services;

import com.itextpdf.text.pdf.*;
import org.backendsdcc.models.Product;
import org.backendsdcc.models.Purchase;
import org.backendsdcc.models.Receipt;
import org.backendsdcc.repositories.ProductRepository;
import org.backendsdcc.repositories.PurchaseRepository;
import org.backendsdcc.repositories.ReceiptRepository;
import org.backendsdcc.repositories.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.StringTokenizer;

import com.itextpdf.text.BaseColor;
import com.itextpdf.text.Document;
import com.itextpdf.text.DocumentException;
import com.itextpdf.text.Element;
import com.itextpdf.text.Font;
import com.itextpdf.text.Image;
import com.itextpdf.text.Paragraph;
import com.itextpdf.text.Phrase;
import org.springframework.web.multipart.MultipartFile;

@Service
public class PDFService
{

    @Value("${logoImgPath}")
    private String logoImgPath;

    @Value("${logoImgScale}")
    private Float[] logoImgScale;

    @Value("${currencySymbol:}")
    private String currencySymbol;

    @Value("${table_noOfColumns}")
    private int noOfColumns;

    @Value("${table.columnNames}")
    private List<String> columnNames;

    private static final Font COURIER = new Font(Font.FontFamily.COURIER, 20, Font.BOLD);
    private static final Font COURIER_SMALL = new Font(Font.FontFamily.COURIER, 16, Font.BOLD);
    private static final Font COURIER_SMALL_FOOTER = new Font(Font.FontFamily.COURIER, 12, Font.BOLD);

    @Autowired
    private ReceiptRepository receiptRepository;

    @Autowired
    private PurchaseRepository purchaseRepository;

    private String documentName;
    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private UserRepository userRepository;

    private Document generatePDF(Receipt receipt)
    {
        Document document = new Document();
        documentName = "receipt_" + receipt.getCode() + "_" + receipt.getUser().getEmail() + "_" + receipt.getDate() + ".pdf";
        try
        {
            PdfWriter.getInstance(document, new FileOutputStream(documentName));
            document.open();
            addLogo(document);
            addDocTitle(document, receipt);
            createTable(document, noOfColumns, receipt);
            addFooter(document, receipt);
            document.close();

        } catch (FileNotFoundException | DocumentException e)
        {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return document;
    }

    private void addLogo(com.itextpdf.text.Document document) {
        try {
            Image img = Image.getInstance(logoImgPath);
            img.scalePercent(logoImgScale[0], logoImgScale[1]);
            img.setAlignment(Element.ALIGN_RIGHT);
            document.add(img);
        } catch (DocumentException | IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    private void addDocTitle(com.itextpdf.text.Document document, Receipt receipt) throws DocumentException {
        Paragraph p1 = new Paragraph();
        leaveEmptyLine(p1, 1);
        p1.add(new Paragraph(documentName, COURIER));
        p1.setAlignment(Element.ALIGN_CENTER);
        leaveEmptyLine(p1, 1);
        p1.add(new Paragraph("Receipt issued on" + receipt.getDate(), COURIER_SMALL));

        document.add(p1);
    }

    private void createTable(com.itextpdf.text.Document document, int noOfColumns, Receipt receipt) throws DocumentException {
        Paragraph paragraph = new Paragraph();
        leaveEmptyLine(paragraph, 3);
        document.add(paragraph);

        PdfPTable table = new PdfPTable(noOfColumns);

        for(int i=0; i<noOfColumns; i++) {
            PdfPCell cell = new PdfPCell(new Phrase(columnNames.get(i)));
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            cell.setBackgroundColor(BaseColor.CYAN);
            table.addCell(cell);
        }

        table.setHeaderRows(1);
        getDbData(table, receipt);
        document.add(table);
    }

    private void getDbData(PdfPTable table, Receipt receipt) throws DocumentException
    {
        List<Purchase> purchases = purchaseRepository.findByReceipt(receipt);

        for (Purchase purchase : purchases)
        {

            table.setWidthPercentage(100);
            table.getDefaultCell().setHorizontalAlignment(Element.ALIGN_CENTER);
            table.getDefaultCell().setVerticalAlignment(Element.ALIGN_MIDDLE);

            table.addCell(purchase.getProduct().getCode());
            table.addCell(purchase.getProduct().getName());
            table.addCell(String.valueOf(purchase.getQuantity()));
            table.addCell(currencySymbol + purchase.getPrice());
            float total = purchase.getQuantity() * purchase.getPrice();
            table.addCell(currencySymbol + total);
        }

    }

    private void addFooter(Document document, Receipt receipt) throws DocumentException {
        Paragraph p2 = new Paragraph();
        leaveEmptyLine(p2, 1);
        p2.setAlignment(Element.ALIGN_CENTER);
        p2.add(new Paragraph(
                "Tax:   " + currencySymbol + receipt.getTax(),
                COURIER_SMALL));
        p2.add(new Paragraph(
                "Total: " + currencySymbol + receipt.getAmount(),
                COURIER_SMALL));
        leaveEmptyLine(p2, 3);
        p2.setAlignment(Element.ALIGN_MIDDLE);
        p2.add(new Paragraph(
                "End Of " + documentName,
                COURIER_SMALL_FOOTER));

        document.add(p2);
    }

    private static void leaveEmptyLine(Paragraph paragraph, int number) {
        for (int i = 0; i < number; i++) {
            paragraph.add(new Paragraph(" "));
        }
    }
    @Transactional(readOnly = true)
    public void saveReceiptFromPDF (MultipartFile file) throws IOException // TODO
    {
        StringBuilder pdfContent = new StringBuilder();
        // Leggi il PDF
        PdfReader reader = new PdfReader(file.getInputStream());

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
        String date = stringTokenizer.nextToken();
        stringTokenizer.nextToken(); // product code
        stringTokenizer.nextToken(); // product name
        stringTokenizer.nextToken(); // product quantity
        stringTokenizer.nextToken(); // product price
        stringTokenizer.nextToken(); // total price

        List<Product> products = new ArrayList<>();
        List<Purchase> purchases = new ArrayList<>();

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
            float price = Float.parseFloat(stringTokenizer.nextToken());
            float total = Float.parseFloat(stringTokenizer.nextToken());

            if (productRepository.findByCode(productCode) == null)
            {
                Product product = new Product();
                product.setCode(productCode);
                product.setName(productName);
                productRepository.save(product);
            }
            Product product = productRepository.findByCode(productCode);
            products.add(product);

            Purchase purchase = new Purchase();
            purchase.setReceipt(receipt);
            purchase.setProduct(product);
            purchase.setQuantity(quantity);
            purchase.setPrice(price);
            purchaseRepository.save(purchase);
            purchases.add(purchase);
        }
        float tax = Float.parseFloat(stringTokenizer.nextToken());
        stringTokenizer.nextToken();
        float amount = Float.parseFloat(stringTokenizer.nextToken());
        receipt.setTax(tax);
        receipt.setAmount(amount);
        receiptRepository.save(receipt);
    }

    @Transactional(readOnly = true)
    public Document getPDFFromReceiptID(long receiptID)
    {
        return getPDFFromReceipt(receiptRepository.findReceiptById(receiptID));
    }

    @Transactional(readOnly = true)
    public Document getPDFFromReceipt(Receipt receipt)
    {
        return generatePDF(receipt);
    }
}
