package ecommerce_event_driven.order.modules.order.application.usecases;

import ecommerce_event_driven.order.config.KafkaConfig.InvalidEventException;
import ecommerce_event_driven.order.modules.order.application.ports.outbound.messaging.OrderEventPublisherPort;
import ecommerce_event_driven.order.modules.order.application.ports.outbound.repos.OrderRepositoryPort;
import ecommerce_event_driven.order.modules.order.domain.models.Order;
import ecommerce_event_driven.order.modules.order.domain.models.OrderItem;
import ecommerce_event_driven.order.modules.order.domain.models.OrderStatus;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Trata o evento shipment.status.changed.
 * Atualiza projecoes e transiciona o status do pedido conforme o status do envio.
 * Respeita monotonicidade: recusa transicoes para tras.
 */
@Component
public class ShipmentStatusChangedEventHandler {
    private static final Logger log = LoggerFactory.getLogger(ShipmentStatusChangedEventHandler.class);

    private final OrderRepositoryPort orders;
    private final OrderEventPublisherPort publisher;

    public ShipmentStatusChangedEventHandler(OrderRepositoryPort orders, OrderEventPublisherPort publisher) {
        this.orders = orders;
        this.publisher = publisher;
    }

    @Transactional
    public void handle(Long orderId, Long shipmentId, String from, String to, String trackingCode, Instant changedAt) throws InvalidEventException {
        Order order = orders.findById(orderId)
                .orElseThrow(() -> new InvalidEventException("Pedido " + orderId + " nao encontrado"));

        // Validar transicao: evitar retrocesso (monotonicidade)
        if (wouldRegress(order.shipmentStatus(), to)) {
            log.debug("shipment.status.changed ignorado: pedido {} retrocederia de {} para {}", orderId, order.shipmentStatus(), to);
            return;
        }

        // Atualizar projecao base (shipment_id, shipment_status, tracking_code)
        Order with_shipment = order.toBuilder()
                .shipmentId(shipmentId)
                .shipmentStatus(to)
                .trackingCode(trackingCode != null ? trackingCode : order.trackingCode())
                .build();

        // Decidir se muda o status do pedido
        switch (to) {
            case "pending":
                // Primeira transicao: so atualiza projecao
                orders.save(with_shipment);
                log.debug("Envio criado para pedido {}: shipmentId {}", orderId, shipmentId);
                break;

            case "ready_to_ship":
                // So atualiza projecao
                orders.save(with_shipment);
                log.debug("Envio ready_to_ship para pedido {}", orderId);
                break;

            case "in_transit":
                // processing -> shipped
                if (order.status() == OrderStatus.processing) {
                    boolean updated = orders.updateStatus(orderId, OrderStatus.processing, OrderStatus.shipped);
                    if (updated) {
                        Order updated_order = orders.findById(orderId)
                                .orElseThrow(() -> new InvalidEventException("Pedido " + orderId + " nao encontrado"));
                        Order with_status = updated_order.toBuilder()
                                .shipmentStatus(to)
                                .trackingCode(trackingCode != null ? trackingCode : order.trackingCode())
                                .build();
                        orders.save(with_status);
                        log.info("Pedido {} transicionou processing -> shipped (in_transit)", orderId);
                    } else {
                        log.debug("in_transit nao atualizou pedido {}: ja nao esta em processing", orderId);
                    }
                } else {
                    // Estado a frente ou ja shipped: so atualiza projecao
                    orders.save(with_shipment);
                    log.debug("in_transit nao altera status do pedido {} (estado atual: {})", orderId, order.status());
                }
                break;

            case "out_for_delivery":
                // So atualiza projecao
                orders.save(with_shipment);
                log.debug("Envio out_for_delivery para pedido {}", orderId);
                break;

            case "delivered":
                // processing ou shipped -> delivered
                if (order.status() == OrderStatus.processing || order.status() == OrderStatus.shipped) {
                    boolean updated = orders.updateStatus(orderId, order.status(), OrderStatus.delivered);
                    if (updated) {
                        Order updated_order = orders.findById(orderId)
                                .orElseThrow(() -> new InvalidEventException("Pedido " + orderId + " nao encontrado"));
                        Order with_status = updated_order.toBuilder()
                                .shipmentStatus(to)
                                .trackingCode(trackingCode != null ? trackingCode : order.trackingCode())
                                .build();
                        orders.save(with_status);

                        // Publicar order.delivered com produtos distintos
                        List<Long> distinctProductIds = updated_order.items().stream()
                                .map(OrderItem::idProduct)
                                .distinct()
                                .collect(Collectors.toList());
                        publisher.publishOrderDelivered(with_status, changedAt);

                        log.info("Pedido {} transicionou para delivered (shipment.status.changed)", orderId);
                    } else {
                        log.debug("delivered nao atualizou pedido {}: estado mudou entre leitura e escrita", orderId);
                    }
                } else {
                    log.debug("delivered ignorado para pedido {} em estado {}", orderId, order.status());
                }
                break;

            case "returned":
                // So atualiza projecao
                orders.save(with_shipment);
                log.debug("Envio returned para pedido {}", orderId);
                break;

            case "cancelled":
                // processing -> cancelled, ou ignora se ja cancelado
                if (order.status() == OrderStatus.processing) {
                    boolean updated = orders.updateStatus(orderId, OrderStatus.processing, OrderStatus.cancelled);
                    if (updated) {
                        Order updated_order = orders.findById(orderId)
                                .orElseThrow(() -> new InvalidEventException("Pedido " + orderId + " nao encontrado"));
                        Order with_status = updated_order.toBuilder()
                                .shipmentStatus(to)
                                .build();
                        orders.save(with_status);

                        // Publicar order.cancelled e order.refund.requested
                        publisher.publishOrderCancelled(orderId, updated_order.idCustomer(), "envio cancelado");
                        if (updated_order.paymentId() != null) {
                            publisher.publishOrderRefundRequested(updated_order, updated_order.paymentId(), "envio cancelado");
                        }

                        log.info("Pedido {} cancelado por envio cancelado", orderId);
                    } else {
                        log.debug("shipment cancelled nao atualizou pedido {}: ja nao esta em processing", orderId);
                    }
                } else if (order.status() == OrderStatus.cancelled) {
                    // Echo do cancelamento que o proprio pedido publicou: ignorar
                    log.debug("shipment.status.changed cancelled ignorado: e eco do cancelamento do pedido {}", orderId);
                } else {
                    log.debug("shipment cancelled ignorado para pedido {} em estado {}", orderId, order.status());
                }
                break;

            default:
                log.warn("shipment.status.changed com status desconhecido: {}", to);
        }
    }

    /**
     * Verifica se uma transicao de status de envio causaria retrocesso no pedido.
     * Estados de envio nao mapeiam 1:1 para pedido, mas alguns causam transicoes.
     */
    private boolean wouldRegress(String currentShipmentStatus, String newShipmentStatus) {
        if (currentShipmentStatus == null) {
            return false; // Primeira vez, nao pode regedir
        }

        // Ordem valida de shipment_status (conforme docs/api-contracts.md 10.2 ou docs/event-contracts.md 7.1)
        // Simplificado: so retrocede se tentar voltar a um status anterior
        return getShipmentStatusLevel(newShipmentStatus) < getShipmentStatusLevel(currentShipmentStatus);
    }

    private int getShipmentStatusLevel(String status) {
        return switch (status) {
            case "pending" -> 1;
            case "ready_to_ship" -> 2;
            case "in_transit" -> 3;
            case "out_for_delivery" -> 4;
            case "delivered" -> 5;
            case "returned" -> 6;
            case "cancelled" -> 7; // Cancelado pode vir de qualquer estado
            default -> 0;
        };
    }
}
