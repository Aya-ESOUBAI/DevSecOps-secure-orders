
package com.ensias.secureorders.order;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class OrderAbacTest {

    private HttpServer server;
    private OrderService orderService;

    private final AtomicInteger reservationCalls = new AtomicInteger();
    private final AtomicInteger paymentCalls = new AtomicInteger();

    @BeforeEach
    void setUp() throws IOException {

        server = HttpServer.create(
                new InetSocketAddress("127.0.0.1", 0), 0
        );

        server.createContext("/inventory/P-200", exchange -> {
            respond(exchange, 200, """
                {
                    "productId": "P-200",
                    "name": "Test Product",
                    "stock": 100,
                    "unitPrice": 15000.0
                }
                """);
        });

        server.createContext("/inventory/reserve", exchange -> {
            reservationCalls.incrementAndGet();
            respond(exchange, 200, """
                {
                    "productId": "P-200",
                    "quantity": 1,
                    "unitPrice": 15000.0
                }
                """);
        });

        server.createContext("/payments", exchange -> {
            paymentCalls.incrementAndGet();
            respond(exchange, 200, """
                {
                    "paymentId": "PAY-001",
                    "status": "APPROVED"
                }
                """);
        });

        server.start();

        String baseUrl = "http://127.0.0.1:"
                + server.getAddress().getPort();

        ClientHttpRequestInterceptor interceptor =
                (request, body, execution) ->
                        execution.execute(request, body);

        orderService = new OrderService(
                baseUrl,
                baseUrl,
                interceptor
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();

        if (server != null) {
            server.stop(0);
        }
    }

    private void authenticate(String username, String... roles) {

        Jwt jwt = Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .subject(username)
                .claim("preferred_username", username)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();

        List<SimpleGrantedAuthority> authorities =
                java.util.Arrays.stream(roles)
                        .map(role ->
                                new SimpleGrantedAuthority("ROLE_" + role))
                        .toList();

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        jwt, null, authorities
                );

        SecurityContextHolder.getContext()
                .setAuthentication(authentication);
    }

    private void respond(
            com.sun.net.httpserver.HttpExchange exchange,
            int status,
            String json
    ) throws IOException {

        byte[] response =
                json.getBytes(StandardCharsets.UTF_8);

        exchange.getResponseHeaders()
                .set("Content-Type", "application/json");

        exchange.sendResponseHeaders(status, response.length);

        try (var output = exchange.getResponseBody()) {
            output.write(response);
        }
    }

    @Test
    void aliceCannotCreateExpensiveOrder_shouldReturn403() {

        authenticate("alice", "USER");

        ResponseStatusException exception =
                assertThrows(
                        ResponseStatusException.class,
                        () -> orderService.create(
                                new OrderRequest("P-200", 1)
                        )
                );

        assertEquals(
                HttpStatus.FORBIDDEN,
                exception.getStatusCode()
        );

        assertEquals(0, reservationCalls.get());
        assertEquals(0, paymentCalls.get());
    }

    @Test
    void bobAdminCanCreateExpensiveOrder() {

        authenticate("bob", "USER", "ADMIN");

        Order order = orderService.create(
                new OrderRequest("P-200", 1)
        );

        assertEquals("bob", order.ownerUsername());
        assertEquals(15000.0, order.total());
        assertEquals("CONFIRMED", order.status());

        assertEquals(1, reservationCalls.get());
        assertEquals(1, paymentCalls.get());
    }

    @Test
    void aliceCannotAccessBobsOrder_shouldReturn403() {

        authenticate("bob", "USER", "ADMIN");

        Order bobOrder = orderService.create(
                new OrderRequest("P-200", 1)
        );

        authenticate("alice", "USER");

        ResponseStatusException exception =
                assertThrows(
                        ResponseStatusException.class,
                        () -> orderService.findById(bobOrder.id())
                );

        assertEquals(
                HttpStatus.FORBIDDEN,
                exception.getStatusCode()
        );
    }

    @Test
    void bobAdminCanAccessAnyOrder() {

        authenticate("bob", "USER", "ADMIN");

        Order bobOrder = orderService.create(
                new OrderRequest("P-200", 1)
        );

        Order result = orderService.findById(bobOrder.id());

        assertEquals(bobOrder.id(), result.id());
    }
}
