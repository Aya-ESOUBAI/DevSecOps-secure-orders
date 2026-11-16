package com.ensias.secureorders.order;

import java.time.Instant;

public record Order(String id, String productId, int quantity, double total,
                    String status, Instant createdAt) {
}
