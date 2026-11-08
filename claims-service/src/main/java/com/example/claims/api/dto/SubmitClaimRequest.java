package com.example.claims.api.dto;

import com.example.claims.validation.MedicalCodes;
import com.example.claims.validation.Npi;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

public record SubmitClaimRequest(
        @NotBlank @Pattern(regexp = MedicalCodes.MEMBER_ID_PATTERN, message = "must look like AET123456789")
        String memberId,

        @NotBlank @Npi
        String providerNpi,

        @NotNull @PastOrPresent
        LocalDate serviceDate,

        @NotEmpty @Size(max = 50)
        List<@Valid @NotNull ClaimLineRequest> lines) {
}
