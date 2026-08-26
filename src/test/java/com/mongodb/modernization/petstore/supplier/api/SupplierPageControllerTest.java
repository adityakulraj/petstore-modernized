package com.mongodb.modernization.petstore.supplier.api;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SupplierPageControllerTest {
    @Test
    void forwardsFriendlySupplierRouteToStaticPortal() {
        assertThat(new SupplierPageController().supplierPortal())
                .isEqualTo("forward:/supplier/index.html");
    }
}
