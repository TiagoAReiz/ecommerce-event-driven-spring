package ecommerce_event_driven.inventory.modules.product.application.usecases;

import ecommerce_event_driven.inventory.modules.product.application.dtos.ProductSearchResponse;
import ecommerce_event_driven.inventory.modules.product.application.dtos.ProductSearchResponse.FacetsResponse;
import ecommerce_event_driven.inventory.modules.product.application.dtos.ProductSearchResponse.FacetsResponse.CategoryFacet;
import ecommerce_event_driven.inventory.modules.product.application.dtos.ProductSearchResponse.FacetsResponse.PriceRangeFacet;
import ecommerce_event_driven.inventory.modules.product.application.dtos.ProductSearchResponse.ProductSearchItem;
import ecommerce_event_driven.inventory.modules.product.application.mappers.CategoryMapper;
import ecommerce_event_driven.inventory.modules.product.application.mappers.ProductMapper;
import ecommerce_event_driven.inventory.modules.product.domain.models.Product;
import ecommerce_event_driven.inventory.modules.product.domain.models.Category;
import ecommerce_event_driven.inventory.modules.product.infra.outbound.repos.CategoryJpaRepository;
import ecommerce_event_driven.inventory.modules.product.infra.outbound.repos.ProductJpaRepository;
import ecommerce_event_driven.inventory.modules.product.infra.outbound.repos.ProductPhotoJpaRepository;
import ecommerce_event_driven.inventory.modules.product.infra.outbound.repos.entity.ProductEntity;
import ecommerce_event_driven.inventory.shared.web.PageMeta;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

@Service
public class ProductSearchService {

    private final ProductJpaRepository productRepository;
    private final CategoryJpaRepository categoryRepository;
    private final AvailabilityService availabilityService;
    private final ProductPhotoJpaRepository photoRepository;

