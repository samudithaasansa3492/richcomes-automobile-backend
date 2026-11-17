package com.richcomes.garage.auth.dto;

/** Small owner-scoped self-service DTO. Do not expose NIC, credit terms or other customers. */
public record CustomerProfileResponse(Long customerId, String name, String email,
                                      String mobile, String city) {}
