package com.peterscode.rentalmanagementsystem.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ValidateCodeRequest {

    @NotBlank(message = "Reset code is required")
    @Pattern(regexp = "^\\d{6}$", message = "Reset code must be 6 digits")
    private String code;
}
