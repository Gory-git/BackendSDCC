package org.backendsdcc.support.PDF;

import org.backendsdcc.models.Purchase;
import org.backendsdcc.models.Receipt;
import org.backendsdcc.repositories.ProductRepository;
import org.backendsdcc.repositories.PurchaseRepository;
import org.backendsdcc.repositories.ReceiptRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import com.itextpdf.text.BaseColor;
import com.itextpdf.text.Document;
import com.itextpdf.text.DocumentException;
import com.itextpdf.text.Element;
import com.itextpdf.text.Font;
import com.itextpdf.text.Image;
import com.itextpdf.text.Paragraph;
import com.itextpdf.text.Phrase;
import com.itextpdf.text.pdf.PdfPCell;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.PdfWriter;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class PDFGenerator
{
    @Value("${pdfDir}")
    private String pdfDir;

    @Value("${documentNameDateFormat}")
    private String documentNameDateFormat;

    @Value("${localDateFormat}")
    private String localDateFormat;

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

    private static Font COURIER = new Font(Font.FontFamily.COURIER, 20, Font.BOLD);
    private static Font COURIER_SMALL = new Font(Font.FontFamily.COURIER, 16, Font.BOLD);
    private static Font COURIER_SMALL_FOOTER = new Font(Font.FontFamily.COURIER, 12, Font.BOLD);

    @Autowired
    private ReceiptRepository receiptRepository;

    @Autowired
    private PurchaseRepository purchaseRepository;

    @Autowired
    private ProductRepository productRepository;

    private String documentName;
    private Receipt receipt;

    public void generatePDF(long receiptID)
    {
        Document document = new Document();
        receipt = receiptRepository.findReceiptById(receiptID);
        documentName = "receipt_" + receipt.getCode() + "_" + receipt.getDate() + ".pdf";
        try
        {
            PdfWriter.getInstance(document, new FileOutputStream(documentName));
            document.open();
            addLogo(document);
            addDocTitle(document);
            createTable(document, noOfColumns, receiptID);
            addFooter(document);
            document.close();

        } catch (FileNotFoundException | DocumentException e)
        {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    private void addLogo(Document document) {
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

    private void addDocTitle(Document document) throws DocumentException {
        Paragraph p1 = new Paragraph();
        leaveEmptyLine(p1, 1);
        p1.add(new Paragraph(documentName, COURIER));
        p1.setAlignment(Element.ALIGN_CENTER);
        leaveEmptyLine(p1, 1);
        p1.add(new Paragraph("Receipt issued on" + receipt.getDate(), COURIER_SMALL));

        document.add(p1);
    }

    private void createTable(Document document, int noOfColumns, long receiptID) throws DocumentException {
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
        getDbData(table, receiptID);
        document.add(table);
    }

    private void getDbData(PdfPTable table, long receiptID) throws DocumentException
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

    private void addFooter(Document document) throws DocumentException {
        Paragraph p2 = new Paragraph();
        leaveEmptyLine(p2, 3);
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

}
