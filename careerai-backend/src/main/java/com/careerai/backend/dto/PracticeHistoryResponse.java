package com.careerai.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

/** A page of practice history (avoids serialising Spring's Page type directly). */
@Getter
@AllArgsConstructor
public class PracticeHistoryResponse {

    private List<PracticeHistoryItem> items;
    private int page;
    private int size;
    private long totalItems;
    private int totalPages;
}
