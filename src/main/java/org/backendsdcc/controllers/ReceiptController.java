package org.backendsdcc.controllers;

import org.backendsdcc.services.ReceiptService;
import org.backendsdcc.services.S3Service;
import org.backendsdcc.support.dto.ReceiptDTO;
import org.backendsdcc.support.exceptions.NotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
    @PreAuthorize("hasAnyRole('ROLE_user','ROLE_admin')")
    public ResponseEntity<Map<String, String>> getPDFDownloadUrl(
            @PathVariable String code,
            @AuthenticationPrincipal UserDetails userDetails)
    {
        try
        {
            ReceiptDTO receipt = receiptService.getReceipt(code);
            if (receipt.getS3Key() == null)
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Nessun PDF allegato a questa ricevuta"));

            // URL valido 15 minuti — sicuro, non espone il bucket
            String url = s3Service.generatePresignedUrl(receipt.getS3Key(), 15);
            return ResponseEntity.ok(Map.of("downloadUrl", url));
        } catch (NotFoundException e)
        {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Ricevuta non trovata"));
        }

    }

}
