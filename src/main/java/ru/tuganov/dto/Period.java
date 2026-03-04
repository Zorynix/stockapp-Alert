package ru.tuganov.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum Period {
    DAY("day"),
    WEEK("week"),
    MONTH("month"),
    YEAR("year"),
    ;
    private final String value;
}
