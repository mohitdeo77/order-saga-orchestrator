package com.mohitdeo.saga.inventory;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "products")
public class Product {

    @Id
    private String sku;

    @Column(nullable = false)
    private String name;

    @Column(name = "available_quantity", nullable = false)
    private int availableQuantity;

    @Version
    private Long version;

    protected Product() {
    }

    public Product(String sku, String name, int availableQuantity) {
        this.sku = sku;
        this.name = name;
        this.availableQuantity = availableQuantity;
    }

    boolean hasAtLeast(int quantity) {
        return availableQuantity >= quantity;
    }

    void take(int quantity) {
        if (!hasAtLeast(quantity)) {
            throw new IllegalStateException("Insufficient stock for " + sku);
        }
        availableQuantity -= quantity;
    }

    void putBack(int quantity) {
        availableQuantity += quantity;
    }

    public String getSku() {
        return sku;
    }

    public String getName() {
        return name;
    }

    public int getAvailableQuantity() {
        return availableQuantity;
    }
}
