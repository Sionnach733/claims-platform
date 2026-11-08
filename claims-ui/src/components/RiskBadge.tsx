/** Same banding as fraud-service: LOW < 30 ≤ MEDIUM < 70 ≤ HIGH. */
export function riskBand(score: number): "low" | "medium" | "high" {
  if (score >= 70) return "high";
  if (score >= 30) return "medium";
  return "low";
}

export function RiskBadge({ score }: { score: number | null }) {
  if (score === null) {
    return <span className="badge badge-neutral">Not scored</span>;
  }
  const band = riskBand(score);
  return (
    <span className={`badge risk-${band}`} title={`Fraud risk score ${score} of 100`}>
      {score} · {band.toUpperCase()}
    </span>
  );
}
