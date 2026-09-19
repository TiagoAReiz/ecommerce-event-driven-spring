package ecommerce_event_driven.payment.modules.payment.infra.outbound.events;

import ecommerce_event_driven.payment.modules.payment.application.dtos.PaymentApprovedEvent;
import ecommerce_event_driven.payment.modules.payment.application.dtos.PaymentFailedEvent;
import ecommerce_event_driven.payment.modules.payment.application.dtos.PaymentRefundedEvent;
import ecommerce_event_driven.payment.shared.outbox.OutboxMessage;
import ecommerce_event_driven.payment.shared.outbox.OutboxWriter;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class PaymentEventOutboxPublisher {
    private final OutboxWriter outboxWriter;

    public PaymentEventOutboxPublisher(OutboxWriter outboxWriter) {
        this.outboxWriter = outboxWriter;
    }

    public void publishPaymentApproved(PaymentApprovedEvent event) {
        OutboxMessage message = new OutboxMessage(
                event.eventId(),
                "payment",
                event.orderId().toString(),
                "ecommerce.payment.approved.v1",
                "paymentApproved",
                event);
        outboxWriter.write(message);
    }

    public void publishPaymentFailed(PaymentFailedEvent event) {
        OutboxMessage message = new OutboxMessage(
                event.eventId(),
                "payment",
                event.orderId().toString(),
                "ecommerce.payment.failed.v1",
                "paymentFailed",
                event);
        outboxWriter.write(message);
    }

    public void publishPaymentRefunded(PaymentRefundedEvent event) {
        OutboxMessage message = new OutboxMessage(
                event.eventId(),
                "payment",
                event.orderId().toString(),
                "ecommerce.payment.refunded.v1",
                "paymentRefunded",
                event);
        outboxWriter.write(message);
    }
}
