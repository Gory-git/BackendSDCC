package org.backendsdcc.services;

import org.backendsdcc.models.Receipt;
import org.backendsdcc.repositories.ProductRepository;
import org.backendsdcc.repositories.ReceiptRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
public class PDFService
{
    @Autowired
    private ReceiptRepository  receiptRepository;

    @Autowired
    private ProductRepository productRepository;

    @Transactional(readOnly = true)
    public void savePDF ()
    {
        // TODO
    }

    @Transactional(readOnly = true)
    public Receipt getPDF(long receiptID)
    {
        Receipt receipt = receiptRepository.findReceiptById(receiptID);
        if (receipt == null)
            throw new RuntimeException("Receipt not found");
        return receipt; // TODO
    }
}
