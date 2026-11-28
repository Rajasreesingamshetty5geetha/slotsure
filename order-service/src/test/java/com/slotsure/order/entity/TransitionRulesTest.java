package com.slotsure.order.entity;

import com.slotsure.order.exception.IllegalTransitionException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TransitionRulesTest {

    @Test
    void pendingCanMoveToEveryFinalStatus() {
        assertThat(OrderStatus.PENDING.canMoveTo(OrderStatus.CONFIRMED)).isTrue();
        assertThat(OrderStatus.PENDING.canMoveTo(OrderStatus.FAILED)).isTrue();
        assertThat(OrderStatus.PENDING.canMoveTo(OrderStatus.CANCELLED)).isTrue();
        assertThat(OrderStatus.PENDING.canMoveTo(OrderStatus.PENDING)).isFalse();
    }

    @Test
    void finalStatusesCannotMove() {
        for (OrderStatus from : new OrderStatus[]{OrderStatus.DELIVERED, OrderStatus.FAILED, OrderStatus.CANCELLED}) {
            assertThat(from.isFinal()).isTrue();
            for (OrderStatus to : OrderStatus.values()) {
                assertThat(from.canMoveTo(to)).as(from + " -> " + to).isFalse();
            }
        }
    }

    @Test
    void confirmedOrdersGoOutForDeliveryThenGetDelivered() {
        assertThat(OrderStatus.CONFIRMED.canMoveTo(OrderStatus.OUT_FOR_DELIVERY)).isTrue();
        assertThat(OrderStatus.CONFIRMED.canMoveTo(OrderStatus.DELIVERED)).isTrue();
        assertThat(OrderStatus.CONFIRMED.canMoveTo(OrderStatus.FAILED)).isFalse();
        assertThat(OrderStatus.CONFIRMED.canMoveTo(OrderStatus.PENDING)).isFalse();
        assertThat(OrderStatus.OUT_FOR_DELIVERY.canMoveTo(OrderStatus.DELIVERED)).isTrue();
        assertThat(OrderStatus.OUT_FOR_DELIVERY.canMoveTo(OrderStatus.CONFIRMED)).isFalse();
        assertThat(OrderStatus.CONFIRMED.isFinal()).isFalse();
        assertThat(OrderStatus.OUT_FOR_DELIVERY.isFinal()).isFalse();
    }

    @Test
    void paymentModesAllowOnlyTheirOwnChoices() {
        assertThat(PaymentMode.PREPAID.allows(PaymentChoice.PREPAID)).isTrue();
        assertThat(PaymentMode.PREPAID.allows(PaymentChoice.ONLINE)).isFalse();
        assertThat(PaymentMode.PREPAID.allows(PaymentChoice.CASH)).isFalse();
        assertThat(PaymentMode.COD.allows(PaymentChoice.ONLINE)).isTrue();
        assertThat(PaymentMode.COD.allows(PaymentChoice.CASH)).isTrue();
        assertThat(PaymentMode.COD.allows(PaymentChoice.PREPAID)).isFalse();
    }

    @Test
    void confirmedOrderRefusesToMoveToFailed() {
        Order order = new Order("customer-1", "hold-1", new BigDecimal("10"), "key-1");
        order.moveTo(OrderStatus.CONFIRMED);

        assertThatThrownBy(() -> order.moveTo(OrderStatus.FAILED))
                .isInstanceOf(IllegalTransitionException.class)
                .hasMessageContaining("CONFIRMED")
                .hasMessageContaining("FAILED");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test
    void sagaStepRules() {
        assertThat(SagaStep.PAYMENT_REQUESTED.canMoveTo(SagaStep.SLOT_CONFIRMING)).isTrue();
        assertThat(SagaStep.PAYMENT_REQUESTED.canMoveTo(SagaStep.COMPENSATING)).isTrue();
        assertThat(SagaStep.PAYMENT_REQUESTED.canMoveTo(SagaStep.DONE)).isFalse();
        assertThat(SagaStep.SLOT_CONFIRMING.canMoveTo(SagaStep.DONE)).isTrue();
        assertThat(SagaStep.SLOT_CONFIRMING.canMoveTo(SagaStep.COMPENSATING)).isTrue();
        assertThat(SagaStep.SLOT_CONFIRMING.canMoveTo(SagaStep.PAYMENT_REQUESTED)).isFalse();
        assertThat(SagaStep.COMPENSATING.canMoveTo(SagaStep.DONE)).isTrue();
        assertThat(SagaStep.COMPENSATING.canMoveTo(SagaStep.SLOT_CONFIRMING)).isFalse();
        for (SagaStep to : SagaStep.values()) {
            assertThat(SagaStep.DONE.canMoveTo(to)).as("DONE -> " + to).isFalse();
        }
    }

    @Test
    void finishedSagaRefusesToCompensate() {
        SagaInstance saga = new SagaInstance("order-1");
        saga.moveTo(SagaStep.SLOT_CONFIRMING);
        saga.moveTo(SagaStep.DONE);

        assertThatThrownBy(() -> saga.moveTo(SagaStep.COMPENSATING))
                .isInstanceOf(IllegalTransitionException.class)
                .hasMessageContaining("DONE");
        assertThat(saga.getStep()).isEqualTo(SagaStep.DONE);
    }

    @Test
    void amountIsStoredWithScaleTwo() {
        Order order = new Order("customer-1", "hold-1", new BigDecimal("10"), "key-1");
        assertThat(order.getAmount()).isEqualTo(new BigDecimal("10.00"));
    }
}
