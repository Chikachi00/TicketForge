package io.github.chikachi.ticketforge.payment.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.chikachi.ticketforge.common.exception.ApiException;
import io.github.chikachi.ticketforge.common.exception.ResourceNotFoundException;
import io.github.chikachi.ticketforge.payment.application.PaymentApplicationService;
import io.github.chikachi.ticketforge.payment.application.PaymentCallbackService;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest({PaymentController.class, PaymentCallbackController.class})
class PaymentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PaymentApplicationService paymentApplicationService;

    @MockBean
    private PaymentCallbackService paymentCallbackService;

    @Test
    void createPaymentSessionReturnsCreatedForNewSession() throws Exception {
        when(paymentApplicationService.createPaymentSession("user@ticketforge.local", "pay-key", "TF-1"))
                .thenReturn(new PaymentSessionResult(session(), true));

        mockMvc.perform(post("/api/payments/orders/TF-1")
                        .header("X-User-Email", "user@ticketforge.local")
                        .header("Idempotency-Key", "pay-key"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/payments/PAY-1"))
                .andExpect(jsonPath("$.paymentTransactionId").value("PAY-1"));
    }

    @Test
    void createPaymentSessionReturnsOkForExistingPendingSession() throws Exception {
        when(paymentApplicationService.createPaymentSession("user@ticketforge.local", "pay-key", "TF-1"))
                .thenReturn(new PaymentSessionResult(session(), false));

        mockMvc.perform(post("/api/payments/orders/TF-1")
                        .header("X-User-Email", "user@ticketforge.local")
                        .header("Idempotency-Key", "pay-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void paymentNotFoundReturns404() throws Exception {
        when(paymentApplicationService.getPayment("user@ticketforge.local", "PAY-MISSING"))
                .thenThrow(new ResourceNotFoundException("PAYMENT_NOT_FOUND", "Payment not found: PAY-MISSING"));

        mockMvc.perform(get("/api/payments/PAY-MISSING").header("X-User-Email", "user@ticketforge.local"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PAYMENT_NOT_FOUND"));
    }

    @Test
    void invalidSignatureReturns401() throws Exception {
        when(paymentCallbackService.handleCallback(any(PaymentCallbackRequest.class), org.mockito.ArgumentMatchers.eq("bad")))
                .thenThrow(new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_PAYMENT_SIGNATURE", "Invalid payment callback signature"));

        mockMvc.perform(post("/api/payments/callback")
                        .header("X-Payment-Signature", "bad")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "providerEventId":"EVT-1",
                                  "paymentTransactionId":"PAY-1",
                                  "orderNumber":"TF-1",
                                  "status":"SUCCESS",
                                  "amount":1280.00,
                                  "currency":"CNY",
                                  "occurredAt":"2026-06-16T10:02:00Z"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_PAYMENT_SIGNATURE"));
    }

    private static PaymentSessionResponse session() {
        return new PaymentSessionResponse(
                "PAY-1",
                "TF-1",
                new BigDecimal("1280.00"),
                "CNY",
                "PENDING",
                Instant.parse("2026-06-16T10:00:00Z")
        );
    }

}
