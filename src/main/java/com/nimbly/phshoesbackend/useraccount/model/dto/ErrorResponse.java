package com.nimbly.phshoesbackend.useraccount.model.dto;

import java.util.List;
import java.util.Map;

public record ErrorResponse(
        int status,
        String error,
        Map<String, List<String>> errors
) {}