package ecommerce_event_driven.inventory.modules.product.application.usecases;

import ecommerce_event_driven.inventory.modules.product.application.dtos.CategoryResponse;
import ecommerce_event_driven.inventory.modules.product.application.ports.outbound.repos.CategoryRepositoryPort;
import ecommerce_event_driven.inventory.modules.product.application.ports.outbound.repos.ProductRepositoryPort;
import ecommerce_event_driven.inventory.modules.product.domain.models.Category;
import ecommerce_event_driven.inventory.shared.web.NotFoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

/**
 * Leitura de categorias, cacheada no Redis por 1 h.
 *
 * <p>O cache fica aqui e nao no controller por dois motivos: a anotacao so funciona
 * chamada de outro bean, e o valor cacheado precisa ser um DTO serializavel, nao um
 * ResponseEntity. Categorias so mudam por migration, entao o TTL basta como invalidacao.
 */
@Service
public class CategoryQueryService {

    private final CategoryRepositoryPort categoryRepo;
    private final ProductRepositoryPort productRepo;

    public CategoryQueryService(CategoryRepositoryPort categoryRepo, ProductRepositoryPort productRepo) {
        this.categoryRepo = categoryRepo;
        this.productRepo = productRepo;
    }

    @Cacheable(value = "catalog:categories", key = "'all:' + #includeEmpty")
    public List<CategoryResponse> list(boolean includeEmpty) {
        // ArrayList explicito: e serializavel e nao depende da implementacao de toList().
        return new ArrayList<>(categoryRepo.findAll().stream()
                .map(this::toResponse)
                .filter(cat -> includeEmpty || cat.productCount() > 0)
                .toList());
    }

    @Cacheable(value = "catalog:categories", key = "'one:' + #idOrSlug")
    public CategoryResponse get(String idOrSlug) {
        return findByIdOrSlug(idOrSlug)
                .map(this::toResponse)
                .orElseThrow(() -> new NotFoundException("Categoria nao encontrada: " + idOrSlug));
    }

    private Optional<Category> findByIdOrSlug(String idOrSlug) {
        try {
            return categoryRepo.findById(Long.parseLong(idOrSlug));
        } catch (NumberFormatException e) {
            return categoryRepo.findBySlug(idOrSlug);
        }
    }

    private CategoryResponse toResponse(Category category) {
        return new CategoryResponse(
                category.id(),
                category.name(),
                category.slug(),
                productRepo.countActiveByCategory(category.id()));
    }
}
