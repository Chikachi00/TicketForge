package io.github.chikachi.ticketforge.payment.application;

import io.github.chikachi.ticketforge.payment.api.PaymentCallbackRequest;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class PaymentSignatureService {

    private final String callbackSecret;

    public PaymentSignatureService(@Value("${ticketforge.payment.callback-secret}") String callbackSecret) {
        this.callbackSecret = callbackSecret;
    }

    public String signingString(PaymentCallbackRequest request) {
        return String.join("|",
                request.providerEventId(),
                request.paymentTransactionId(),
                request.orderNumber(),
                request.status(),
                formatAmount(request.amount()),
                request.currency(),
                request.occurredAt().toString()
        );
    }

    public String sign(PaymentCallbackRequest request) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(callbackSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(signingString(request).getBytes(StandardCharsets.UTF_8));
            return toHex(digest);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to create payment callback signature", exception);
        }
    }

    public boolean verify(PaymentCallbackRequest request, String providedSignature) {
        if (providedSignature == null || providedSignature.isBlank()) {
            return false;
        }
        byte[] expected = sign(request).getBytes(StandardCharsets.UTF_8);
        byte[] actual = providedSignature.trim().getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, actual);
    }

    public String formatAmount(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.UNNECESSARY).toPlainString();
    }

    private static String toHex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            builder.append(String.format("%02x", b));
        }
        return builder.toString();
    }
}
