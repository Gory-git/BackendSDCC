package org.backendsdcc.models;

/**
 * Metodi di pagamento supportati dal sistema.
 * Salvati come stringa nel DB (EnumType.STRING) per leggibilità
 * e resistenza al riordino dei valori.
 */
public enum PaymentMethod {
    CASH,
    CREDIT_CARD,
    DEBIT_CARD,
    PAYPAL,
    BANK_TRANSFER
}