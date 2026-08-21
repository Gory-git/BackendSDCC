package org.backendsdcc.support.pdf;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.backendsdcc.support.dto.ReceiptDTO;

public class ReceiptPDFParser
{
    public static ReceiptDTO parse(String receiptDataJson, ObjectMapper objectMapper) throws IllegalArgumentException
    {
        if (receiptDataJson == null || receiptDataJson.isBlank())
            throw new IllegalArgumentException("Missing receipt data in PDF metadata");

        try
        {
            return objectMapper.readValue(receiptDataJson, ReceiptDTO.class);
        } catch (Exception e)
        {
            throw new IllegalArgumentException("Invalid receipt data in PDF metadata: " + e.getMessage());
        }
    }
}
