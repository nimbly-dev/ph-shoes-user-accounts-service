package com.nimbly.phshoesbackend.useraccount.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GetContentFromTokenResponse {
    private String email;
}
