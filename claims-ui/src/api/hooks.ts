import { keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api } from "./client";
import type { Claim, ClaimSearch, ClaimStatus, SubmitClaimRequest } from "./types";

export const queryKeys = {
  claims: (params: ClaimSearch) => ["claims", params] as const,
  claim: (claimNumber: string) => ["claim", claimNumber] as const,
  providers: ["providers"] as const,
};

export function useClaims(params: ClaimSearch) {
  return useQuery({
    queryKey: queryKeys.claims(params),
    queryFn: () => api.searchClaims(params),
    placeholderData: keepPreviousData, // keep the old page visible while the next one loads
  });
}

export function useClaim(claimNumber: string) {
  return useQuery({ queryKey: queryKeys.claim(claimNumber), queryFn: () => api.getClaim(claimNumber) });
}

export function useProviders() {
  return useQuery({ queryKey: queryKeys.providers, queryFn: api.providers, staleTime: 5 * 60_000 });
}

/** After any write, put the server's version of the claim in the cache and refresh search results. */
function useClaimWrite<TArgs>(fn: (args: TArgs) => Promise<Claim>) {
  const client = useQueryClient();
  return useMutation({
    mutationFn: fn,
    onSuccess: (claim) => {
      client.setQueryData(queryKeys.claim(claim.claimNumber), claim);
      void client.invalidateQueries({ queryKey: ["claims"] });
    },
  });
}

export const useSubmitClaim = () => useClaimWrite((body: SubmitClaimRequest) => api.submitClaim(body));

export const useAdjudicate = () => useClaimWrite((claimNumber: string) => api.adjudicate(claimNumber));

export const useChangeStatus = () =>
  useClaimWrite(
    ({ claimNumber, status, reason }: { claimNumber: string; status: ClaimStatus; reason?: string }) =>
      api.changeStatus(claimNumber, status, reason),
  );
