
package com.ensias.secureorders.order;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class OrderSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OrderService orderService;


    private JwtDecoder jwtDecoder;

    @Test
    void createOrderWithoutToken_shouldReturn401() throws Exception {
        mockMvc.perform(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                    {
                        "productId": "P-200",
                        "quantity": 1
                    }
                    """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aliceCreateOrder_shouldReturn201() throws Exception {
        Order order = new Order(
                "order-1", "P-200", 1, 150.0,
                "CONFIRMED", Instant.now(), "alice"
        );

        when(orderService.create(any(OrderRequest.class)))
                .thenReturn(order);

        mockMvc.perform(post("/orders")
                        .with(jwt()
                                .jwt(token -> token.claim(
                                        "preferred_username", "alice"))
                                .authorities(new SimpleGrantedAuthority("ROLE_USER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                    {
                        "productId": "P-200",
                        "quantity": 1
                    }
                    """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ownerUsername").value("alice"));
    }

    @Test
    void bobListOrders_shouldReturn200() throws Exception {
        when(orderService.findAll()).thenReturn(List.of());

        mockMvc.perform(get("/orders")
                        .with(jwt()
                                .jwt(token -> token.claim(
                                        "preferred_username", "bob"))
                                .authorities(
                                        new SimpleGrantedAuthority("ROLE_USER"),
                                        new SimpleGrantedAuthority("ROLE_ADMIN")
                                )))
                .andExpect(status().isOk());
    }

    @Test
    void createOrderWithServiceRole_shouldReturn403() throws Exception {
        mockMvc.perform(post("/orders")
                        .with(jwt().authorities(
                                new SimpleGrantedAuthority("ROLE_SVC_ORDER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                    {
                        "productId": "P-200",
                        "quantity": 1
                    }
                    """))
                .andExpect(status().isForbidden());
    }
}
