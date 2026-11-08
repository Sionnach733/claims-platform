package com.example.claims.api;

import com.example.claims.api.dto.ClaimResponse;
import com.example.claims.api.dto.ClaimSummaryResponse;
import com.example.claims.api.dto.PageResponse;
import com.example.claims.api.dto.SubmitClaimRequest;
import com.example.claims.api.dto.UpdateStatusRequest;
import com.example.claims.domain.ClaimStatus;
import com.example.claims.service.ClaimService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.LocalDate;
import java.util.function.Function;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/claims")
@PreAuthorize("hasAnyRole('SUBMITTER', 'ADJUDICATOR')")
@Tag(name = "Claims", description = "Submit, search and adjudicate healthcare claims")
public class ClaimController {

    private final ClaimService claimService;

    public ClaimController(ClaimService claimService) {
        this.claimService = claimService;
    }

    @PostMapping
    @Operation(summary = "Submit a claim", description = "Validates and stores the claim with status SUBMITTED.")
    @ApiResponse(responseCode = "201", description = "Created; Location header points at the new claim")
    @ApiResponse(responseCode = "400", description = "Validation failed (per-field errors)", content = @Content(
            mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "422", description = "Business rule violated, e.g. unknown member", content = @Content(
            mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    public ResponseEntity<ClaimResponse> submit(@Valid @RequestBody SubmitClaimRequest request) {
        ClaimResponse claim = claimService.submit(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{claimNumber}").buildAndExpand(claim.claimNumber()).toUri();
        return ResponseEntity.created(location).body(claim);
    }

    @GetMapping("/{claimNumber}")
    @Operation(summary = "Get a claim with its lines and status history")
    @ApiResponse(responseCode = "404", description = "No such claim", content = @Content(
            mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    public ClaimResponse get(@PathVariable @Parameter(example = "CLM-2026-00000001") String claimNumber) {
        return claimService.get(claimNumber);
    }

    @GetMapping
    @Operation(summary = "Search claims", description = "All filters are optional. Results are summaries, newest first.")
    public PageResponse<ClaimSummaryResponse> search(
            @RequestParam(required = false) @Parameter(example = "AET100000001") String memberId,
            @RequestParam(required = false) ClaimStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @ParameterObject @PageableDefault(size = 20, sort = "submittedAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return PageResponse.from(claimService.search(memberId, status, from, to, pageable), Function.identity());
    }

    @PostMapping("/{claimNumber}/adjudicate")
    @PreAuthorize("hasRole('ADJUDICATOR')")
    @Operation(summary = "Adjudicate a SUBMITTED claim",
            description = "Runs fraud screening and the rules engine: APPROVED (priced), DENIED or IN_REVIEW.")
    @ApiResponse(responseCode = "403", description = "Requires the ADJUDICATOR role", content = @Content(
            mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "409", description = "Already adjudicated, or changed concurrently", content = @Content(
            mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    public ClaimResponse adjudicate(@PathVariable String claimNumber) {
        return claimService.adjudicate(claimNumber);
    }

    @PatchMapping("/{claimNumber}/status")
    @PreAuthorize("hasRole('ADJUDICATOR')")
    @Operation(summary = "Change claim status manually",
            description = "Resolve a review (APPROVED / DENIED with reason) or record payment (PAID).")
    @ApiResponse(responseCode = "409", description = "Transition not allowed from the current status", content = @Content(
            mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    public ClaimResponse changeStatus(@PathVariable String claimNumber,
                                      @Valid @RequestBody UpdateStatusRequest request) {
        return claimService.changeStatus(claimNumber, request);
    }
}
