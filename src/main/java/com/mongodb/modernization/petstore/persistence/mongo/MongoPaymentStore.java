package com.mongodb.modernization.petstore.persistence.mongo;

import com.mongodb.modernization.petstore.orders.domain.Order;
import com.mongodb.modernization.petstore.payments.application.PaymentStore;
import com.mongodb.modernization.petstore.payments.domain.Payment;
import com.mongodb.modernization.petstore.shared.application.NotFoundException;
import com.mongodb.modernization.petstore.shared.application.StoreConflictException;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.function.Function;

@Repository
@Profile("mongo")
class MongoPaymentStore implements PaymentStore {
    private final MongoPaymentRepository payments;

    /** Creates a mongo payment store and wires its required collaborators. */
    MongoPaymentStore(MongoPaymentRepository payments) { this.payments = payments; }

    /** Executes the authorize persistence operation against the selected database. */
    @Override public Payment authorize(Order order, String token, Instant when) {
        return payments.findById(order.id()).map(PaymentDocument::toDomain).orElseGet(() ->
                payments.insert(new PaymentDocument(Payment.authorize(order, token, when))).toDomain());
    }

    /** Executes the capture persistence operation against the selected database. */
    @Override public Payment capture(Order order, Instant when) { return transition(order, value -> value.capture(when)); }
    /** Executes the void authorization persistence operation against the selected database. */
    @Override public Payment voidAuthorization(Order order, Instant when) { return transition(order, value -> value.voidAuthorization(when)); }
    /** Executes the refund persistence operation against the selected database. */
    @Override public Payment refund(Order order, Instant when) { return transition(order, value -> value.refund(when)); }

    /** Executes the payments persistence operation against the selected database. */
    @Override public List<Payment> payments(String customerId) {
        return payments.findByCustomerIdOrderByCreatedAtDesc(customerId).stream().map(PaymentDocument::toDomain).toList();
    }

    /** Executes the transition persistence operation against the selected database. */
    private Payment transition(Order order, Function<Payment, Payment> transition) {
        var document = payments.findById(order.id())
                .orElseThrow(() -> new NotFoundException("Payment not found for order " + order.id()));
        if (!document.customerId.equals(order.customerId())) throw new NotFoundException("Payment not found");
        var current = document.toDomain();
        var changed = transition.apply(current);
        if (changed == current) return current;
        document.replaceWith(changed);
        try {
            return payments.save(document).toDomain();
        } catch (OptimisticLockingFailureException conflict) {
            throw new StoreConflictException("Payment changed in another request; refresh and retry", conflict);
        }
    }
}
