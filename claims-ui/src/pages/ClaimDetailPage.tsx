import { useState } from "react";
import { Link, useParams } from "react-router";
import { useAdjudicate, useChangeStatus, useClaim } from "../api/hooks";
import { hasRole, useSession } from "../auth/session";
import type { Claim } from "../api/types";
import { ProblemAlert } from "../components/ProblemAlert";
import { RiskBadge } from "../components/RiskBadge";
import { StatusBadge } from "../components/StatusBadge";
import { formatDate, formatDateTime, formatMoney, humanStatus } from "../lib/format";

export function ClaimDetailPage() {
  const { claimNumber = "" } = useParams();
  const { data: claim, error, isPending } = useClaim(claimNumber);

  if (isPending) return <p aria-live="polite">Loading claim…</p>;
  if (error || !claim) {
    return (
      <section>
        <ProblemAlert error={error} title="Couldn't load this claim" />
        <Link to="/">Back to claims</Link>
      </section>
    );
  }

  return (
    <section>
      <p>
        <Link to="/">← All claims</Link>
      </p>
      <div className="page-header">
        <h1>
          Claim {claim.claimNumber} <StatusBadge status={claim.status} />
        </h1>
      </div>

      <dl className="summary">
        <div>
          <dt>Member</dt>
          <dd>{claim.memberId}</dd>
        </div>
        <div>
          <dt>Provider NPI</dt>
          <dd>{claim.providerNpi}</dd>
        </div>
        <div>
          <dt>Date of service</dt>
          <dd>{formatDate(claim.serviceDate)}</dd>
        </div>
        <div>
          <dt>Billed</dt>
          <dd>{formatMoney(claim.totalBilled)}</dd>
        </div>
        <div>
          <dt>Allowed</dt>
          <dd>{formatMoney(claim.totalAllowed)}</dd>
        </div>
        <div>
          <dt>Paid</dt>
          <dd>{formatMoney(claim.totalPaid)}</dd>
        </div>
        <div>
          <dt>Fraud risk</dt>
          <dd>
            <RiskBadge score={claim.riskScore} />
          </dd>
        </div>
      </dl>

      {claim.denialReason && (
        <div className="alert alert-warning" role="status">
          <strong>Denied:</strong> {claim.denialReason}
        </div>
      )}

      <ClaimActions claim={claim} />

      <h2>Service lines</h2>
      <div className="table-wrap">
        <table>
          <caption className="visually-hidden">Service lines</caption>
          <thead>
            <tr>
              <th scope="col">#</th>
              <th scope="col">CPT</th>
              <th scope="col">Diagnosis</th>
              <th scope="col" className="num">
                Units
              </th>
              <th scope="col" className="num">
                Billed
              </th>
            </tr>
          </thead>
          <tbody>
            {claim.lines.map((l) => (
              <tr key={l.lineNumber}>
                <td>{l.lineNumber}</td>
                <td>{l.procedureCode}</td>
                <td>{l.diagnosisCode}</td>
                <td className="num">{l.units}</td>
                <td className="num">{formatMoney(l.billedAmount)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <h2>Status history</h2>
      <ol className="timeline" aria-label="Status history">
        {claim.history.map((h, i) => (
          <li key={i}>
            <div className="timeline-head">
              <StatusBadge status={h.toStatus} />
              <time dateTime={h.changedAt}>{formatDateTime(h.changedAt)}</time>
              <span className="muted">by {h.changedBy}</span>
            </div>
            {h.reason && <p>{h.reason}</p>}
          </li>
        ))}
      </ol>
    </section>
  );
}

/**
 * Only offers actions the user's role and the claim's status allow. Hiding buttons is a usability
 * feature, not security: the API enforces both rules regardless.
 */
function ClaimActions({ claim }: { claim: Claim }) {
  const session = useSession();
  const adjudicate = useAdjudicate();
  const changeStatus = useChangeStatus();
  const [reason, setReason] = useState("");
  const busy = adjudicate.isPending || changeStatus.isPending;
  const error = adjudicate.error ?? changeStatus.error;
  const n = claim.claimNumber;

  const change = (status: Claim["status"]) =>
    changeStatus.mutate({ claimNumber: n, status, reason }, { onSuccess: () => setReason("") });

  if (!hasRole(session, "ADJUDICATOR")) {
    return (
      <div className="actions-panel" aria-label="Claim actions" role="region">
        <p className="muted">Adjudication actions require the Adjudicator role.</p>
      </div>
    );
  }

  return (
    <div className="actions-panel" aria-label="Claim actions" role="region">
      {claim.status === "SUBMITTED" && (
        <>
          <button className="button primary" disabled={busy} onClick={() => adjudicate.mutate(n)}>
            {adjudicate.isPending ? "Adjudicating…" : "Adjudicate"}
          </button>
          <button className="button" disabled={busy} onClick={() => change("IN_REVIEW")}>
            Send to manual review
          </button>
        </>
      )}

      {claim.status === "IN_REVIEW" && (
        <>
          <label className="reason">
            Review notes <span className="muted">(required to deny)</span>
            <textarea value={reason} onChange={(e) => setReason(e.target.value)} rows={2} maxLength={500} />
          </label>
          <button className="button primary" disabled={busy} onClick={() => change("APPROVED")}>
            Approve
          </button>
          <button
            className="button danger"
            disabled={busy || !reason.trim()}
            onClick={() => change("DENIED")}
          >
            Deny
          </button>
        </>
      )}

      {claim.status === "APPROVED" && (
        <button className="button primary" disabled={busy} onClick={() => change("PAID")}>
          Mark as paid ({formatMoney(claim.totalAllowed)})
        </button>
      )}

      {(claim.status === "DENIED" || claim.status === "PAID") && (
        <p className="muted">This claim is {humanStatus(claim.status).toLowerCase()}. No further actions.</p>
      )}

      <ProblemAlert error={error} title="That action failed" />
    </div>
  );
}
