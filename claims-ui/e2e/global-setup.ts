/** Fail fast with a clear message if the backend isn't up, instead of 30s of confusing UI timeouts. */
export default async function globalSetup() {
  const checks = [
    { name: "claims-service", url: process.env.CLAIMS_HEALTH_URL ?? "http://localhost:8080/actuator/health" },
    { name: "fraud-service", url: process.env.FRAUD_HEALTH_URL ?? "http://localhost:8000/health/ready" },
  ];
  for (const { name, url } of checks) {
    try {
      const res = await fetch(url);
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
    } catch (e) {
      throw new Error(
        `${name} is not healthy at ${url} (${(e as Error).message}). ` +
          "Start the stack first: docker compose up -d, then run fraud-service and claims-service (see README).",
        { cause: e },
      );
    }
  }
}
