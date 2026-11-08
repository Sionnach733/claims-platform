package com.example.claims.api;

import com.example.claims.api.dto.ClaimResponse;
import com.example.claims.api.dto.ClaimSummaryResponse;
import com.example.claims.api.dto.PageResponse;
import com.example.claims.api.dto.SubmitClaimRequest;
import com.example.claims.domain.Claim;
import com.example.claims.domain.ClaimStatus;
import com.example.claims.service.ClaimService;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.LocalDate;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
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
        Claim claim = claimService.submit(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{claimNumber}").buildAndExpand(claim.getClaimNumber()).toUri();
        return ResponseEntity.created(location).body(ClaimResponse.from(claim));
    }

    @GetMapping("/{claimNumber}")
    public ClaimResponse get(@PathVariable String claimNumber) {
        return ClaimResponse.from(claimService.get(claimNumber));
    }

    @GetMapping
    public PageResponse<ClaimSummaryResponse> search(
            @RequestParam(required = false) String memberId,
            @RequestParam(required = false) ClaimStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @PageableDefault(size = 20, sort = "submittedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return PageResponse.from(claimService.search(memberId, status, from, to, pageable), ClaimSummaryResponse::from);
    }
}
