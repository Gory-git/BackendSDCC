package org.backendsdcc.services;

import org.backendsdcc.repositories.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class SmartQueryService
{
    @Autowired
    private ReceiptRepository receiptRepository;
    @Autowired
    private PurchaseRepository purchaseRepository;
    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private UserRepository userRepository;

    // TODO capire come posso inserire query dinamiche, magari con Criteria API o QueryDSL, per poter fare query complesse sui dati dei ricevute, acquisti, prodotti e utenti.
}
