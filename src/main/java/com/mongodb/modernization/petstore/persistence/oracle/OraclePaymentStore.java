package com.mongodb.modernization.petstore.persistence.oracle;

import com.mongodb.modernization.petstore.orders.domain.Order;
import com.mongodb.modernization.petstore.payments.application.PaymentStore;
import com.mongodb.modernization.petstore.payments.domain.Payment;
import com.mongodb.modernization.petstore.shared.application.NotFoundException;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.function.Function;

@Repository
@Profile("oracle")
class OraclePaymentStore implements PaymentStore {
    private final JpaPaymentRepository payments;

    /** Creates the Oracle payment adapter with its JPA repository and database executor. */
    OraclePaymentStore(JpaPaymentRepository payments) { this.payments = payments; }

    /** Executes the authorize persistence operation against the selected database. */
    @Override public Payment authorize(Order order, String token, Instant when) {
        return payments.findById(order.id()).map(PaymentJpaEntity::toDomain).orElseGet(() ->
                payments.saveAndFlush(new PaymentJpaEntity(Payment.authorize(order, token, when))).toDomain());
    }

    /** Executes the capture persistence operation against the selected database. */
    @Override public Payment capture(Order order, Instant when) { return transition(order, value -> value.capture(when)); }
    /** Executes the void authorization persistence operation against the selected database. */
    @Override public Payment voidAuthorization(Order order, Instant when) { return transition(order, value -> value.voidAuthorization(when)); }
    /** Executes the refund persistence operation against the selected database. */
    @Override public Payment refund(Order order, Instant when) { return transition(order, value -> value.refund(when)); }

    /** Executes the payments persistence operation against the selected database. */
    @Override public List<Payment> payments(String customerId) {
        return payments.findByCustomerIdOrderByCreatedAtDesc(customerId).stream().map(PaymentJpaEntity::toDomain).toList();
    }

    /** Executes the transition persistence operation against the selected database. */
    private Payment transition(Order order, Function<Payment, Payment> transition) {
        var entity = payments.findByOrderIdForUpdate(order.id())
                .orElseThrow(() -> new NotFoundException("Payment not found for order " + order.id()));
        if (!entity.customerId.equals(order.customerId())) throw new NotFoundException("Payment not found");
        var current = entity.toDomain();
        var changed = transition.apply(current);
        if (changed == current) return current;
        entity.replaceWith(changed);
        return payments.saveAndFlush(entity).toDomain();
    }
}
