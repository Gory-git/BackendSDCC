package org.backendsdcc.support.pdf;

import com.itextpdf.text.*;
import com.itextpdf.text.pdf.PdfPCell;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.PdfWriter;
import org.backendsdcc.models.Purchase;
import org.backendsdcc.models.Receipt;
import org.backendsdcc.repositories.PurchaseRepository;
import org.springframework.beans.factory.annotation.Value;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

public class PDF
{
    private final static String currencySymbol = "€";

    @Value("${table_noOfColumns}")
    private static int noOfColumns;

    @Value("${table.columnNames}")
    private static List<String> columnNames;

    private static final Font COURIER = new Font(Font.FontFamily.COURIER, 20, Font.BOLD);
    private static final Font COURIER_SMALL = new Font(Font.FontFamily.COURIER, 16, Font.BOLD);
    private static final Font COURIER_SMALL_FOOTER = new Font(Font.FontFamily.COURIER, 12, Font.BOLD);

    private static String documentName;

    public static Document generatePDF(Receipt receipt, List<Purchase> purchases) throws IOException, DocumentException
    {
        Document document = new Document();
        documentName = "receipt_" + receipt.getCode() + "_" + receipt.getUser().getEmail() + "_" + receipt.getDate() + ".pdf";

        PdfWriter.getInstance(document, new FileOutputStream(documentName));
        document.addAuthor("SDCC_GEN");
        document.open();
        addDocTitle(document, receipt);
        createTable(document, noOfColumns, purchases);
        addFooter(document, receipt);
        document.close();
        return document;
    }


    private static void addDocTitle(Document document, Receipt receipt) throws DocumentException
    {
        Paragraph p1 = new Paragraph();
        leaveEmptyLine(p1, 1);
        p1.add(new Paragraph(documentName, COURIER));
        p1.setAlignment(Element.ALIGN_CENTER);
        leaveEmptyLine(p1, 1);
        p1.add(new Paragraph("Receipt issued on" + receipt.getDate(), COURIER_SMALL));

        document.add(p1);
    }

    private static void createTable(Document document, int noOfColumns, List<Purchase> purchases) throws DocumentException
    {
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
        getDbData(table, purchases);
        document.add(table);
    }

    private static void getDbData(PdfPTable table, List<Purchase> purchases)
    {
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

    private static void addFooter(Document document, Receipt receipt) throws DocumentException
    {
        Paragraph p2 = new Paragraph();
        leaveEmptyLine(p2, 1);
        p2.setAlignment(Element.ALIGN_CENTER);
        p2.add(new Paragraph(
                "Tax:   " + currencySymbol + receipt.getTax(),
                COURIER_SMALL));
        p2.add(new Paragraph(
                "Total: " + currencySymbol + receipt.getAmount(),
                COURIER_SMALL));
        p2.add(new Paragraph(
                "Payment: " + currencySymbol + receipt.getPaymentMethod(),
                COURIER_SMALL));
        leaveEmptyLine(p2, 3);
        p2.setAlignment(Element.ALIGN_MIDDLE);
        p2.add(new Paragraph(
                "End Of " + documentName,
                COURIER_SMALL_FOOTER));

        document.add(p2);
    }

    private static void leaveEmptyLine(Paragraph paragraph, int number)
    {
        for (int i = 0; i < number; i++)
        {
            paragraph.add(new Paragraph(" "));
        }
    }
}
