package com.richcomes.garage.service.dto;

import java.math.BigDecimal;

public class ServiceItemDTO {
    private Long id;
    private Long categoryId;
    private String categoryName;
    private String name;
    private String description;
    private Double standardLabourHours;
    private BigDecimal defaultLabourRate;
    private Boolean isActive;

    public ServiceItemDTO() {}

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getCategoryId() { return categoryId; }
    public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }
    public String getCategoryName() { return categoryName; }
    public void setCategoryName(String categoryName) { this.categoryName = categoryName; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Double getStandardLabourHours() { return standardLabourHours; }
    public void setStandardLabourHours(Double standardLabourHours) { this.standardLabourHours = standardLabourHours; }
    public BigDecimal getDefaultLabourRate() { return defaultLabourRate; }
    public void setDefaultLabourRate(BigDecimal defaultLabourRate) { this.defaultLabourRate = defaultLabourRate; }
    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }
}