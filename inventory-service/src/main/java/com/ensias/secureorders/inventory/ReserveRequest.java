package com.ensias.secureorders.inventory;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record ReserveRequest(@NotBlank String productId, @Min(1) int quantity) {
}
