package com.mohitdeo.saga.inventory;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.mohitdeo.saga.inventory.InventoryDtos.Item;
import com.mohitdeo.saga.inventory.InventoryDtos.ProductView;
import com.mohitdeo.saga.inventory.InventoryDtos.ReservationView;
import com.mohitdeo.saga.inventory.InventoryDtos.ReserveResult;

/**
 * Reserves stock for a saga, and releases it as the compensating action.
 * <p>
 * Both calls are idempotent and keyed by saga id. If a release arrives before its reserve
 * (the reserve was delayed in the network), the release writes a tombstone so the late
 * reserve is refused and no stock leaks.
 */
@Service
public class InventoryService {

    private final ProductRepository products;
    private final StockReservationRepository reservations;
    private final TransactionTemplate tx;

    public InventoryService(ProductRepository products, StockReservationRepository reservations,
                            TransactionTemplate tx) {
        this.products = products;
        this.reservations = reservations;
        this.tx = tx;
    }

    public ReserveResult reserve(UUID sagaId, List<Item> requested) {
        // Merge duplicate SKUs; TreeMap also gives a deterministic lock order
        Map<String, Integer> wanted = requested.stream()
                .collect(Collectors.toMap(Item::sku, Item::quantity, Integer::sum, TreeMap::new));
        try {
            return tx.execute(status -> {
                var existing = reservations.findForUpdate(sagaId);
                if (existing.isPresent()) {
                    return new ReserveResult(replay(existing.get()), false);
                }

                Map<String, Product> locked = products.findAllForUpdate(wanted.keySet()).stream()
                        .collect(Collectors.toMap(Product::getSku, Function.identity()));

                List<String> unknown = wanted.keySet().stream().filter(sku -> !locked.containsKey(sku)).toList();
                if (!unknown.isEmpty()) {
                    throw InventoryException.unknownProducts("Unknown SKU(s): " + unknown);
                }
                List<String> shortSkus = wanted.entrySet().stream()
                        .filter(e -> !locked.get(e.getKey()).hasAtLeast(e.getValue()))
                        .map(Map.Entry::getKey)
                        .toList();
                if (!shortSkus.isEmpty()) {
                    throw InventoryException.insufficientStock("Not enough stock for: " + shortSkus);
                }

                wanted.forEach((sku, qty) -> locked.get(sku).take(qty));
                StockReservation reservation = StockReservation.reserved(sagaId, wanted.entrySet().stream()
                        .map(e -> new ReservedItem(e.getKey(), e.getValue())).toList());
                return new ReserveResult(ReservationView.from(reservations.saveAndFlush(reservation)), true);
            });
        } catch (DataIntegrityViolationException e) {
            // A concurrent duplicate (or a release tombstone) was inserted first
            StockReservation existing = reservations.findById(sagaId).orElseThrow(() -> e);
            return new ReserveResult(replay(existing), false);
        }
    }

    public ReservationView release(UUID sagaId) {
        try {
            return tx.execute(status -> {
                StockReservation reservation = reservations.findForUpdate(sagaId)
                        .orElseGet(() -> reservations.saveAndFlush(StockReservation.releasedTombstone(sagaId)));
                if (reservation.getStatus() == ReservationStatus.RESERVED) {
                    List<String> skus = reservation.getItems().stream().map(ReservedItem::getSku).toList();
                    Map<String, Product> locked = products.findAllForUpdate(skus).stream()
                            .collect(Collectors.toMap(Product::getSku, Function.identity()));
                    reservation.getItems().forEach(i -> locked.get(i.getSku()).putBack(i.getQuantity()));
                    reservation.markReleased();
                }
                return ReservationView.from(reservation);
            });
        } catch (DataIntegrityViolationException e) {
            // The reserve committed at the same moment; retry now that the row exists
            return release(sagaId);
        }
    }

    public ProductView product(String sku) {
        return products.findById(sku).map(ProductView::from)
                .orElseThrow(() -> InventoryException.notFound("Unknown SKU " + sku));
    }

    private static ReservationView replay(StockReservation existing) {
        if (existing.getStatus() == ReservationStatus.RELEASED) {
            throw InventoryException.alreadyReleased(
                    "Stock for saga " + existing.getSagaId() + " was already released");
        }
        return ReservationView.from(existing);
    }
}
