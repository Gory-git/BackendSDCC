package org.backendsdcc.support.pdf;

import org.backendsdcc.models.PaymentMethod;
import org.backendsdcc.support.dto.ReceiptDTO;

import java.math.BigDecimal;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.backendsdcc.support.dto.ReceiptLineDTO;
import org.backendsdcc.support.validators.DateValidator;

public class ReceiptPDFParser
{
    // Regex patterns per estrarre campi con label
    private static final Pattern RECEIPT_CODE_PATTERN =
            Pattern.compile("RECEIPT_CODE\\s*[:=]\\s*([^\\s]+)");
    private static final Pattern USER_EMAIL_PATTERN =
            Pattern.compile("USER_EMAIL\\s*[:=]\\s*([^\\s]+@[^\\s]+)");
    private static final Pattern DATE_PATTERN =
            Pattern.compile("DATE\\s*[:=]\\s*(\\d{4}-\\d{2}-\\d{2}-\\d{2}:\\d{2}:\\d{2})");
    private static final Pattern TAX_PATTERN =
            Pattern.compile("\\bTAX\\b\\s*[:=]\\s*([\\d.]+)");;
    private static final Pattern AMOUNT_PATTERN =
            Pattern.compile("TOTAL\\s*[:=]\\s*([\\d.]+)");
    private static final Pattern PAYMENT_PATTERN =
            Pattern.compile("PAYMENT\\s*[:=]\\s*([^\\n]+)");

    // Regex per righe prodotto: "CODE | NAME | QTY | PRICE"
    private static final Pattern LINE_PATTERN =
            Pattern.compile("^\\s*([^\\|]+)\\|([^\\|]+)\\|([\\d]+)\\|([\\d.]+)\\s*$",
                    Pattern.MULTILINE);

    public static ReceiptDTO parse(String pdfContent)
    {
        ReceiptDTO result = new ReceiptDTO();

        // Estrai campi singoli con regex
        result.setCode(extractField(pdfContent, RECEIPT_CODE_PATTERN, "RECEIPT_CODE"));
        result.setUserEmail(extractField(pdfContent, USER_EMAIL_PATTERN, "USER_EMAIL"));
        result.setTax(new BigDecimal(extractField(pdfContent, TAX_PATTERN, "TAX")));
        result.setAmount(new BigDecimal(extractField(pdfContent, AMOUNT_PATTERN, "TOTAL")));
        String rawPaymentMethod = extractField(pdfContent, PAYMENT_PATTERN, "PAYMENT").trim();
        try
        {
            result.setPaymentMethod(PaymentMethod.valueOf(rawPaymentMethod));
        } catch (IllegalArgumentException e)
        {
            throw new IllegalArgumentException("Invalid payment method: " + rawPaymentMethod);
        }
        result.setDate(DateValidator.parse(extractField(pdfContent, DATE_PATTERN, "DATE")));

        parseProductLines(pdfContent, result);

        return result;
    }

    private static String extractField(String content, Pattern pattern, String fieldName)
    {
        Matcher matcher = pattern.matcher(content);
        if (!matcher.find())
            throw new IllegalArgumentException("Field not found: " + fieldName);
        return matcher.group(1).trim();
    }

    private static void parseProductLines(String content, ReceiptDTO receipt)
    {
        Matcher matcher = LINE_PATTERN.matcher(content);
        while (matcher.find())
        {
            ReceiptLineDTO line = new ReceiptLineDTO();
            line.setProductCode(matcher.group(1).trim());
            line.setProductName(matcher.group(2).trim());
            line.setQuantity(Integer.parseInt(matcher.group(3).trim()));
            line.setPrice(new BigDecimal(matcher.group(4).trim()));
            List<ReceiptLineDTO> lines = receipt.getLines();
            if (lines == null)
                lines = new java.util.ArrayList<>();
            lines.add(line);
            receipt.setLines(lines);
        }

        if (receipt.getLines().isEmpty())
            throw new IllegalArgumentException("No product lines found in PDF");
    }
}
