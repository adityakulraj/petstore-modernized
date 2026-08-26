package com.mongodb.modernization.petstore.notifications.api;

import com.mongodb.modernization.petstore.notifications.application.CustomerNotificationService;
import com.mongodb.modernization.petstore.notifications.domain.CustomerNotification;
import jakarta.validation.Valid;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/notifications")
public class CustomerNotificationController {
    private final CustomerNotificationService notifications;

    public CustomerNotificationController(CustomerNotificationService notifications) {
        this.notifications = notifications;
    }

    @GetMapping
    List<CustomerNotification> notifications(Authentication authentication) {
        return notifications.notifications(authentication.getName());
    }

    @PostMapping("/{notificationId}/read")
    CustomerNotification markRead(Authentication authentication, @PathVariable String notificationId,
                                  @Valid @RequestBody MarkReadRequest request) {
        return notifications.markRead(authentication.getName(), notificationId, request.expectedVersion());
    }

    record MarkReadRequest(@PositiveOrZero long expectedVersion) {}
}
