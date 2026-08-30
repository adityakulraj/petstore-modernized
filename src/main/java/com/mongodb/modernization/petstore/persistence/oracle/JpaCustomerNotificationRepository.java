package com.mongodb.modernization.petstore.persistence.oracle;

import com.mongodb.modernization.petstore.notifications.domain.CustomerNotification;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

interface JpaCustomerNotificationRepository extends JpaRepository<CustomerNotificationJpaEntity, String> {
    /** Queries persisted records by customer id order by created at desc. */
    List<CustomerNotificationJpaEntity> findByCustomerIdOrderByCreatedAtDesc(String customerId);
    /** Queries persisted records by delivery status and next attempt at less than equal order by created at asc. */
    List<CustomerNotificationJpaEntity> findByDeliveryStatusAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
            CustomerNotification.DeliveryStatus status, Instant nextAttemptAt, Pageable pageable);
}
