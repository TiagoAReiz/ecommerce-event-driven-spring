package ecommerce_event_driven.inventory.modules.product.infra.inbound.controllers;

import ecommerce_event_driven.inventory.modules.product.application.dtos.CreateProductRequest;
import ecommerce_event_driven.inventory.modules.product.application.dtos.CreateProductRequest.CreatePhotoRequest;
import ecommerce_event_driven.inventory.modules.product.application.dtos.PatchStockRequest;
import ecommerce_event_driven.inventory.modules.product.application.dtos.ProductDetailResponse;
import ecommerce_event_driven.inventory.modules.product.application.dtos.ProductDetailResponse.ProductPhotoResponse;
import ecommerce_event_driven.inventory.modules.product.application.dtos.UpdateProductRequest;
import ecommerce_event_driven.inventory.modules.product.application.dtos.UploadUrlRequest;
import ecommerce_event_driven.inventory.modules.product.application.dtos.UploadUrlResponse;
import ecommerce_event_driven.inventory.modules.product.application.mappers.ProductMapper;
import ecommerce_event_driven.inventory.modules.product.application.ports.inbound.usecases.GenerateDraftUploadUrlUseCase;
import ecommerce_event_driven.inventory.modules.product.application.ports.inbound.usecases.GenerateUploadUrlUseCase;
import ecommerce_event_driven.inventory.modules.product.application.ports.outbound.storage.PhotoStoragePort;
import ecommerce_event_driven.inventory.modules.product.domain.models.Product;
import ecommerce_event_driven.inventory.modules.product.infra.outbound.repos.CategoryJpaRepository;
import ecommerce_event_driven.inventory.modules.product.infra.outbound.repos.ProductJpaRepository;
import ecommerce_event_driven.inventory.modules.product.infra.outbound.repos.ProductPhotoJpaRepository;
import ecommerce_event_driven.inventory.modules.product.infra.outbound.repos.entity.ProductEntity;
import ecommerce_event_driven.inventory.modules.product.infra.outbound.repos.entity.ProductPhotoEntity;
import ecommerce_event_driven.inventory.shared.web.ConflictException;
import ecommerce_event_driven.inventory.modules.product.application.dtos.CategoryResponse;
import ecommerce_event_driven.inventory.modules.product.application.mappers.CategoryMapper;
import ecommerce_event_driven.inventory.modules.product.application.usecases.AvailabilityService;
import ecommerce_event_driven.inventory.shared.web.PageMeta;
import ecommerce_event_driven.inventory.shared.web.PageResponse;
import ecommerce_event_driven.inventory.shared.web.NotFoundException;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.net.URI;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/products")
@PreAuthorize("hasAuthority('SCOPE_catalog:write')")
public class ProductManagementController {

    private final ProductJpaRepository productRepository;
    private final ProductPhotoJpaRepository photoRepository;
    private final CategoryJpaRepository categoryRepository;
    private final ecommerce_event_driven.inventory.modules.product.infra.outbound.repos.StockReservationJpaRepository reservationRepository;
    private final GenerateUploadUrlUseCase generateUploadUrlUseCase;
    private final GenerateDraftUploadUrlUseCase generateDraftUploadUrlUseCase;
    private final PhotoStoragePort photoStorage;
    private final AvailabilityService availabilityService;

    public ProductManagementController(
            ProductJpaRepository productRepository,
            ProductPhotoJpaRepository photoRepository,
            CategoryJpaRepository categoryRepository,
            ecommerce_event_driven.inventory.modules.product.infra.outbound.repos.StockReservationJpaRepository reservationRepository,
            GenerateUploadUrlUseCase generateUploadUrlUseCase,
            GenerateDraftUploadUrlUseCase generateDraftUploadUrlUseCase,
            PhotoStoragePort photoStorage,
            AvailabilityService availabilityService) {
        this.productRepository = productRepository;
        this.photoRepository = photoRepository;
        this.categoryRepository = categoryRepository;
        this.reservationRepository = reservationRepository;
        this.generateUploadUrlUseCase = generateUploadUrlUseCase;
        this.generateDraftUploadUrlUseCase = generateDraftUploadUrlUseCase;
        this.photoStorage = photoStorage;
        this.availabilityService = availabilityService;
    }

