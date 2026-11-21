
package com.ensias.secureorders.payment;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PaymentSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JwtDecoder jwtDecoder;

    private static final String PAYMENT_JSON = """
            {
                "orderId": "ORDER-001",
                "amount": 150.0
            }
            """;

    @Test
    void paymentWithoutToken_shouldReturn401() throws Exception {
        mockMvc.perform(post("/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PAYMENT_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aliceCannotPay_shouldReturn403() throws Exception {
        mockMvc.perform(post("/payments")
                        .with(jwt()
                                .jwt(j -> j
                                        .subject("alice")
                                        .claim("preferred_username", "alice"))
                                .authorities(() -> "ROLE_USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PAYMENT_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    void serviceAccountCanPay_shouldReturn200() throws Exception {
        mockMvc.perform(post("/payments")
                        .with(jwt()
                                .jwt(j -> j
                                        .subject("service-account-order-service"))
                                .authorities(() -> "ROLE_SVC_ORDER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PAYMENT_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));
    }

    @Test
    void serviceAccountPaymentAboveLimit_shouldBeDeclined() throws Exception {
        mockMvc.perform(post("/payments")
                        .with(jwt()
                                .jwt(j -> j
                                        .subject("service-account-order-service"))
                                .authorities(() -> "ROLE_SVC_ORDER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "orderId": "ORDER-002",
                                    "amount": 150000.0
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DECLINED"));
    }
}
