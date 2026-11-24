package com.mohitdeo.saga.inventory;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class ReservedItem {

    @Column(nullable = false)
    private String sku;

    @Column(nullable = false)
    private int quantity;

    protected ReservedItem() {
    }

    public ReservedItem(String sku, int quantity) {
        this.sku = sku;
        this.quantity = quantity;
    }

    public String getSku() {
        return sku;
    }

    public int getQuantity() {
        return quantity;
    }
}
