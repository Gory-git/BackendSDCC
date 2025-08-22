package org.backendsdcc.models;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.Date;

@Getter
@Setter
@Entity
@Table(name = "receipt")
public class Receipt
{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "receipt_id", nullable = false)
    private long id;

    @Column(name = "code", nullable = false, unique = true)
    private String code;

    @Column(name = "amount", nullable = false)
    private float amount;

    @Column(name = "tax", nullable = false)
    private float tax;

    @Column(name = "date", nullable = false)
    private Date date;

    @Column(name = "payment_method", nullable = false)
    private String paymentMethod;

    @ManyToOne(optional = false, cascade = CascadeType.ALL)
    @JoinColumn(name = "associated_user", nullable = false)
    private User user;
}
