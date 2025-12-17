package com.peterscode.rentalmanagementsystem.dto.request;

import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LeaseCreateRequest {

    @NotNull
    private Long tenantId;

    @NotNull
    private Long propertyId;

    @NotNull
    private LocalDate startDate;

    @NotNull
    private LocalDate endDate;

    @NotNull
    @DecimalMin(value = "0.0", inclusive = false)
    private BigDecimal monthlyRent;

    @DecimalMin(value = "0.0")
    private BigDecimal deposit;

    private String notes;
}
