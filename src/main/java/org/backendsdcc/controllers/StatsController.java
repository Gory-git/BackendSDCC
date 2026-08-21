package org.backendsdcc.controllers;

import org.backendsdcc.services.StatsService;
import org.backendsdcc.support.exceptions.InvalidRequestException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

@RestController
@RequestMapping(value = "/admin/stats")
@CrossOrigin(
        origins = "http://localhost:5173",
        allowedHeaders = "*",
        methods = { RequestMethod.GET }
)
public class StatsController
{
    @Autowired
    private StatsService statsService;

    @GetMapping("/revenue")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> getRevenue(@RequestParam Instant dateMin, @RequestParam Instant dateMax)
    {
        try
        {
            return ResponseEntity.ok(statsService.getRevenueOverTime(dateMin, dateMax));
        } catch (InvalidRequestException e)
        {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Errore: " + e.getMessage());
        }
    }

    @GetMapping("/top-products")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> getTopProducts(@RequestParam Instant dateMin, @RequestParam Instant dateMax, @RequestParam(defaultValue = "10") int limit)
    {
        try
        {
            return ResponseEntity.ok(statsService.getTopProducts(dateMin, dateMax, limit));
        } catch (InvalidRequestException e)
        {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Errore: " + e.getMessage());
        }
    }

    @GetMapping("/payment-methods")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> getPaymentMethods(@RequestParam Instant dateMin, @RequestParam Instant dateMax)
    {
        try
        {
            return ResponseEntity.ok(statsService.getPaymentMethodBreakdown(dateMin, dateMax));
        } catch (InvalidRequestException e)
        {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Errore: " + e.getMessage());
        }
    }

    @GetMapping("/top-users")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> getTopUsers(@RequestParam Instant dateMin, @RequestParam Instant dateMax, @RequestParam(defaultValue = "10") int limit)
    {
        try
        {
            return ResponseEntity.ok(statsService.getTopUsers(dateMin, dateMax, limit));
        } catch (InvalidRequestException e)
        {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Errore: " + e.getMessage());
        }
    }

    @GetMapping("/summary")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> getSummary(@RequestParam Instant dateMin, @RequestParam Instant dateMax)
    {
        try
        {
            return ResponseEntity.ok(statsService.getSummary(dateMin, dateMax));
        } catch (InvalidRequestException e)
        {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Errore: " + e.getMessage());
        }
    }
}
