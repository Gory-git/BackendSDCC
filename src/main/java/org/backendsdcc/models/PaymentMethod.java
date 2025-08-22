package org.backendsdcc.models;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "payment_method")
public class PaymentMethod
{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(nullable = false)
    private Long id;

    @Column(name = "code", nullable = false, unique = true, length = 4)
    private String code;

    @ManyToOne(optional = false, cascade = CascadeType.ALL)
    @JoinColumn(name = "associated_user", nullable = false)
    private User user;
}
