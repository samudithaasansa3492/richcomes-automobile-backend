package com.richcomes.garage.auth.dto;

/** Never return customer credentials or arbitrary garage customer records. */
public record CustomerAccountResponse(Long accountId, Long customerId, String username, boolean enabled) {}
