package com.ensias.secureorders.inventory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/inventory")
public class InventoryController {

    private final Map<String, Product> stock = new LinkedHashMap<>();

    public InventoryController() {
        stock.put("P-100", new Product("P-100", "Laptop", 10, 5000.0));
        stock.put("P-200", new Product("P-200", "Souris", 50, 150.0));
        stock.put("P-300", new Product("P-300", "Clavier", 25, 400.0));
    }

    @GetMapping
    public synchronized List<Product> all() {
        return new ArrayList<>(stock.values());
    }

    @GetMapping("/{productId}")
    public synchronized Product get(@PathVariable String productId) {
        Product p = stock.get(productId);
        if (p == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "unknown product " + productId);
        }
        return p;
    }
    @PreAuthorize("hasRole('SVC_ORDER')")
    @PostMapping("/reserve")
    public synchronized Reservation reserve(@Valid @RequestBody ReserveRequest req) {
        Product p = get(req.productId());
        if (p.stock() < req.quantity()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "insufficient stock for " + p.productId() + " (available: " + p.stock() + ")");
        }
        stock.put(p.productId(), new Product(p.productId(), p.name(), p.stock() - req.quantity(), p.unitPrice()));
        return new Reservation(p.productId(), req.quantity(), p.unitPrice());
    }
}
