package com.mongodb.modernization.petstore.shared.application;

import com.mongodb.modernization.petstore.cart.domain.Cart;
import com.mongodb.modernization.petstore.catalog.domain.Product;
import com.mongodb.modernization.petstore.orders.domain.Order;
import com.mongodb.modernization.petstore.shared.domain.Address;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StorefrontServiceTest {
    @Test
    void returnsExistingOrderWithoutExecutingCheckoutAgain() {
        var store = mock(StorefrontStore.class);
        var order = mock(Order.class);
        var address = mock(Address.class);
        when(store.orderByIdempotencyKey("alice", "same-key")).thenReturn(Optional.of(order));
        var service = new StorefrontService(store);

        assertThat(service.checkout("alice", 7, "same-key", address)).isSameAs(order);
        verify(store).orderByIdempotencyKey("alice", "same-key");
    }
}
