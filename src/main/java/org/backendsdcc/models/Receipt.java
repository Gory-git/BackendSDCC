package org.backendsdcc.models;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Entity
@Table(name = "receipt")
public class Receipt
{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "receipt_id", nullable = false)
    private Long id;

    @Column(name = "code", nullable = false, unique = true)
    private String code;

    @Column(name = "amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(name = "tax", nullable = false, precision = 10, scale = 2)
    private BigDecimal tax;

    @Column(name = "date", nullable = false)
    private Instant date;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false)
    private PaymentMethod paymentMethod;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /**
     * Le ultime quattro cifre della carta usata per pagare, quando si e' pagato
     * con una carta. Mai il numero completo: vedi CardValidator per il perche'.
     * Nullo per contante, bonifico e PayPal, e per le ricevute inserite prima
     * che questo campo esistesse.
     */
    @Column(name = "card_last4", length = 4)
    private String cardLast4;

    @Column(name = "s3_key", nullable = true)
    private String s3Key;

    @OneToMany(mappedBy = "receipt", fetch = FetchType.LAZY)
    private List<Purchase> purchases = new ArrayList<>();

}
