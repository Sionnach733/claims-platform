import type { FormEvent } from "react";
import { Link, useSearchParams } from "react-router";
import { useClaims } from "../api/hooks";
import { CLAIM_STATUSES, type ClaimSearch, type ClaimStatus } from "../api/types";
import { ProblemAlert } from "../components/ProblemAlert";
import { StatusBadge } from "../components/StatusBadge";
import { formatDate, formatMoney, humanStatus } from "../lib/format";

const PAGE_SIZE = 10;

/** Filters live in the URL, so a search can be bookmarked, shared and survives a refresh. */
function readSearch(params: URLSearchParams): ClaimSearch {
  const status = params.get("status");
  return {
    memberId: params.get("memberId") ?? undefined,
    status: CLAIM_STATUSES.includes(status as ClaimStatus) ? (status as ClaimStatus) : undefined,
    from: params.get("from") ?? undefined,
    to: params.get("to") ?? undefined,
    page: Number(params.get("page") ?? 0),
    size: PAGE_SIZE,
  };
}

export function ClaimsListPage() {
  const [params, setParams] = useSearchParams();
  const search = readSearch(params);
  const { data, error, isPending, isFetching } = useClaims(search);

  function onFilter(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const next = new URLSearchParams();
    for (const key of ["memberId", "status", "from", "to"]) {
      const value = String(form.get(key) ?? "").trim();
      if (value) next.set(key, key === "memberId" ? value.toUpperCase() : value);
    }
    setParams(next);
  }

  function goToPage(page: number) {
    const next = new URLSearchParams(params);
    next.set("page", String(page));
    setParams(next);
  }

  return (
    <section>
      <div className="page-header">
        <h1>Claims</h1>
        <Link className="button primary" to="/claims/new">
          Submit claim
        </Link>
      </div>

      <form className="filters" onSubmit={onFilter} aria-label="Filter claims" key={params.toString()}>
        <label>
          Member ID
          <input name="memberId" defaultValue={search.memberId} placeholder="AET100000001" />
        </label>
        <label>
          Status
          <select name="status" defaultValue={search.status ?? ""}>
            <option value="">Any</option>
            {CLAIM_STATUSES.map((s) => (
              <option key={s} value={s}>
                {humanStatus(s)}
              </option>
            ))}
          </select>
        </label>
        <label>
          Service from
          <input type="date" name="from" defaultValue={search.from} />
        </label>
        <label>
          Service to
          <input type="date" name="to" defaultValue={search.to} />
        </label>
        <div className="filter-actions">
          <button type="submit" className="button">
            Search
          </button>
          <button type="button" className="button link" onClick={() => setParams(new URLSearchParams())}>
            Clear
          </button>
        </div>
      </form>

      <ProblemAlert error={error} title="Couldn't load claims" />

      {isPending && !error && <p aria-live="polite">Loading claims…</p>}

      {data && (
        <>
          <div className="table-wrap" aria-busy={isFetching}>
            <table>
              <caption className="visually-hidden">Claims matching the filters</caption>
              <thead>
                <tr>
                  <th scope="col">Claim</th>
                  <th scope="col">Member</th>
                  <th scope="col">Provider NPI</th>
                  <th scope="col">Service date</th>
                  <th scope="col">Status</th>
                  <th scope="col" className="num">
                    Billed
                  </th>
                  <th scope="col" className="num">
                    Paid
                  </th>
                </tr>
              </thead>
              <tbody>
                {data.content.length === 0 && (
                  <tr>
                    <td colSpan={7} className="empty">
                      No claims match these filters.
                    </td>
                  </tr>
                )}
                {data.content.map((c) => (
                  <tr key={c.claimNumber}>
                    <td>
                      <Link to={`/claims/${c.claimNumber}`}>{c.claimNumber}</Link>
                    </td>
                    <td>{c.memberId}</td>
                    <td>{c.providerNpi}</td>
                    <td>{formatDate(c.serviceDate)}</td>
                    <td>
                      <StatusBadge status={c.status} />
                    </td>
                    <td className="num">{formatMoney(c.totalBilled)}</td>
                    <td className="num">{formatMoney(c.totalPaid)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          <nav className="pagination" aria-label="Pagination">
            <button
              className="button"
              disabled={data.page === 0}
              onClick={() => goToPage(data.page - 1)}
              type="button"
            >
              Previous
            </button>
            <span>
              Page {data.totalPages === 0 ? 0 : data.page + 1} of {data.totalPages} · {data.totalElements}{" "}
              claims
            </span>
            <button
              className="button"
              disabled={data.page + 1 >= data.totalPages}
              onClick={() => goToPage(data.page + 1)}
              type="button"
            >
              Next
            </button>
          </nav>
        </>
      )}
    </section>
  );
}
