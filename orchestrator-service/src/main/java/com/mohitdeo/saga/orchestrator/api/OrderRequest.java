package com.mohitdeo.saga.orchestrator.api;

import java.math.BigDecimal;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record OrderRequest(
        @NotBlank @Size(max = 100) String customerId,
        @NotEmpty @Size(max = 50) List<@Valid Item> items) {

    public record Item(
            @NotBlank @Size(max = 50) String sku,
            @Min(1) int quantity,
            @NotNull @DecimalMin("0.01") BigDecimal unitPrice) {
    }
}
