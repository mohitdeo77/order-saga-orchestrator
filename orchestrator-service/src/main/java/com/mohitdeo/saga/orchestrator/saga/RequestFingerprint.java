package com.mohitdeo.saga.orchestrator.saga;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Collectors;

/** SHA-256 of the order request, the same whatever order the items arrive in. */
final class RequestFingerprint {

    private RequestFingerprint() {
    }

    static String of(String customerId, List<SagaItem> items) {
        String canonical = customerId + "|" + items.stream()
                .sorted(Comparator.comparing(SagaItem::getSku))
                .map(i -> i.getSku() + "x" + i.getQuantity() + "@" + i.getUnitPrice().stripTrailingZeros().toPlainString())
                .collect(Collectors.joining(","));
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