    // Task 5.1: GET /products/manage
    @org.springframework.web.bind.annotation.GetMapping("/manage")
    public ResponseEntity<PageResponse<ProductDetailResponse>> listManagement(
            @RequestParam(required = false, defaultValue = "active") String status,
            @RequestParam(required = false) String q,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {

        Page<ProductEntity> page;
        switch (status.toLowerCase()) {
            case "active" ->
                page = productRepository.findAll((root, query, cb) ->
                    cb.and(cb.isNull(root.get("deletedAt")), cb.greaterThanOrEqualTo(root.get("stock"), 0)),
                    pageable);
            case "out_of_stock" ->
                page = productRepository.findAll((root, query, cb) ->
                    cb.and(cb.isNull(root.get("deletedAt")), cb.equal(root.get("stock"), 0)),
                    pageable);
            case "deleted" ->
                page = productRepository.findAll((root, query, cb) -> cb.isNotNull(root.get("deletedAt")),
                    pageable);
            case "all" ->
                page = productRepository.findAll(pageable);
            default ->
                throw new IllegalArgumentException("Status inválido: " + status);
        }

        // O envelope do contrato e {content, page:{...}}; devolver o Page cru do
        // Spring muda o formato e a tela quebra procurando `page.number`.
        var itens = page.map(entity -> {
                Product product = ProductMapper.toDomain(entity);
                var photos = photoRepository.findByProductIdAndDeletedAtIsNullOrderByPositionAsc(product.id())
                    .stream()
                    .map(p -> new ProductPhotoResponse(p.getId(), p.getPhotoUrl(), p.getPosition()))
                    .toList();
                return new ProductDetailResponse(
                    product.id(),
                    product.name(),
                    product.description(),
                    product.price().toPlainString(),
                    product.stock(),
                    // Importa sim: a lista da loja mostra quanto esta livre para venda,
                    // que e o estoque menos o que ja esta preso em reserva.
                    availabilityService.getAvailability(product.id()),
                    product.rating() != null ? product.rating().toPlainString() : "0",
                    product.ratingCount() != null ? product.ratingCount() : 0,
                    // Sem a categoria, a tela da loja lia `category.name` de undefined
                    // e a pagina inteira quebrava.
                    categoryRepository.findById(product.idCategory())
                        .map(CategoryMapper::toDomain)
                        .map(c -> new CategoryResponse(c.id(), c.name(), c.slug(), 0L))
                        .orElse(null),
                    photos,
                    product.createdAt(),
                    product.updatedAt());
        });

        return ResponseEntity.ok(new PageResponse<>(
            itens.getContent(),
            new PageMeta(itens.getNumber(), itens.getSize(), itens.getTotalElements(), itens.getTotalPages())));
    }

    // Task 5.1: POST /products
    // A lista de categorias carrega productCount e, sem includeEmpty, so mostra
    // categoria que tem produto: escrever produto muda as duas coisas. Sem esta
    // limpeza a vitrine fica ate uma hora com a navegacao errada.
    @CacheEvict(value = "catalog:categories", allEntries = true)
    @PostMapping
    public ResponseEntity<ProductDetailResponse> createProduct(
            @Valid @RequestBody CreateProductRequest request) {

        // Validar categoria
        var categoryEntity = categoryRepository.findById(request.idCategory())
            .orElseThrow(() -> new NotFoundException("Categoria não encontrada"));

        // Criar produto
        var product = Product.builder()
            .idCategory(request.idCategory())
            .name(request.name())
            .description(request.description())
            .price(new BigDecimal(request.price()))
            .stock(request.stock() != null ? request.stock() : 0)
            .rating(BigDecimal.ZERO)
            .ratingCount(0)
            .build();

        ProductEntity entity = ProductMapper.toEntity(product);
        var saved = productRepository.save(entity);

        // Salvar fotos se houver
        if (request.photos() != null) {
            for (CreatePhotoRequest photoReq : request.photos()) {
                validatePhotoUrl(photoReq.photoUrl());
                // Foto enviada antes do produto existir fica em rascunho/; agora que o
                // id nasceu, promove para a pasta do produto. Url que nao for do nosso
                // bucket (link externo colado pelo dono) volta intacta. Falha na
                // movimentacao nao derruba a criacao - a porta ja loga e devolve a
                // propria url de rascunho, que continua acessivel.
                String finalPhotoUrl = photoStorage.promoteDraft(photoReq.photoUrl(), saved.getId());
                var photoEntity = ProductPhotoEntity.builder()
                    .product(saved)
                    .photoUrl(finalPhotoUrl)
                    .position(photoReq.position().shortValue())
                    .build();
                photoRepository.save(photoEntity);
            }
        }

        var result = ProductMapper.toDomain(saved);
        var photos = photoRepository.findByProductIdAndDeletedAtIsNullOrderByPositionAsc(result.id())
            .stream()
            .map(p -> new ProductPhotoResponse(p.getId(), p.getPhotoUrl(), p.getPosition()))
            .toList();

        var response = new ProductDetailResponse(
            result.id(),
            result.name(),
            result.description(),
            result.price().toPlainString(),
            result.stock(),
            result.stock(),
            result.rating() != null ? result.rating().toPlainString() : "0",
            result.ratingCount() != null ? result.ratingCount() : 0,
            null,
            photos,
            result.createdAt(),
            result.updatedAt());

        return ResponseEntity
            .created(URI.create("/products/" + result.id()))
            .body(response);
    }

    // Task 5.1: PUT /products/{id}
    // A lista de categorias carrega productCount e, sem includeEmpty, so mostra
    // categoria que tem produto: escrever produto muda as duas coisas. Sem esta
    // limpeza a vitrine fica ate uma hora com a navegacao errada.
    @CacheEvict(value = "catalog:categories", allEntries = true)
    @PutMapping("/{id}")
    public ResponseEntity<ProductDetailResponse> updateProduct(
            @PathVariable Long id,
            @Valid @RequestBody UpdateProductRequest request) {

        var productEntity = productRepository.findById(id)
            .filter(p -> p.getDeletedAt() == null)
            .orElseThrow(() -> new NotFoundException("Produto não encontrado"));

        // Validar categoria se fornecida
        if (request.idCategory() != null) {
            categoryRepository.findById(request.idCategory())
                .orElseThrow(() -> new NotFoundException("Categoria não encontrada"));
            productEntity.setCategory(
                categoryRepository.findById(request.idCategory()).orElse(null));
        }

        if (request.name() != null) productEntity.setName(request.name());
        if (request.description() != null) productEntity.setDescription(request.description());
        if (request.price() != null) productEntity.setPrice(new BigDecimal(request.price()));

        var saved = productRepository.save(productEntity);
        var result = ProductMapper.toDomain(saved);

        var photos = photoRepository.findByProductIdAndDeletedAtIsNullOrderByPositionAsc(result.id())
            .stream()
            .map(p -> new ProductPhotoResponse(p.getId(), p.getPhotoUrl(), p.getPosition()))
            .toList();

        var response = new ProductDetailResponse(
            result.id(),
            result.name(),
            result.description(),
            result.price().toPlainString(),
            result.stock(),
            result.stock(),
            result.rating() != null ? result.rating().toPlainString() : "0",
            result.ratingCount() != null ? result.ratingCount() : 0,
            null,
            photos,
            result.createdAt(),
            result.updatedAt());

        return ResponseEntity.ok(response);
    }

    // Task 5.1: PATCH /products/{id}
    @PatchMapping("/{id}")
    public ResponseEntity<ProductDetailResponse> patchProduct(
            @PathVariable Long id,
            @RequestBody UpdateProductRequest request) {
        return updateProduct(id, request);
    }

    // Task 5.1: DELETE /products/{id}
    // A lista de categorias carrega productCount e, sem includeEmpty, so mostra
    // categoria que tem produto: escrever produto muda as duas coisas. Sem esta
    // limpeza a vitrine fica ate uma hora com a navegacao errada.
    @CacheEvict(value = "catalog:categories", allEntries = true)
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProduct(@PathVariable Long id) {
        var productEntity = productRepository.findById(id)
            .filter(p -> p.getDeletedAt() == null)
            .orElseThrow(() -> new NotFoundException("Produto não encontrado"));

        // Produto com reserva ativa tem pedido em curso: remover agora deixaria esse pedido
        // sem ter como baixar o estoque. O pedido precisa fechar ou cair antes.
        int held = reservationRepository.sumActiveQuantity(
                id,
                ecommerce_event_driven.inventory.modules.product.domain.models.ReservationStatus.held,
                java.time.Instant.now());
        if (held > 0) {
            throw new ConflictException("Produto tem reserva ativa de pedido em curso");
        }

        productEntity.setDeletedAt(java.time.Instant.now());
        productRepository.save(productEntity);

        return ResponseEntity.noContent().build();
    }

    // Task 5.2: PATCH /products/{id}/stock
    @PatchMapping("/{id}/stock")
    public ResponseEntity<ProductDetailResponse> patchStock(
            @PathVariable Long id,
            @RequestBody PatchStockRequest request) {

        if ((request.stock() == null && request.delta() == null)
            || (request.stock() != null && request.delta() != null)) {
            throw new IllegalArgumentException("Forneça stock ou delta, mas não ambos");
        }

        var productEntity = productRepository.findById(id)
            .filter(p -> p.getDeletedAt() == null)
            .orElseThrow(() -> new NotFoundException("Produto não encontrado"));

        int newStock;
        if (request.stock() != null) {
            newStock = request.stock();
        } else {
            newStock = productEntity.getStock() + request.delta();
        }

        if (newStock < 0) {
            throw new ConflictException("Estoque não pode ser negativo");
        }

        productEntity.setStock(newStock);
        var saved = productRepository.save(productEntity);
        var result = ProductMapper.toDomain(saved);

        var photos = photoRepository.findByProductIdAndDeletedAtIsNullOrderByPositionAsc(result.id())
            .stream()
            .map(p -> new ProductPhotoResponse(p.getId(), p.getPhotoUrl(), p.getPosition()))
            .toList();

        var response = new ProductDetailResponse(
            result.id(),
            result.name(),
            result.description(),
            result.price().toPlainString(),
            result.stock(),
            result.stock(),
            result.rating() != null ? result.rating().toPlainString() : "0",
            result.ratingCount() != null ? result.ratingCount() : 0,
            null,
            photos,
            result.createdAt(),
            result.updatedAt());

        return ResponseEntity.ok(response);
    }

    // Task 5.2: POST /products/{id}/photos
    @PostMapping("/{id}/photos")
    public ResponseEntity<ProductPhotoResponse> addPhoto(
            @PathVariable Long id,
            @RequestBody CreatePhotoRequest request) {

        var productEntity = productRepository.findById(id)
            .filter(p -> p.getDeletedAt() == null)
            .orElseThrow(() -> new NotFoundException("Produto não encontrado"));

        // Verificar limite de 10 fotos
        long photoCount = photoRepository.findByProductIdAndDeletedAtIsNullOrderByPositionAsc(id)
            .size();
        if (photoCount >= 10) {
            throw new ConflictException("Limite de 10 fotos atingido");
        }

        validatePhotoUrl(request.photoUrl());

        var photoEntity = ProductPhotoEntity.builder()
            .product(productEntity)
            .photoUrl(request.photoUrl())
            .position(request.position().shortValue())
            .build();

        var saved = photoRepository.save(photoEntity);
        return ResponseEntity.status(201)
            .body(new ProductPhotoResponse(
                saved.getId(),
                saved.getPhotoUrl(),
                saved.getPosition()));
    }

    // POST /products/photos/upload-url
    // Mesmo desenho da rota com id, para a tela de CRIACAO de produto: o id so
    // nasce no INSERT, entao ainda nao da para usar /{id}/photos/upload-url.
    // Segmento literal "photos" tem prioridade sobre a variavel {id} no
    // roteamento do Spring (PathPattern compara por especificidade: menos
    // variaveis ganha), entao esta rota nao e capturada por /{id}/... e
    // POST /products/4/photos/upload-url continua batendo na outra.
    // Chave fica em rascunho/{uuid}.{extensao}; POST /products promove para a
    // pasta do produto quando o id passa a existir.
    @PostMapping("/photos/upload-url")
    public ResponseEntity<UploadUrlResponse> generateDraftUploadUrl(
            @Valid @RequestBody UploadUrlRequest request) {
        return ResponseEntity.ok(generateDraftUploadUrlUseCase.execute(request));
    }

    // Task 5.2: POST /products/{id}/photos/upload-url
    // Gera a url assinada de PUT: o navegador sobe a foto direto no S3 (MinIO),
    // sem passar pelo backend. O front registra a foto depois com a publicUrl
    // devolvida aqui, em POST /products/{id}/photos.
    @PostMapping("/{id}/photos/upload-url")
    public ResponseEntity<UploadUrlResponse> generateUploadUrl(
            @PathVariable Long id,
            @Valid @RequestBody UploadUrlRequest request) {
        return ResponseEntity.ok(generateUploadUrlUseCase.execute(id, request));
    }

    /** Corpo de PUT /products/{id}/photos/order. */
    public record PhotoOrderRequest(java.util.List<Item> order) {
        public record Item(Long id, Short position) {
        }
    }

    // Task 5.2: PUT /products/{id}/photos/order
    @org.springframework.web.bind.annotation.PutMapping("/{id}/photos/order")
    public java.util.List<ProductPhotoResponse> reorderPhotos(
            @PathVariable Long id,
            @RequestBody PhotoOrderRequest request) {
        productRepository.findById(id)
            .filter(p -> p.getDeletedAt() == null)
            .orElseThrow(() -> new NotFoundException("Produto não encontrado"));

        if (request == null || request.order() == null || request.order().isEmpty()) {
            throw new ecommerce_event_driven.inventory.shared.web.BadRequestException("order vazio");
        }
        long distinctPositions = request.order().stream().map(PhotoOrderRequest.Item::position).distinct().count();
        if (distinctPositions != request.order().size()) {
            throw new ecommerce_event_driven.inventory.shared.web.BadRequestException("position repetida");
        }

        var photos = photoRepository.findByProductIdAndDeletedAtIsNullOrderByPositionAsc(id);
        var byId = new java.util.HashMap<Long, ProductPhotoEntity>();
        photos.forEach(p -> byId.put(p.getId(), p));

        for (var item : request.order()) {
            if (!byId.containsKey(item.id())) {
                throw new NotFoundException("Foto " + item.id() + " não pertence ao produto");
            }
        }
        // Reordenar parcialmente deixaria posicoes duplicadas entre as fotos nao citadas.
        if (request.order().size() != photos.size()) {
            throw new ecommerce_event_driven.inventory.shared.web.UnprocessableException(
                    "a lista precisa cobrir todas as fotos do produto");
        }

        for (var item : request.order()) {
            byId.get(item.id()).setPosition(item.position());
        }
        photoRepository.saveAll(photos);

        return photoRepository.findByProductIdAndDeletedAtIsNullOrderByPositionAsc(id).stream()
            .map(p -> new ProductPhotoResponse(p.getId(), p.getPhotoUrl(), p.getPosition()))
            .toList();
    }

    // Task 5.2: DELETE /products/{id}/photos/{photoId}
    @DeleteMapping("/{id}/photos/{photoId}")
    public ResponseEntity<Void> deletePhoto(@PathVariable Long id, @PathVariable Long photoId) {
        var product = productRepository.findById(id)
            .filter(p -> p.getDeletedAt() == null)
            .orElseThrow(() -> new NotFoundException("Produto não encontrado"));

        var photo = photoRepository.findByIdAndDeletedAtIsNull(photoId)
            .filter(p -> p.getProduct() != null && p.getProduct().getId().equals(product.getId()))
            .orElseThrow(() -> new NotFoundException("Foto não encontrada"));

        // Produto ativo sem nenhuma foto quebra a vitrine.
        if (photoRepository.findByProductIdAndDeletedAtIsNullOrderByPositionAsc(id).size() == 1) {
            throw new ConflictException("Não é possível remover a única foto de um produto ativo");
        }

        photo.setDeletedAt(java.time.Instant.now());
        photoRepository.save(photo);

        // Falha ao apagar o objeto no S3 nao pode derrubar a remocao da foto:
        // o registro no banco e a verdade. A porta ja loga e segue sozinha.
        photoStorage.deleteIfOwned(photo.getPhotoUrl());

        return ResponseEntity.noContent().build();
    }

    private void validatePhotoUrl(String photoUrl) {
        if (photoUrl == null) {
            throw new ecommerce_event_driven.inventory.shared.web.UnprocessableException(
                    "photoUrl deve ser uma URL absoluta https");
        }
        // Foto do nosso proprio armazenamento passa mesmo em http: em ambiente
        // local o MinIO serve por http, e foi esta rota que devolveu a URL.
        if (photoUrl.startsWith(photoStorage.publicPrefix())) {
            return;
        }
        if (!photoUrl.startsWith("https://")) {
            throw new ecommerce_event_driven.inventory.shared.web.UnprocessableException(
                    "photoUrl deve ser uma URL absoluta https");
        }
    }
}
