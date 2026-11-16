package com.ensias.secureorders.payment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record PaymentRequest(@NotBlank String orderId, @Positive double amount) {
}
