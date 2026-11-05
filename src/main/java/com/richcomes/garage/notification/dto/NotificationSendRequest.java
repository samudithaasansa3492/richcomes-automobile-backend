package com.richcomes.garage.notification.dto;

import com.richcomes.garage.notification.enums.NotificationChannel;
import com.richcomes.garage.notification.enums.NotificationType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class NotificationSendRequest {

    @Positive(message = "Customer ID must be positive")
    private Long customerId;

    @Positive(message = "Vehicle ID must be positive")
    private Long vehicleId;

    @Positive(message = "Appointment ID must be positive")
    private Long appointmentId;

    @NotNull(message = "Notification type is required")
    private NotificationType type;

    @NotNull(message = "Notification channel is required")
    private NotificationChannel channel;

    @NotBlank(message = "Recipient contact information is required")
    @Size(max = 150, message = "Recipient cannot exceed 150 characters")
    private String recipient;

    @NotBlank(message = "Notification message cannot be blank")
    @Size(max = 5000, message = "Message cannot exceed 5000 characters")
    private String message;

    public NotificationSendRequest() {
    }

    public Long getCustomerId() {
        return customerId;
    }

    public void setCustomerId(Long customerId) {
        this.customerId = customerId;
    }

    public Long getVehicleId() {
        return vehicleId;
    }

    public void setVehicleId(Long vehicleId) {
        this.vehicleId = vehicleId;
    }

    public Long getAppointmentId() {
        return appointmentId;
    }

    public void setAppointmentId(Long appointmentId) {
        this.appointmentId = appointmentId;
    }

    public NotificationType getType() {
        return type;
    }

    public void setType(NotificationType type) {
        this.type = type;
    }

    public NotificationChannel getChannel() {
        return channel;
    }

    public void setChannel(NotificationChannel channel) {
        this.channel = channel;
    }

    public String getRecipient() {
        return recipient;
    }

    public void setRecipient(String recipient) {
        this.recipient = recipient;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
