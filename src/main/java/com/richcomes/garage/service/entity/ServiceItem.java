package com.richcomes.garage.service.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "service_items")
public class ServiceItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ServiceCategory එක සමඟ ඇති සම්බන්ධතාවය (Foreign Key)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private ServiceCategory category;

    private String name;

    private String description;

    @Column(name = "standard_labour_hours")
    private Double standardLabourHours;

    @Column(name = "default_labour_rate")
    private BigDecimal defaultLabourRate;

    @Column(name = "is_active")
    private Boolean isActive;

    public ServiceItem() {
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public ServiceCategory getCategory() {
        return category;
    }

    public void setCategory(ServiceCategory category) {
        this.category = category;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Double getStandardLabourHours() {
        return standardLabourHours;
    }

    public void setStandardLabourHours(Double standardLabourHours) {
        this.standardLabourHours = standardLabourHours;
    }

    public BigDecimal getDefaultLabourRate() {
        return defaultLabourRate;
    }

    public void setDefaultLabourRate(BigDecimal defaultLabourRate) {
        this.defaultLabourRate = defaultLabourRate;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean isActive) {
        this.isActive = isActive;
    }
}