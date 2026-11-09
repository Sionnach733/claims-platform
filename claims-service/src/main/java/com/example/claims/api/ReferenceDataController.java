package com.example.claims.api;

import com.example.claims.api.dto.MemberResponse;
import com.example.claims.api.dto.MemberSummaryResponse;
import com.example.claims.api.dto.ProviderResponse;
import com.example.claims.service.ReferenceDataService;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@PreAuthorize("hasAnyRole('SUBMITTER', 'ADJUDICATOR')")
@Tag(name = "Reference data", description = "Members and providers (read-only)")
public class ReferenceDataController {

    private final ReferenceDataService referenceData;

    public ReferenceDataController(ReferenceDataService referenceData) {
        this.referenceData = referenceData;
    }

    /** All members, for the demo's member picker. A real member base would be a paginated search. */
    @GetMapping("/members")
    public List<MemberSummaryResponse> members() {
        return referenceData.members().stream().map(MemberSummaryResponse::from).toList();
    }

    @GetMapping("/members/{memberId}")
    public MemberResponse member(@PathVariable String memberId) {
        return MemberResponse.from(referenceData.member(memberId));
    }

    @GetMapping("/providers")
    public List<ProviderResponse> providers() {
        return referenceData.providers().stream().map(ProviderResponse::from).toList();
    }

    @GetMapping("/providers/{npi}")
    public ProviderResponse provider(@PathVariable String npi) {
        return ProviderResponse.from(referenceData.provider(npi));
    }
}
