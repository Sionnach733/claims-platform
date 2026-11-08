package com.example.claims.api;

import com.example.claims.api.dto.ClaimResponse;
import com.example.claims.api.dto.ClaimSummaryResponse;
import com.example.claims.api.dto.PageResponse;
import com.example.claims.api.dto.SubmitClaimRequest;
import com.example.claims.api.dto.UpdateStatusRequest;
import com.example.claims.domain.ClaimStatus;
import com.example.claims.service.ClaimService;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.LocalDate;
import java.util.function.Function;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
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
public class ClaimController {

    private final ClaimService claimService;

    public ClaimController(ClaimService claimService) {
        this.claimService = claimService;
    }

    @PostMapping
    public ResponseEntity<ClaimResponse> submit(@Valid @RequestBody SubmitClaimRequest request) {
        ClaimResponse claim = claimService.submit(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{claimNumber}").buildAndExpand(claim.claimNumber()).toUri();
        return ResponseEntity.created(location).body(claim);
    }

    @GetMapping("/{claimNumber}")
    public ClaimResponse get(@PathVariable String claimNumber) {
        return claimService.get(claimNumber);
    }

    @GetMapping
    public PageResponse<ClaimSummaryResponse> search(
            @RequestParam(required = false) String memberId,
            @RequestParam(required = false) ClaimStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @PageableDefault(size = 20, sort = "submittedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return PageResponse.from(claimService.search(memberId, status, from, to, pageable), Function.identity());
    }

    /** Run the adjudication rules. 409 if the claim has already been adjudicated. */
    @PostMapping("/{claimNumber}/adjudicate")
    public ClaimResponse adjudicate(@PathVariable String claimNumber) {
        return claimService.adjudicate(claimNumber);
    }

    /** Manual status transition, guarded by the claim state machine (409 on an illegal move). */
    @PatchMapping("/{claimNumber}/status")
    public ClaimResponse changeStatus(@PathVariable String claimNumber,
                                      @Valid @RequestBody UpdateStatusRequest request) {
        return claimService.changeStatus(claimNumber, request);
    }
}
