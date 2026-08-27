package org.backendsdcc.controllers;

import jakarta.validation.Valid;
import org.backendsdcc.services.SmartQueryService;
import org.backendsdcc.support.dto.ChatRequestDTO;
import org.backendsdcc.support.dto.ChatResponseDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Il chatbot e' fuori da /admin apposta: ci arrivano sia i clienti sia gli
 * amministratori, e a filtrare cosa possono vedere sono gli strumenti, non
 * l'URL. Un cliente che chiede il fatturato totale riceve una risposta che
 * spiega che il dato non e' suo, non un 403 sull'intero endpoint.
 */
@RestController
@RequestMapping(value = "/chat")
public class ChatController
{
    @Autowired
    private SmartQueryService smartQueryService;

    @PostMapping
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ResponseEntity<?> ask(@RequestBody @Valid ChatRequestDTO request)
    {
        if (!smartQueryService.isEnabled())
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body("Il chatbot non e' configurato su questo ambiente");

        String answer = smartQueryService.ask(request.getQuestion(), request.getHistory());
        return ResponseEntity.ok(new ChatResponseDTO(answer));
    }

    /**
     * Serve al frontend per non mostrare la pagina della chat dove la chiave non
     * c'e' (ad esempio in locale), invece di far scoprire il problema all'utente
     * dopo che ha scritto la domanda.
     */
    @GetMapping("/status")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ResponseEntity<Boolean> status()
    {
        return ResponseEntity.ok(smartQueryService.isEnabled());
    }
}
