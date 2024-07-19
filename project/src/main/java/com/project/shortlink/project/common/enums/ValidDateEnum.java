package com.project.shortlink.project.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum ValidDateEnum {
    PERMANENT(0),

    CUSTOMIZE(1);

    @Getter
    private final Integer type;
}
