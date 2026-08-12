package org.backendsdcc.controllers;

import org.backendsdcc.services.ReceiptService;
import org.backendsdcc.services.S3Service;
import org.backendsdcc.support.dto.ReceiptDTO;
import org.backendsdcc.support.exceptions.ConflictException;
import org.backendsdcc.support.exceptions.InvalidRequestException;
import org.backendsdcc.support.exceptions.NotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping(value = "/receipt")
@CrossOrigin(
        origins = "http://localhost:4200",
        allowedHeaders = "*",
        methods = { RequestMethod.GET, RequestMethod.POST }
)
public class ReceiptController
{
    @Autowired
    private ReceiptService receiptService;
    @Autowired
    private S3Service s3Service;

    @GetMapping("/{code}/pdf")
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

    @GetMapping("/{code}")
    @PreAuthorize("hasAnyRole('ROLE_user','ROLE_admin')")
    public ResponseEntity<ReceiptDTO> getReceipt(@PathVariable String code)
    {
        try
        {
            ReceiptDTO receipt = receiptService.getReceipt(code);
            return ResponseEntity.ok(receipt);
        } catch (NotFoundException e)
        {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    @GetMapping("/all/{date}")
    @PreAuthorize("hasAnyRole('ROLE_user','ROLE_admin')")
    public ResponseEntity<List<ReceiptDTO>> getAllReceipts(@PathVariable Boolean date)
    {
        try
        {
            List<ReceiptDTO> receipts = receiptService.getAllReceiptsOrdered(date);
            return ResponseEntity.ok(receipts);
        } catch (NotFoundException e)
        {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    @PostMapping("/add")
    @PreAuthorize("hasAnyRole('ROLE_user','ROLE_admin')")
    public ResponseEntity<String> addReceipt(@RequestParam ReceiptDTO receiptDTO)
    {
        try
        {
            receiptService.saveReceipt(receiptDTO);
            return ResponseEntity.status(HttpStatus.CREATED).body("Ricevuta aggiunta con successo");
        } catch (NotFoundException e)
        {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Errore: " + e.getMessage());
        } catch (InvalidRequestException e)
        {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Errore: " + e.getMessage());
        } catch (ConflictException e)
        {
            return ResponseEntity.status(HttpStatus.CONFLICT).body("Errore: " + e.getMessage());
        }
    }

    @PostMapping("/upload-pdf/{code}")
    @PreAuthorize("hasAnyRole('ROLE_user','ROLE_admin')")
    public ResponseEntity<String> uploadPDF(@PathVariable String code, @RequestParam("file") byte[] file)
    {
        try
        {
            receiptService.uploadPDF(code, file); // TODO mi sono dimenticato di fare l'upload del PDF su S3, quindi non funziona
            return ResponseEntity.ok("PDF caricato con successo");
        } catch (NotFoundException e)
        {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Errore: " + e.getMessage());
        } catch (InvalidRequestException e)
        {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Errore: " + e.getMessage());
        } catch (ConflictException e)
        {
            return ResponseEntity.status(HttpStatus.CONFLICT).body("Errore: " + e.getMessage());
        }
    }

    @GetMapping("find-by-email-like/{userEmail}")
    @PreAuthorize("hasAuthority('ROLE_admin')")
    public ResponseEntity<List<ReceiptDTO>> getReceiptsByUser(@PathVariable String userEmail, @RequestParam("threshold") float threshold)
    {
        try
        {
            List<ReceiptDTO> receipts = receiptService.findByUserEmailLike(userEmail, threshold);
            return ResponseEntity.ok(receipts);
        } catch (InvalidRequestException e)
        {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(List.of("Errore: " + e.getMessage())); // TODO vedere che vuole st'errore
        }
    }

    @GetMapping("find-by-code-like/{code}")
    @PreAuthorize("hasAnyRole('ROLE_user','ROLE_admin')")
    public ResponseEntity<List<ReceiptDTO>> getReceiptsByCode(@PathVariable String code, @RequestParam("threshold") float threshold)
    {
        try
        {
            List<ReceiptDTO> receipts = receiptService.findByCodeLike(code, threshold);
            return ResponseEntity.ok(receipts);
        } catch (InvalidRequestException e)
        {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(List.of("Errore: " + e.getMessage())); // TODO vedere che vuole st'errore
        }
    }

    // TODO statistiche e query AI
}
