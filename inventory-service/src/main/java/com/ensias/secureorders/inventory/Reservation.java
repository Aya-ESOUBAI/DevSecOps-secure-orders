package com.ensias.secureorders.inventory;

public record Reservation(String productId, int quantity, double unitPrice) {
}
