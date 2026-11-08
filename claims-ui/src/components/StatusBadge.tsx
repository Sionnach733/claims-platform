import type { ClaimStatus } from "../api/types";
import { humanStatus } from "../lib/format";

export function StatusBadge({ status }: { status: ClaimStatus }) {
  return <span className={`badge badge-${status.toLowerCase()}`}>{humanStatus(status)}</span>;
}
