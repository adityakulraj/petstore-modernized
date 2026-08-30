package com.mongodb.modernization.petstore.payments.domain;

import com.mongodb.modernization.petstore.cart.domain.Cart;
import com.mongodb.modernization.petstore.cart.domain.CartLine;
import com.mongodb.modernization.petstore.orders.domain.Order;
import com.mongodb.modernization.petstore.payments.application.PaymentDeclinedException;
import com.mongodb.modernization.petstore.shared.domain.Address;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentTest {
    private static final Address ADDRESS = new Address("Alice", "1 Main", "", "Pune", "MH", "411001", "India");

    @Test
    void authorizesCapturesAndRefundsWithoutRetainingTheToken() {
        var payment = Payment.authorize(order(), Payment.APPROVED_DEMO_TOKEN, Instant.EPOCH);
        assertThat(payment.status()).isEqualTo(Payment.Status.AUTHORIZED);
        assertThat(payment.toString()).doesNotContain(Payment.APPROVED_DEMO_TOKEN);

        var captured = payment.capture(Instant.EPOCH.plusSeconds(1));
        var refunded = captured.refund(Instant.EPOCH.plusSeconds(2));
        assertThat(refunded.status()).isEqualTo(Payment.Status.REFUNDED);
        assertThat(refunded.captureReference()).startsWith("demo-capture-");
        assertThat(refunded.refundReference()).startsWith("demo-refund-");
    }

    @Test
    void cancellationVoidsAnAuthorizationAndDeclineCreatesNothing() {
        assertThat(Payment.authorize(order(), Payment.APPROVED_DEMO_TOKEN, Instant.EPOCH)
                .voidAuthorization(Instant.EPOCH.plusSeconds(1)).status()).isEqualTo(Payment.Status.VOIDED);
        assertThatThrownBy(() -> Payment.authorize(order(), Payment.DECLINED_DEMO_TOKEN, Instant.EPOCH))
                .isInstanceOf(PaymentDeclinedException.class);
    }

    private static Order order() {
        var cart = new Cart("cart", "alice", 1,
                List.of(new CartLine("item", "Item", new BigDecimal("125.00"), 1)));
        return Order.submitted("order", "alice", "key", Instant.EPOCH, ADDRESS, cart, new BigDecimal("500.00"));
    }
}
