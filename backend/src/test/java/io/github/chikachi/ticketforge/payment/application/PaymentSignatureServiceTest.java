package io.github.chikachi.ticketforge.payment.application;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.chikachi.ticketforge.payment.api.PaymentCallbackRequest;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class PaymentSignatureServiceTest {

    private final PaymentSignatureService service = new PaymentSignatureService("test-secret");

    @Test
    void signsAndVerifiesCanonicalCallbackPayload() {
        PaymentCallbackRequest request = request(new BigDecimal("1280.0"));

        String signature = service.sign(request);

        assertThat(service.signingString(request)).isEqualTo("EVT-1|PAY-1|TF-1|SUCCESS|1280.00|CNY|2026-06-16T10:02:00Z");
        assertThat(service.verify(request, signature)).isTrue();
        String tampered = signature.substring(0, signature.length() - 1)
                + (signature.endsWith("0") ? "1" : "0");
        assertThat(service.verify(request, tampered)).isFalse();
    }

    @Test
    void formatsPaymentAmountWithTwoDecimals() {
        assertThat(service.formatAmount(new BigDecimal("88"))).isEqualTo("88.00");
    }

    private static PaymentCallbackRequest request(BigDecimal amount) {
        return new PaymentCallbackRequest(
                "EVT-1",
                "PAY-1",
                "TF-1",
                "SUCCESS",
                amount,
                "CNY",
                Instant.parse("2026-06-16T10:02:00Z"),
                null
        );
    }
}
