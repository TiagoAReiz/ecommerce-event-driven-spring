package ecommerce_event_driven.payment.modules.payment.application.usecases;

import ecommerce_event_driven.payment.modules.payment.application.ports.outbound.repos.PaymentRepositoryPort;
import ecommerce_event_driven.payment.modules.payment.domain.models.Payment;
import ecommerce_event_driven.payment.shared.client.OrderServiceClient;
import ecommerce_event_driven.payment.shared.web.NotFoundException;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

/** Todas as tentativas de pagamento de um pedido, da mais nova para a mais antiga. */
@Service
public class ListPaymentsUseCase {

    private final PaymentRepositoryPort paymentRepository;
    private final OrderServiceClient orderServiceClient;

    public ListPaymentsUseCase(PaymentRepositoryPort paymentRepository, OrderServiceClient orderServiceClient) {
        this.paymentRepository = paymentRepository;
        this.orderServiceClient = orderServiceClient;
    }

    /**
     * Para o cliente, confere a posse do pedido no order. Pedido de outro cliente responde 404
     * como inexistente, para nao confirmar que o id existe.
     */
    public List<Payment> forCustomer(Long orderId, Long currentUserId, boolean isOwner) {
        if (!isOwner) {
            JsonNode order = orderServiceClient.getOrder(orderId);
            if (order == null || order.get("idCustomer") == null
                    || order.get("idCustomer").asLong() != currentUserId) {
                throw new NotFoundException("Pedido nao encontrado");
            }
        }
        return forService(orderId);
    }

    /** Leitura servidor-a-servidor: o chamador ja e confiavel pelo escopo internal:hydrate. */
    public List<Payment> forService(Long orderId) {
        return paymentRepository.findByIdOrder(orderId).stream()
                .sorted(Comparator.comparing(Payment::id).reversed())
                .toList();
    }
}
