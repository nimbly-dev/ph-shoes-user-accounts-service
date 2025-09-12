package com.nimbly.phshoesbackend.useraccount.model.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AccountCreateRequest {
    @NotBlank(message = "{field.required}")
    @Email(message = "{email.invalid}")
    private String email;

    @NotBlank(message = "{field.required}")
    @Size(min = 12, message = "{password.length}")
    @Pattern(
            regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^\\w\\s]).+$",
            message = "{password.complexity}"
    )
    private String password;
}
