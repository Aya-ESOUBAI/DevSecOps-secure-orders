
package com.ensias.secureorders.inventory;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class InventorySecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JwtDecoder jwtDecoder;

    private static final String RESERVE_JSON = """
            {
                "productId": "P-200",
                "quantity": 1
            }
            """;

    @Test
    void reserveWithoutToken_shouldReturn401() throws Exception {
        mockMvc.perform(post("/inventory/reserve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(RESERVE_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aliceCannotReserve_shouldReturn403() throws Exception {
        mockMvc.perform(post("/inventory/reserve")
                        .with(jwt()
                                .jwt(j -> j
                                        .subject("alice")
                                        .claim("preferred_username", "alice")
                                        .claim("realm_access",
                                                Map.of("roles", List.of("USER")))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(RESERVE_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    void serviceAccountCanReserve_shouldReturn200() throws Exception {
        mockMvc.perform(post("/inventory/reserve")
                        .with(jwt()
                                .jwt(j -> j
                                        .subject("service-account-order-service")
                                        .claim("realm_access",
                                                Map.of("roles", List.of("SVC_ORDER"))))
                                .authorities(() -> "ROLE_SVC_ORDER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(RESERVE_JSON))
                .andExpect(status().isOk());
    }
}
