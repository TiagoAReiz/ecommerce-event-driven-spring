package ecommerce_event_driven.inventory.modules.product.application.dtos;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.List;

public record CreateProductRequest(
        @NotBlank(message = "name é obrigatório")
        String name,

        String description,

        @NotNull(message = "idCategory é obrigatório")
        Long idCategory,

        @NotNull(message = "price é obrigatório")
        String price,

        @PositiveOrZero(message = "stock não pode ser negativo")
        Integer stock,

        @Valid
        List<CreatePhotoRequest> photos) {

    public record CreatePhotoRequest(
            @NotBlank(message = "photoUrl é obrigatório")
            String photoUrl,

            @NotNull(message = "position é obrigatório")
            Integer position) {
    }
}
