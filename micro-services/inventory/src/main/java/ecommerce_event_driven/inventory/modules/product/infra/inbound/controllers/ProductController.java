package ecommerce_event_driven.inventory.modules.product.infra.inbound.controllers;

import ecommerce_event_driven.inventory.modules.product.application.dtos.AvailabilityResponse;
import ecommerce_event_driven.inventory.modules.product.application.dtos.ProductDetailResponse;
import ecommerce_event_driven.inventory.modules.product.application.dtos.ProductDetailResponse.ProductPhotoResponse;
import ecommerce_event_driven.inventory.modules.product.application.dtos.ProductSearchResponse;
import ecommerce_event_driven.inventory.modules.product.application.mappers.CategoryMapper;
import ecommerce_event_driven.inventory.modules.product.application.mappers.ProductMapper;
import ecommerce_event_driven.inventory.modules.product.application.usecases.AvailabilityService;
import ecommerce_event_driven.inventory.modules.product.application.usecases.ProductSearchService;
import ecommerce_event_driven.inventory.modules.product.domain.models.Product;
import ecommerce_event_driven.inventory.modules.product.infra.outbound.repos.ProductJpaRepository;
import ecommerce_event_driven.inventory.modules.product.infra.outbound.repos.ProductPhotoJpaRepository;
import ecommerce_event_driven.inventory.shared.web.NotFoundException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/products")
public class ProductController {

    private final ProductSearchService searchService;
    private final AvailabilityService availabilityService;
    private final ProductJpaRepository productRepository;
    private final ProductPhotoJpaRepository photoRepository;

    public ProductController(
            ProductSearchService searchService,
            AvailabilityService availabilityService,
            ProductJpaRepository productRepository,
            ProductPhotoJpaRepository photoRepository) {
        this.searchService = searchService;
        this.availabilityService = availabilityService;
        this.productRepository = productRepository;
        this.photoRepository = photoRepository;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('SCOPE_catalog:read')")
    public ResponseEntity<ProductSearchResponse> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) List<Long> categoryId,
            @RequestParam(required = false) String categorySlug,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) BigDecimal minRating,
            @RequestParam(required = false) Boolean inStock,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {

        // Validacoes
        if (q != null && !q.isBlank() && q.length() < 2) {
            throw new IllegalArgumentException("q deve ter pelo menos 2 caracteres");
        }
        if (minPrice != null && maxPrice != null && minPrice.compareTo(maxPrice) > 0) {
            throw new IllegalArgumentException("minPrice nao pode ser maior que maxPrice");
        }
        if (pageable.getPageSize() > 100) {
            throw new IllegalArgumentException("size nao pode ser maior que 100");
        }

        var response = searchService.search(q, categoryId, categorySlug, minPrice, maxPrice, minRating, inStock, pageable);

        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(60, java.util.concurrent.TimeUnit.SECONDS).cachePublic())
                .body(response);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('SCOPE_catalog:read')")
    public ResponseEntity<ProductDetailResponse> getProductDetail(@PathVariable Long id) {
        var productEntity = productRepository
                .findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new NotFoundException("Produto nao encontrado"));

        Product product = ProductMapper.toDomain(productEntity);

        // Disponibilidade
        int available = availabilityService.getAvailability(product.id());

        // Fotos
        var photos = photoRepository.findByProductIdAndDeletedAtIsNullOrderByPositionAsc(product.id()).stream()
                .map(p -> new ProductPhotoResponse(p.getId(), p.getPhotoUrl(), p.getPosition()))
                .toList();

        var response = new ProductDetailResponse(
                product.id(),
                product.name(),
                product.description(),
                product.price().toPlainString(),
                product.stock(),
                available,
                product.rating() != null ? product.rating().toPlainString() : "0",
                product.ratingCount() != null ? product.ratingCount() : 0,
                null, // category - implementar depois
                photos,
                product.createdAt(),
                product.updatedAt());

        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(10, java.util.concurrent.TimeUnit.MINUTES).cachePublic())
                .body(response);
    }

    @GetMapping("/{id}/availability")
    @PreAuthorize("hasAuthority('SCOPE_catalog:read')")
    public ResponseEntity<AvailabilityResponse> getAvailability(@PathVariable Long id) {
        var productEntity = productRepository
                .findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new NotFoundException("Produto nao encontrado"));

        Product product = ProductMapper.toDomain(productEntity);
        var availability = availabilityService.getDetailedAvailability(product.id());

        var response = new AvailabilityResponse(
                product.id(),
                product.stock(),
                availability.held(),
                availability.available(),
                Instant.now());

        // Nunca cachear disponibilidade
        return ResponseEntity.ok().body(response);
    }

    @GetMapping("/{id}/photos")
    @PreAuthorize("hasAuthority('SCOPE_catalog:read')")
    public ResponseEntity<List<ProductPhotoResponse>> getPhotos(@PathVariable Long id) {
        var productEntity = productRepository
                .findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new NotFoundException("Produto nao encontrado"));

        var photos = photoRepository.findByProductIdAndDeletedAtIsNullOrderByPositionAsc(id).stream()
                .map(p -> new ProductPhotoResponse(p.getId(), p.getPhotoUrl(), p.getPosition()))
                .toList();

        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(10, java.util.concurrent.TimeUnit.MINUTES).cachePublic())
                .body(photos);
    }
}
