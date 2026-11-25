package com.slotsure.payment.event;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.slotsure.payment.service.PaymentService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class PaymentEventListenerTest {

    private final ObjectMapper objectMapper = new ObjectMapper()
            .findAndRegisterModules()
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
    private final PaymentService paymentService = mock(PaymentService.class);
    private final PaymentEventListener listener = new PaymentEventListener(objectMapper, paymentService);

    @Test
    void parsesPaymentRequestedEnvelope() throws Exception {
        String json = """
                {"eventId":"e-1","eventType":"payment.requested","occurredAt":"2026-11-26T01:00:00Z",
                 "correlationId":"o-1","payload":{"orderId":"o-1","customerId":"c-1","amount":199.90}}
                """;

        listener.onPaymentRequested(json);

        ArgumentCaptor<PaymentRequestedEvent> captor = ArgumentCaptor.forClass(PaymentRequestedEvent.class);
        verify(paymentService).handlePaymentRequested(eq("e-1"), captor.capture());
        assertThat(captor.getValue().orderId()).isEqualTo("o-1");
        assertThat(captor.getValue().customerId()).isEqualTo("c-1");
        assertThat(captor.getValue().amount()).isEqualByComparingTo(new BigDecimal("199.90"));
    }

    @Test
    void rejectsEnvelopeWithoutEventId() {
        String json = """
                {"eventType":"refund.requested","payload":{"orderId":"o-1","amount":10.00,"reason":"x"}}
                """;

        assertThatThrownBy(() -> listener.onRefundRequested(json)).isInstanceOf(IllegalArgumentException.class);
    }
}
