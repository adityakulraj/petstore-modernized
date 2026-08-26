package com.mongodb.modernization.petstore.persistence.oracle;

import com.mongodb.modernization.petstore.notifications.domain.CustomerNotification;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

interface JpaCustomerNotificationRepository extends JpaRepository<CustomerNotificationJpaEntity, String> {
    List<CustomerNotificationJpaEntity> findByCustomerIdOrderByCreatedAtDesc(String customerId);
    List<CustomerNotificationJpaEntity> findByDeliveryStatusAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
            CustomerNotification.DeliveryStatus status, Instant nextAttemptAt, Pageable pageable);
}
