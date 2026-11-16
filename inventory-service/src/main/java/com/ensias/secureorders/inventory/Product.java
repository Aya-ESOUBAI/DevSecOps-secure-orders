package com.ensias.secureorders.inventory;

public record Product(String productId, String name, int stock, double unitPrice) {
}
