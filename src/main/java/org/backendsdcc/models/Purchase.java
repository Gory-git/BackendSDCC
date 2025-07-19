package org.backendsdcc.models;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "purchase")
public class Purchase
{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private long id;

    @ManyToOne(optional = false, cascade = CascadeType.ALL)
    @JoinColumn(name = "product", nullable = false, unique = true)
    private Product product;

    @Column(name = "quantity", nullable = false)
    private float quantity;

    @Column(name = "price", nullable = false)
    private float price;

    @ManyToOne(optional = false, cascade = CascadeType.ALL)
    @JoinColumn(name = "receipt", nullable = false, unique = true)
    private Receipt receipt;
}
