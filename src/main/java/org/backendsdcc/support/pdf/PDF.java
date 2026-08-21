package org.backendsdcc.support.pdf;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.itextpdf.text.*;
import com.itextpdf.text.pdf.PdfPCell;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.PdfWriter;
import org.backendsdcc.models.Purchase;
import org.backendsdcc.models.Receipt;
import org.backendsdcc.support.dto.ReceiptDTO;
import org.backendsdcc.support.dto.ReceiptLineDTO;
import org.backendsdcc.support.validators.DateValidator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Component
public class PDF
{
    private final static String currencySymbol = "€";

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${table_noOfColumns}")
    private int noOfColumns;

    @Value("${table.columnNames}")
    private List<String> columnNames;

    private static final Font COURIER = new Font(Font.FontFamily.COURIER, 20, Font.BOLD);
    private static final Font COURIER_SMALL = new Font(Font.FontFamily.COURIER, 16, Font.BOLD);
    private static final Font COURIER_SMALL_FOOTER = new Font(Font.FontFamily.COURIER, 12, Font.BOLD);


    public byte[] generatePDF(Receipt receipt, List<Purchase> purchases) throws DocumentException
    {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document document = new Document();
        String documentName = "receipt_" + receipt.getCode() + "_" + receipt.getUser().getEmail() + "_" + receipt.getDate() + ".pdf";

        PdfWriter.getInstance(document, baos);
        document.addTitle(documentName);
        document.addAuthor("SDCC_GEN");
        try
        {
            document.addHeader("X-Receipt-Data", objectMapper.writeValueAsString(toReceiptDTO(receipt, purchases)));
        } catch (JsonProcessingException e)
        {
            throw new RuntimeException("Impossibile serializzare i dati della ricevuta", e);
        }
        document.open();
        addDocTitle(document, receipt);
        createTable(document, noOfColumns, purchases);
        addFooter(document, receipt);
        document.close();
        return baos.toByteArray();
    }

    private ReceiptDTO toReceiptDTO(Receipt receipt, List<Purchase> purchases)
    {
        ReceiptDTO dto = new ReceiptDTO();
        dto.setCode(receipt.getCode());
        dto.setAmount(receipt.getAmount());
        dto.setTax(receipt.getTax());
        dto.setDate(receipt.getDate());
        dto.setPaymentMethod(receipt.getPaymentMethod());
        dto.setUserEmail(receipt.getUser().getEmail());

        List<ReceiptLineDTO> lines = new ArrayList<>();
        for (Purchase p : purchases)
        {
            ReceiptLineDTO line = new ReceiptLineDTO();
            line.setProductCode(p.getProduct().getCode());
            line.setProductName(p.getProduct().getName());
            line.setQuantity(p.getQuantity());
            line.setPrice(p.getPrice());
            lines.add(line);
        }
        dto.setLines(lines);
        return dto;
    }


    private void addDocTitle(Document document, Receipt receipt) throws DocumentException
    {
        Paragraph p1 = new Paragraph();
        leaveEmptyLine(p1, 1);
        p1.add(new Paragraph("RECEIPT_CODE: " + receipt.getCode(), COURIER_SMALL_FOOTER));
        p1.add(new Paragraph("USER_EMAIL: " + receipt.getUser().getEmail(), COURIER_SMALL_FOOTER));
        p1.add(new Paragraph("DATE: " + DateValidator.format(receipt.getDate()), COURIER_SMALL_FOOTER));
        p1.setAlignment(Element.ALIGN_CENTER);
        leaveEmptyLine(p1, 2);

        document.add(p1);
    }

    private void addFooter(Document document, Receipt receipt) throws DocumentException
    {
        Paragraph p2 = new Paragraph();
        leaveEmptyLine(p2, 1);
        p2.setAlignment(Element.ALIGN_CENTER);
        p2.add(new Paragraph("TAX: " + currencySymbol + receipt.getTax(), COURIER_SMALL_FOOTER));
        p2.add(new Paragraph("TOTAL: " + currencySymbol + receipt.getAmount(), COURIER_SMALL_FOOTER));
        p2.add(new Paragraph("PAYMENT: " + receipt.getPaymentMethod(), COURIER_SMALL_FOOTER));
        leaveEmptyLine(p2, 3);

        document.add(p2);
    }

    private void createTable(Document document, int noOfColumns, List<Purchase> purchases) throws DocumentException
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

    private void getDbData(PdfPTable table, List<Purchase> purchases)
    {
        for (Purchase purchase : purchases)
        {
            table.setWidthPercentage(100);
            table.getDefaultCell().setHorizontalAlignment(Element.ALIGN_CENTER);
            table.getDefaultCell().setVerticalAlignment(Element.ALIGN_MIDDLE);

            table.addCell(purchase.getProduct().getCode());
            table.addCell(purchase.getProduct().getName());
            table.addCell(String.valueOf(purchase.getQuantity()));
            table.addCell(currencySymbol + purchase.getPrice().toString());
            BigDecimal total = BigDecimal.valueOf(purchase.getQuantity()).multiply(purchase.getPrice());
            table.addCell(currencySymbol + total);
        }
    }

    private static void leaveEmptyLine(Paragraph paragraph, int number)
    {
        for (int i = 0; i < number; i++)
        {
            paragraph.add(new Paragraph(" "));
        }
    }
}