    public ProductSearchService(
            ProductJpaRepository productRepository,
            CategoryJpaRepository categoryRepository,
            AvailabilityService availabilityService,
            ProductPhotoJpaRepository photoRepository) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.availabilityService = availabilityService;
        this.photoRepository = photoRepository;
    }

    public ProductSearchResponse search(
            String q,
            List<Long> categoryIds,
            String categorySlug,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            BigDecimal minRating,
            Boolean inStock,
            Pageable pageable) {

        Specification<ProductEntity> spec = buildSpecification(
                q, categoryIds, categorySlug, minPrice, maxPrice, minRating, inStock);

        Page<ProductEntity> page = productRepository.findAll(spec, pageable);

        // Monta items com disponibilidade calculada
        var ids = page.getContent().stream().map(e -> e.getId()).toList();
        var availabilities = availabilityService.getAvailabilities(ids);

        // Foto de capa de todos os itens numa consulta so: uma por produto seria
        // N+1 na rota mais chamada do sistema.
        Map<Long, String> capas = ids.isEmpty()
                ? Map.of()
                : photoRepository.findByProductIdInAndDeletedAtIsNullOrderByProductIdAscPositionAsc(ids).stream()
                        .collect(java.util.stream.Collectors.toMap(
                                // Ler so o id do proxy LAZY nao dispara carga do produto.
                                photo -> photo.getProduct().getId(),
                                photo -> photo.getPhotoUrl(),
                                (primeira, seguinte) -> primeira));

        List<ProductSearchItem> items = page.getContent().stream()
                .map(entity -> {
                    Product product = ProductMapper.toDomain(entity);
                    var category = categoryRepository.findById(product.idCategory())
                            .map(CategoryMapper::toDomain);
                    int available = availabilities.getOrDefault(product.id(), 0);
                    String photoUrl = capas.get(product.id());
                    return new ProductSearchItem(
                            product.id(),
                            product.name(),
                            product.price().toPlainString(),
                            product.rating() != null ? product.rating().toPlainString() : "0",
                            product.ratingCount() != null ? product.ratingCount() : 0,
                            available,
                            photoUrl,
                            category.map(c -> new ecommerce_event_driven.inventory.modules.product.application.dtos.CategoryResponse(
                                    c.id(), c.name(), c.slug(), 0L))
                                    .orElse(null));
                })
                .toList();

        var pageMeta = new PageMeta(
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());

        var facets = buildFacets(page.getContent(), minPrice, maxPrice);

        return new ProductSearchResponse(items, pageMeta, facets);
    }

    private Specification<ProductEntity> buildSpecification(
            String q,
            List<Long> categoryIds,
            String categorySlug,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            BigDecimal minRating,
            Boolean inStock) {

        return Specification
                .where(hasDeletedAtNull())
                .and(textSearch(q))
                .and(categoryIdIn(categoryIds))
                .and(categorySlugEquals(categorySlug))
                .and(priceBetween(minPrice, maxPrice))
                .and(minRatingGreaterThan(minRating))
                .and(inStockFilter(inStock, availabilityService));
    }

    private Specification<ProductEntity> hasDeletedAtNull() {
        return (root, query, cb) -> cb.isNull(root.get("deletedAt"));
    }

    private Specification<ProductEntity> textSearch(String q) {
        return (root, query, cb) -> {
            if (q == null || q.isBlank()) {
                return cb.conjunction();
            }
            String pattern = "%" + q.toLowerCase() + "%";
            return cb.or(
                    cb.like(cb.lower(root.get("name")), pattern),
                    cb.like(cb.lower(root.get("description")), pattern));
        };
    }

    private Specification<ProductEntity> categoryIdIn(List<Long> categoryIds) {
        return (root, query, cb) -> {
            if (categoryIds == null || categoryIds.isEmpty()) {
                return cb.conjunction();
            }
            // A entidade tem a associacao `category`, nao uma coluna `categoryId`:
            // pelo nome errado o Hibernate nao resolve o atributo e a rota devolve 500.
            return root.get("category").get("id").in(categoryIds);
        };
    }

    private Specification<ProductEntity> categorySlugEquals(String categorySlug) {
        return (root, query, cb) -> {
            if (categorySlug == null || categorySlug.isBlank()) {
                return cb.conjunction();
            }
            return cb.equal(root.get("category").get("slug"), categorySlug);
        };
    }

    private Specification<ProductEntity> priceBetween(
            BigDecimal minPrice, BigDecimal maxPrice) {
        return (root, query, cb) -> {
            if (minPrice == null && maxPrice == null) {
                return cb.conjunction();
            }
            if (minPrice != null && maxPrice != null) {
                return cb.between(root.get("price"), minPrice, maxPrice);
            }
            if (minPrice != null) {
                return cb.greaterThanOrEqualTo(root.get("price"), minPrice);
            }
            return cb.lessThanOrEqualTo(root.get("price"), maxPrice);
        };
    }

    private Specification<ProductEntity> minRatingGreaterThan(BigDecimal minRating) {
        return (root, query, cb) -> {
            if (minRating == null) {
                return cb.conjunction();
            }
            return cb.greaterThanOrEqualTo(root.get("rating"), minRating);
        };
    }

    private Specification<ProductEntity> inStockFilter(
            Boolean inStock, AvailabilityService availabilityService) {
        return (root, query, cb) -> {
            if (inStock == null || !inStock) {
                return cb.conjunction();
            }
            // Filtro simplificado: apenas produtos com stock > 0
            // A disponibilidade exata será calculada depois
            return cb.greaterThan(root.get("stock"), 0);
        };
    }

    private FacetsResponse buildFacets(
            List<ProductEntity> products,
            BigDecimal minPrice,
            BigDecimal maxPrice) {

        // Facetas de categoria
        Map<Long, Long> categoryCounts = products.stream()
                .collect(Collectors.groupingByConcurrent(
                        p -> p.getCategory().getId(), Collectors.counting()));

        List<CategoryFacet> categoryFacets = categoryCounts.entrySet().stream()
                .map(entry -> {
                    var cat = categoryRepository.findById(entry.getKey());
                    return cat
                            .map(c -> new CategoryFacet(c.getId(), c.getName(), entry.getValue()))
                            .orElse(null);
                })
                .filter(f -> f != null)
                .sorted(Comparator.comparingLong(CategoryFacet::count).reversed())
                .toList();

        // Facetas de faixa de preco
        BigDecimal min = products.stream()
                .map(p -> p.getPrice())
                .min(Comparator.naturalOrder())
                .orElse(BigDecimal.ZERO);
        BigDecimal max = products.stream()
                .map(p -> p.getPrice())
                .max(Comparator.naturalOrder())
                .orElse(BigDecimal.ZERO);

        var priceRange = new PriceRangeFacet(
                min.toPlainString(),
                max.toPlainString());

        return new FacetsResponse(categoryFacets, priceRange);
    }
}
