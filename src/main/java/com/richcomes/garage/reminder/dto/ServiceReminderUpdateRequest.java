package com.richcomes.garage.reminder.dto;

import java.time.LocalDate;

public class ServiceReminderUpdateRequest {

    private LocalDate nextServiceDate;
    private LocalDate reminderDate;
    
    private String message;
    private LocalDate aiSuggestedDate;

    public ServiceReminderUpdateRequest() {
    }

    public LocalDate getNextServiceDate() {
        return nextServiceDate;
    }

    public void setNextServiceDate(LocalDate nextServiceDate) {
        this.nextServiceDate = nextServiceDate;
    }

    public LocalDate getReminderDate() {
        return reminderDate;
    }

    public void setReminderDate(LocalDate reminderDate) {
        this.reminderDate = reminderDate;
    }


    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public LocalDate getAiSuggestedDate() {
        return aiSuggestedDate;
    }

    public void setAiSuggestedDate(LocalDate aiSuggestedDate) {
        this.aiSuggestedDate = aiSuggestedDate;
    }
}
