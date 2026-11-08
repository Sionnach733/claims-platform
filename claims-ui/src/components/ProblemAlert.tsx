import { ApiError } from "../api/client";

/** Renders an API error (RFC 7807) or any other error in an accessible alert. */
export function ProblemAlert({ error, title }: { error: unknown; title?: string }) {
  if (!error) return null;
  const problem = error instanceof ApiError ? error.problem : undefined;
  const heading = title ?? problem?.title ?? "Something went wrong";
  const detail = problem?.detail ?? (error instanceof Error ? error.message : undefined);

  return (
    <div role="alert" className="alert alert-error">
      <strong>{heading}</strong>
      {detail && <p>{detail}</p>}
      {problem?.errors && problem.errors.length > 0 && (
        <ul>
          {problem.errors.map((e) => (
            <li key={e.field}>
              <code>{e.field}</code>: {e.message}
            </li>
          ))}
        </ul>
      )}
      {problem?.allowedTransitions && (
        <p className="muted">
          Allowed from {problem.currentStatus}:{" "}
          {problem.allowedTransitions.length ? problem.allowedTransitions.join(", ") : "none (final status)"}
        </p>
      )}
    </div>
  );
}
