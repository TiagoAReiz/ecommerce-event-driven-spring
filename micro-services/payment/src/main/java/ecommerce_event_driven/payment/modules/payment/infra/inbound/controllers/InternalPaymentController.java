package ecommerce_event_driven.payment.modules.payment.infra.inbound.controllers;

import ecommerce_event_driven.payment.modules.payment.application.dtos.PaymentResponse;
import ecommerce_event_driven.payment.modules.payment.application.usecases.ListPaymentsUseCase;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Leituras servidor-a-servidor. So o escopo internal:hydrate chega aqui (SecurityConfig). */
@RestController
@RequestMapping("/internal/payments")
public class InternalPaymentController {

    private final ListPaymentsUseCase listPayments;

    public InternalPaymentController(ListPaymentsUseCase listPayments) {
        this.listPayments = listPayments;
    }

    @GetMapping
    public List<PaymentResponse> byOrder(@RequestParam Long orderId) {
        return listPayments.forService(orderId).stream().map(PaymentResponse::from).toList();
    }
}
