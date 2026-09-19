package ecommerce_event_driven.inventory.modules.product.infra.inbound.controllers;

import ecommerce_event_driven.inventory.modules.product.application.dtos.HydrationProductResponse;
import ecommerce_event_driven.inventory.modules.product.application.dtos.HydrationProductResponse.HydrationProduct;
import ecommerce_event_driven.inventory.modules.product.application.dtos.ReservationResponse;
import ecommerce_event_driven.inventory.modules.product.application.dtos.ReservationResponse.ReservationItem;
import ecommerce_event_driven.inventory.modules.product.application.mappers.ProductMapper;
import ecommerce_event_driven.inventory.modules.product.application.usecases.AvailabilityService;
import ecommerce_event_driven.inventory.modules.product.infra.outbound.repos.ProductJpaRepository;
import ecommerce_event_driven.inventory.modules.product.infra.outbound.repos.ProductPhotoJpaRepository;
import ecommerce_event_driven.inventory.modules.product.infra.outbound.repos.StockReservationJpaRepository;
import ecommerce_event_driven.inventory.shared.web.NotFoundException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal")
@PreAuthorize("hasAuthority('SCOPE_internal:hydrate')")
public class InternalProductController {

    private final ProductJpaRepository productRepository;
    private final ProductPhotoJpaRepository photoRepository;
    private final StockReservationJpaRepository reservationRepository;
    private final AvailabilityService availabilityService;

    public InternalProductController(
            ProductJpaRepository productRepository,
            ProductPhotoJpaRepository photoRepository,
            StockReservationJpaRepository reservationRepository,
            AvailabilityService availabilityService) {
        this.productRepository = productRepository;
        this.photoRepository = photoRepository;
        this.reservationRepository = reservationRepository;
        this.availabilityService = availabilityService;
    }

    // Task 6.1: GET /internal/products?ids=1,2,3
    @GetMapping("/products")
    public ResponseEntity<HydrationProductResponse> getProducts(
            @RequestParam String ids) {

        String[] idArray = ids.split(",");
        List<Long> idList = new ArrayList<>();
        for (String id : idArray) {
            try {
                idList.add(Long.parseLong(id.trim()));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("IDs devem ser números");
            }
        }

        if (idList.size() > 100) {
            throw new IllegalArgumentException("Máximo 100 IDs");
        }

        List<HydrationProduct> products = new ArrayList<>();
        List<Long> missing = new ArrayList<>();

        for (Long id : idList) {
            var productEntity = productRepository.findById(id);
            if (productEntity.isPresent()) {
                var entity = productEntity.get();
                var product = ProductMapper.toDomain(entity);

                // Pegar primeira foto
                String photoUrl = photoRepository.findByProductIdAndDeletedAtIsNullOrderByPositionAsc(id)
                    .stream()
                    .findFirst()
                    .map(p -> p.getPhotoUrl())
                    .orElse(null);

                // Calcular disponibilidade
                int available = availabilityService.getAvailability(id);

                products.add(new HydrationProduct(
                    product.id(),
                    product.name(),
                    product.price().toPlainString(),
                    photoUrl,
                    available,
                    entity.getDeletedAt() == null)); // active = not deleted
            } else {
                missing.add(id);
            }
        }

        return ResponseEntity.ok(new HydrationProductResponse(products, missing));
    }

    // Task 6.1: GET /internal/reservations?orderId=123
    @GetMapping("/reservations")
    public ResponseEntity<ReservationResponse> getReservations(
            @RequestParam Long orderId) {

        var reservations = reservationRepository.findByIdOrder(orderId);

        List<ReservationItem> items = reservations.stream()
            .map(r -> new ReservationItem(
                r.getIdProduct(),
                r.getQuantity(),
                r.getStatus().toString().toLowerCase(),
                r.getExpiresAt()))
            .toList();

        return ResponseEntity.ok(new ReservationResponse(orderId, items));
    }

    // Task 6.1: POST /internal/reservations/{orderId}/confirm
    @PostMapping("/reservations/{orderId}/confirm")
    public ResponseEntity<Void> confirmReservation(@PathVariable Long orderId) {
        var reservation = reservationRepository.findByIdOrder(orderId);
        if (reservation.isEmpty()) {
            throw new NotFoundException("Reserva não encontrada");
        }

        // Implementar confirmação de reserva
        // Mudar status de held para confirmed
        // Decrementar stock

        return ResponseEntity.ok().build();
    }

    // Task 6.1: POST /internal/reservations/{orderId}/release
    @PostMapping("/reservations/{orderId}/release")
    public ResponseEntity<Void> releaseReservation(@PathVariable Long orderId) {
        var reservation = reservationRepository.findByIdOrder(orderId);
        if (reservation.isEmpty()) {
            throw new NotFoundException("Reserva não encontrada");
        }

        // Implementar liberação de reserva
        // Mudar status de held para released

        return ResponseEntity.ok().build();
    }
}
