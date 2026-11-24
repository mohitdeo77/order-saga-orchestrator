package com.mohitdeo.saga.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;

import com.mohitdeo.saga.inventory.InventoryDtos.Item;

@SpringBootTest
@ActiveProfiles("test")
class InventoryServiceTest {

    @Autowired
    InventoryService inventory;
    @Autowired
    ProductRepository products;

    @Test
    void reserveDecrementsStockAndIsIdempotent() {
        String sku = newProduct(10);
        UUID sagaId = UUID.randomUUID();

        assertThat(inventory.reserve(sagaId, List.of(new Item(sku, 3))).created()).isTrue();
        assertThat(inventory.reserve(sagaId, List.of(new Item(sku, 3))).created()).isFalse();

        assertThat(stock(sku)).isEqualTo(7); // decremented exactly once
    }

    @Test
    void insufficientStockIsABusinessFailureAndChangesNothing() {
        String a = newProduct(5);
        String b = newProduct(1);

        assertThatThrownBy(() -> inventory.reserve(UUID.randomUUID(), List.of(new Item(a, 2), new Item(b, 2))))
                .isInstanceOfSatisfying(InventoryException.class,
                        e -> assertThat(e.status()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT));

        assertThat(stock(a)).isEqualTo(5);
        assertThat(stock(b)).isEqualTo(1);
    }

    @Test
    void releaseRestoresStockAndIsIdempotent() {
        String sku = newProduct(10);
        UUID sagaId = UUID.randomUUID();
        inventory.reserve(sagaId, List.of(new Item(sku, 4)));

        inventory.release(sagaId);
        inventory.release(sagaId);

        assertThat(stock(sku)).isEqualTo(10); // restored exactly once
    }

    @Test
    void releaseBeforeReserveBlocksTheLateReserve() {
        String sku = newProduct(10);
        UUID sagaId = UUID.randomUUID();

        inventory.release(sagaId); // compensation overtook the original request

        assertThatThrownBy(() -> inventory.reserve(sagaId, List.of(new Item(sku, 4))))
                .isInstanceOfSatisfying(InventoryException.class,
                        e -> assertThat(e.status()).isEqualTo(HttpStatus.CONFLICT));
        assertThat(stock(sku)).isEqualTo(10); // no leaked stock
    }

    @Test
    void concurrentSagasNeverOversell() throws Exception {
        String sku = newProduct(3);
        int threads = 12;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger succeeded = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> {
                start.await();
                try {
                    inventory.reserve(UUID.randomUUID(), List.of(new Item(sku, 1)));
                    succeeded.incrementAndGet();
                } catch (InventoryException e) {
                    rejected.incrementAndGet();
                }
                return null;
            }));
        }
        start.countDown();
        for (Future<?> f : futures) {
            f.get(30, TimeUnit.SECONDS);
        }
        pool.shutdown();

        assertThat(succeeded.get()).isEqualTo(3);
        assertThat(rejected.get()).isEqualTo(threads - 3);
        assertThat(stock(sku)).isZero();
    }

    private String newProduct(int quantity) {
        String sku = "TEST-" + UUID.randomUUID().toString().substring(0, 8);
        products.save(new Product(sku, "Test product", quantity));
        return sku;
    }

    private int stock(String sku) {
        return products.findById(sku).orElseThrow().getAvailableQuantity();
    }
}
