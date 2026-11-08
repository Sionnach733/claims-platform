package com.example.claims.api.dto;

import com.example.claims.validation.MedicalCodes;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;

public record ClaimLineRequest(
        @NotBlank @Pattern(regexp = MedicalCodes.CPT_PATTERN, message = "must be a valid CPT code, e.g. 99213")
        String procedureCode,

        @NotBlank @Pattern(regexp = MedicalCodes.ICD10_PATTERN, message = "must be a valid ICD-10 code, e.g. J45.909")
        String diagnosisCode,

        @Min(1) @Max(999)
        int units,

        @NotNull @DecimalMin(value = "0.01") @Digits(integer = 10, fraction = 2)
        BigDecimal billedAmount) {
}
