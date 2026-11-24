package com.mohitdeo.saga.orchestrator.support;

import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.mohitdeo.saga.orchestrator.participant.InventoryClient;
import com.mohitdeo.saga.orchestrator.participant.OrderClient;
import com.mohitdeo.saga.orchestrator.participant.PaymentClient;
import com.mohitdeo.saga.orchestrator.participant.StepOutcome;
import com.mohitdeo.saga.orchestrator.saga.SagaItem;

/**
 * Stands in for all three participant services. Each operation returns scripted outcomes
 * in order, then Success once the script runs out (or the {@code always} outcome if set).
 * Every call is recorded, so tests can check the exact order of actions and compensations.
 */
public class FakeParticipants implements OrderClient, InventoryClient, PaymentClient {

    public static final String CREATE_ORDER = "createOrder";
    public static final String APPROVE_ORDER = "approveOrder";
    public static final String REJECT_ORDER = "rejectOrder";
    public static final String RESERVE_STOCK = "reserveStock";
    public static final String RELEASE_STOCK = "releaseStock";
    public static final String CHARGE = "charge";
    public static final String REFUND = "refund";

    private final Map<String, Deque<StepOutcome>> scripts = new HashMap<>();
    private final Map<String, StepOutcome> always = new HashMap<>();
    private final List<String> calls = Collections.synchronizedList(new ArrayList<>());

    public synchronized void script(String operation, StepOutcome... outcomes) {
        scripts.computeIfAbsent(operation, k -> new ArrayDeque<>()).addAll(List.of(outcomes));
    }

    public synchronized void always(String operation, StepOutcome outcome) {
        always.put(operation, outcome);
    }

    public synchronized void reset() {
        scripts.clear();
        always.clear();
        calls.clear();
    }

    public List<String> calls() {
        return List.copyOf(calls);
    }

    public long count(String operation) {
        return calls().stream().filter(operation::equals).count();
    }

    private synchronized StepOutcome next(String operation) {
        calls.add(operation);
        Deque<StepOutcome> script = scripts.get(operation);
        if (script != null && !script.isEmpty()) {
            return script.poll();
        }
        return always.getOrDefault(operation, StepOutcome.success());
    }

    @Override
    public StepOutcome createOrder(UUID sagaId, String customerId, List<SagaItem> items) {
        return next(CREATE_ORDER);
    }

    @Override
    public StepOutcome approveOrder(UUID sagaId) {
        return next(APPROVE_ORDER);
    }

    @Override
    public StepOutcome rejectOrder(UUID sagaId, String reason) {
        return next(REJECT_ORDER);
    }

    @Override
    public StepOutcome reserveStock(UUID sagaId, List<SagaItem> items) {
        return next(RESERVE_STOCK);
    }

    @Override
    public StepOutcome releaseStock(UUID sagaId) {
        return next(RELEASE_STOCK);
    }

    @Override
    public StepOutcome charge(UUID sagaId, String customerId, BigDecimal amount) {
        return next(CHARGE);
    }

    @Override
    public StepOutcome refund(UUID sagaId) {
        return next(REFUND);
    }
}
