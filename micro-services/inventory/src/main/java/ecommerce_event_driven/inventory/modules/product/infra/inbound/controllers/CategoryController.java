package ecommerce_event_driven.inventory.modules.product.infra.inbound.controllers;

import ecommerce_event_driven.inventory.modules.product.application.dtos.CategoryResponse;
import ecommerce_event_driven.inventory.modules.product.application.usecases.CategoryQueryService;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Categorias sao so leitura: nascem por migration (loja sem admin). */
@RestController
@RequestMapping("/categories")
public class CategoryController {

    private final CategoryQueryService categories;

    public CategoryController(CategoryQueryService categories) {
        this.categories = categories;
    }

    /** Envelope {content: [...]} do contrato, para a lista poder ganhar metadados sem quebrar o front. */
    @GetMapping
    public Map<String, List<CategoryResponse>> list(@RequestParam(defaultValue = "false") boolean includeEmpty) {
        return Map.of("content", categories.list(includeEmpty));
    }

    @GetMapping("/{idOrSlug}")
    public CategoryResponse get(@PathVariable String idOrSlug) {
        return categories.get(idOrSlug);
    }
}
