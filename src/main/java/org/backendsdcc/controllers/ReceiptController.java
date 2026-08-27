package org.backendsdcc.controllers;

import com.itextpdf.text.DocumentException;
import jakarta.validation.Valid;
import org.backendsdcc.services.PDFService;
import org.backendsdcc.services.ReceiptService;
import org.backendsdcc.support.dto.ReceiptDTO;
import org.backendsdcc.support.exceptions.ConflictException;
import org.backendsdcc.support.exceptions.InvalidRequestException;
import org.backendsdcc.support.exceptions.NotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping(value = "/receipt")
public class ReceiptController
{
    @Autowired
    private ReceiptService receiptService;
    @Autowired
    private PDFService pdfService;

    @GetMapping("/{code}")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
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
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
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
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ResponseEntity<String> addReceipt(@RequestBody @Valid ReceiptDTO receiptDTO)
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

    @PostMapping("/upload-pdf")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ResponseEntity<String> uploadPDF(@RequestParam("file") MultipartFile file)
    {
        try
        {
            pdfService.importReceiptFromPdf(file);
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
        } catch (IOException e)
        {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Errore durante l'elaborazione del PDF: " + e.getMessage());
        }
    }

    @GetMapping(value = "/pdf/{code}", produces = MediaType.TEXT_PLAIN_VALUE)
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ResponseEntity<String> getPDFUrl(@PathVariable String code)
    {
        try
        {
            String pdfUrl = pdfService.getPDFUrlFromReceiptCode(code);
            return ResponseEntity.ok(pdfUrl);
        } catch (NotFoundException e)
        {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Errore: " + e.getMessage());
        } catch (InvalidRequestException e)
        {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Errore: " + e.getMessage());
        } catch (DocumentException e)
        {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Errore durante la generazione del PDF: " + e.getMessage());
        }
    }

    @GetMapping("find-by-email-like/{userEmail}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> getReceiptsByUser(@PathVariable String userEmail, @RequestParam("threshold") float threshold)
    {
        try
        {
            List<ReceiptDTO> receipts = receiptService.findByUserEmailLike(userEmail, threshold);
            return ResponseEntity.ok(receipts);
        } catch (InvalidRequestException e)
        {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Errore: " + e.getMessage());
        }
    }

    @GetMapping("find-by-code-like/{code}")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ResponseEntity<?> getReceiptsByCode(@PathVariable String code, @RequestParam("threshold") float threshold)
    {
        try
        {
            List<ReceiptDTO> receipts = receiptService.findByCodeLike(code, threshold);
            return ResponseEntity.ok(receipts);
        } catch (InvalidRequestException e)
        {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Errore: " + e.getMessage());
        }
    }

    @GetMapping("/find-by-amount")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ResponseEntity<?> getReceiptsByAmountRange(@RequestParam("amountMin") java.math.BigDecimal amountMin, @RequestParam("amountMax") java.math.BigDecimal amountMax)
    {
        try
        {
            List<ReceiptDTO> receipts = receiptService.findByAmountBetween(amountMin, amountMax);
            return ResponseEntity.ok(receipts);
        } catch (InvalidRequestException e)
        {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Errore: " + e.getMessage());
        }
    }

    // ReceiptController.java
    @DeleteMapping("/{code}")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ResponseEntity<String> deleteReceipt(@PathVariable String code)
    {
        try
        {
            receiptService.deleteReceipt(code);
            return ResponseEntity.ok("Ricevuta eliminata con successo");
        } catch (NotFoundException e)
        {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Errore: " + e.getMessage());
        }
    }

    // TODO statistiche e query AI
}
