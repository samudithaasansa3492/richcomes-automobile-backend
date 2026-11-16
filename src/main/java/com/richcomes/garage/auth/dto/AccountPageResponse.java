package com.richcomes.garage.auth.dto;

import java.util.List;

public record AccountPageResponse(List<AccountResponse> content, int page, int size,
                                  long totalElements, int totalPages) {
    public AccountPageResponse { content = List.copyOf(content); }
}
