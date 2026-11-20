package com.ensias.secureorders.order;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.client.ClientHttpRequestInterceptor;

/**
 * Orchestre la requete client : order -> inventory (reserve) -> payment (pay).
 * Les appels inter-services passent par RestClient. C'est ICI que l'on ajoutera
 * le token (client credentials) de service a service.
 */
@Service
public class OrderService {

    record ReserveRequest(String productId, int quantity) {}
    record Reservation(String productId, int quantity, double unitPrice) {}
    record PaymentRequest(String orderId, double amount) {}
    record PaymentResult(String paymentId, String status) {}

    private final RestClient inventory;
    private final RestClient payment;
    private final Map<String, Order> orders = new ConcurrentHashMap<>();

    public OrderService(@Value("${services.inventory.url}") String inventoryUrl,
                        @Value("${services.payment.url}") String paymentUrl,
                        ClientHttpRequestInterceptor bearerInterceptor) {
        this.inventory = RestClient.builder().baseUrl(inventoryUrl)
                .requestInterceptor(bearerInterceptor).build();
        this.payment = RestClient.builder().baseUrl(paymentUrl)
                .requestInterceptor(bearerInterceptor).build();
    }

    public Order create(OrderRequest req) {
        Reservation reservation;
        try {
            reservation = inventory.post()
                    .uri("/inventory/reserve")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new ReserveRequest(req.productId(), req.quantity()))
                    .retrieve()
                    .body(Reservation.class);
        } catch (HttpClientErrorException e) {
            throw new ResponseStatusException(e.getStatusCode(),
                    "inventory-service: " + e.getResponseBodyAsString());
        }

        String orderId = UUID.randomUUID().toString();
        double total = reservation.unitPrice() * reservation.quantity();

        PaymentResult result;
        try {
            result = payment.post()
                    .uri("/payments")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new PaymentRequest(orderId, total))
                    .retrieve()
                    .body(PaymentResult.class);
        } catch (HttpClientErrorException e) {
            throw new ResponseStatusException(e.getStatusCode(),
                    "payment-service: " + e.getResponseBodyAsString());
        }

        String status = "APPROVED".equals(result.status()) ? "CONFIRMED" : "PAYMENT_FAILED";
        Order order = new Order(orderId, req.productId(), req.quantity(), total, status, Instant.now());
        orders.put(orderId, order);
        return order;
    }

    public List<Order> findAll() {
        return new ArrayList<>(orders.values());
    }

    public Order findById(String id) {
        Order order = orders.get(id);
        if (order == null) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "order not found");
        }
        return order;
    }
}
