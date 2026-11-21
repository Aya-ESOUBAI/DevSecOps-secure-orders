package com.ensias.secureorders.payment;

import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/payments")
public class PaymentController {

    private static final double LIMIT = 100_000.0;
    @PreAuthorize("hasRole('SVC_ORDER')")
    @PostMapping
    public PaymentResult pay(@Valid @RequestBody PaymentRequest req) {
        String status = req.amount() <= LIMIT ? "APPROVED" : "DECLINED";
        return new PaymentResult(UUID.randomUUID().toString(), status);
    }
}
