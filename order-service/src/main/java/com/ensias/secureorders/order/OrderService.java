package com.ensias.secureorders.order;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

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
    record Product(
            String productId,
            String name,
            int stock,
            double unitPrice
    ) {}
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

        // 1. Consulter le produit sans réserver le stock
        Product product;
        try {
            product = inventory.get()
                    .uri("/inventory/{id}", req.productId())
                    .retrieve()
                    .body(Product.class);
        } catch (HttpClientErrorException e) {
            throw new ResponseStatusException(
                    e.getStatusCode(),
                    "inventory-service: " + e.getResponseBodyAsString()
            );
        }

        if (product == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Empty response from inventory-service"
            );
        }

        // 2. Calculer le montant
        double total = product.unitPrice() * req.quantity();

        // 3. Vérifier ABAC AVANT de réserver le stock
        if (total > 10000 && !isAdmin()) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Only ADMIN can create orders above 10000"
            );
        }

        // 4. Réserver le stock
        Reservation reservation;
        try {
            reservation = inventory.post()
                    .uri("/inventory/reserve")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new ReserveRequest(
                            req.productId(),
                            req.quantity()
                    ))
                    .retrieve()
                    .body(Reservation.class);
        } catch (HttpClientErrorException e) {
            throw new ResponseStatusException(
                    e.getStatusCode(),
                    "inventory-service: " + e.getResponseBodyAsString()
            );
        }

        if (reservation == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Empty reservation response"
            );
        }

        // Utiliser le montant réel retourné par la réservation
        double reservedTotal =
                reservation.unitPrice() * reservation.quantity();

        // Protection supplémentaire si le prix change
        if (reservedTotal > 10000 && !isAdmin()) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Only ADMIN can create orders above 10000"
            );
        }

        String orderId = UUID.randomUUID().toString();

        // 5. Effectuer le paiement
        PaymentResult result;
        try {
            result = payment.post()
                    .uri("/payments")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new PaymentRequest(orderId, reservedTotal))
                    .retrieve()
                    .body(PaymentResult.class);
        } catch (HttpClientErrorException e) {
            throw new ResponseStatusException(
                    e.getStatusCode(),
                    "payment-service: " + e.getResponseBodyAsString()
            );
        }

        if (result == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Empty response from payment-service"
            );
        }

        // 6. Enregistrer la commande avec son propriétaire
        String status = "APPROVED".equals(result.status())
                ? "CONFIRMED"
                : "PAYMENT_FAILED";

        Order order = new Order(
                orderId,
                req.productId(),
                req.quantity(),
                reservedTotal,
                status,
                Instant.now(),
                currentUsername()
        );

        orders.put(orderId, order);

        return order;
    }


    public List<Order> findAll() {
        if (isAdmin()) {
            return new ArrayList<>(orders.values());
        }

        String username = currentUsername();

        return orders.values().stream()
                .filter(order -> username.equals(order.ownerUsername()))
                .toList();
    }

    public Order findById(String id) {
        Order order = orders.get(id);

        if (order == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "order not found"
            );
        }

        if (!isAdmin() && !currentUsername().equals(order.ownerUsername())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "You are not allowed to access this order"
            );
        }

        return order;
    }
    private String currentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Jwt jwt = (Jwt) auth.getPrincipal();
        return jwt.getClaimAsString("preferred_username");
    }

    private boolean isAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }
}
