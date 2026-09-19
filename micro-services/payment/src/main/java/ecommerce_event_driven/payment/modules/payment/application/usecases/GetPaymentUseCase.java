package ecommerce_event_driven.payment.modules.payment.application.usecases;

import ecommerce_event_driven.payment.modules.payment.application.ports.outbound.repos.PaymentRepositoryPort;
import ecommerce_event_driven.payment.modules.payment.domain.models.Payment;
import ecommerce_event_driven.payment.shared.client.OrderServiceClient;
import ecommerce_event_driven.payment.shared.web.NotFoundException;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

@Service
public class GetPaymentUseCase {
    private final PaymentRepositoryPort paymentRepository;
    private final OrderServiceClient orderServiceClient;

    public GetPaymentUseCase(PaymentRepositoryPort paymentRepository, OrderServiceClient orderServiceClient) {
        this.paymentRepository = paymentRepository;
        this.orderServiceClient = orderServiceClient;
    }

    public Payment execute(Long paymentId, Long currentUserId, boolean isOwner) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new NotFoundException("Pagamento não encontrado"));

        if (!isOwner) {
            JsonNode order = orderServiceClient.getOrder(payment.idOrder());
            if (order == null) {
                throw new NotFoundException("Pedido não encontrado");
            }
            Long idCustomer = order.get("idCustomer").asLong();
            if (!idCustomer.equals(currentUserId)) {
                throw new NotFoundException("Pagamento não encontrado");
            }
        }

        return payment;
    }
}
