package com.nimbly.phshoesbackend.useraccount.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class LoginRequest {
    @Email(message = "{email.invalid}")
    @NotBlank(message = "{field.required}")
    private String email;

    @NotBlank(message = "{field.required}")
    private String password;
}
