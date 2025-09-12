package com.nimbly.phshoesbackend.useraccount.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AccountResponse {
    private String userid;
    private String email;
    private Boolean isVerified;
    private String createdAt;
    private String updatedAt;
}
