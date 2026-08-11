package org.backendsdcc.controllers;

import org.backendsdcc.services.ReceiptService;
import org.backendsdcc.services.S3Service;
import org.backendsdcc.support.dto.ReceiptDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.Map;

@Controller
public class ReceiptController
{
    @Autowired
    private ReceiptService receiptService;
    @Autowired
    private S3Service s3Service;

    @GetMapping("/receipts/{code}/pdf")
    public ResponseEntity<Map<String, String>> getPDFDownloadUrl(
            @PathVariable String code,
            @AuthenticationPrincipal UserDetails userDetails)
    {

        ReceiptDTO receipt = receiptService.getReceipt(code);

        // Controllo autorizzazione: base user vede solo le sue
        if (!isAdminOrOwner(userDetails, receipt))
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();

        if (receipt.getS3Key() == null)
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Nessun PDF allegato a questa ricevuta"));

        // URL valido 15 minuti — sicuro, non espone il bucket
        String url = s3Service.generatePresignedUrl(receipt.getS3Key(), 15);
        return ResponseEntity.ok(Map.of("downloadUrl", url));
    }

    private boolean isAdminOrOwner(UserDetails userDetails, ReceiptDTO receipt)
    {
        //TODO Implement the logic to check if the user is an admin or the owner of the receipt
        return false;
    }

}
