import { zodResolver } from "@hookform/resolvers/zod";
import type { ReactNode } from "react";
import {
  useFieldArray,
  useForm,
  useWatch,
  type FieldPath,
  type UseFormRegisterReturn,
} from "react-hook-form";
import { useNavigate } from "react-router";
import { ApiError } from "../api/client";
import { useProviders, useSubmitClaim } from "../api/hooks";
import { ProblemAlert } from "../components/ProblemAlert";
import {
  claimFormSchema,
  emptyLine,
  todayIso,
  toRequest,
  type ClaimFormInput,
  type ClaimFormValues,
} from "../lib/claimForm";
import { formatMoney } from "../lib/format";
import {
  CPT_CODES,
  describeCpt,
  describeIcd,
  groupCodes,
  ICD10_CODES,
  type MedicalCode,
} from "../lib/medicalCodes";

const CPT_GROUPS = groupCodes(CPT_CODES);
const ICD10_GROUPS = groupCodes(ICD10_CODES);

/** Server field paths ("lines[0].units") → react-hook-form paths ("lines.0.units"). */
const toFormPath = (field: string) => field.replace(/\[(\d+)\]/g, ".$1") as FieldPath<ClaimFormInput>;

export function SubmitClaimPage() {
  const navigate = useNavigate();
  const providers = useProviders();
  const submit = useSubmitClaim();

  const {
    register,
    control,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<ClaimFormInput, unknown, ClaimFormValues>({
    resolver: zodResolver(claimFormSchema),
    defaultValues: { memberId: "", providerNpi: "", serviceDate: "", lines: [{ ...emptyLine }] },
  });
  const { fields, append, remove } = useFieldArray({ control, name: "lines" });
  const lines = useWatch({ control, name: "lines" });
  const total = (lines ?? []).reduce((sum, l) => sum + (Number(l?.billedAmount) || 0), 0);

  async function onSubmit(values: ClaimFormValues) {
    try {
      const claim = await submit.mutateAsync(toRequest(values));
      navigate(`/claims/${claim.claimNumber}`);
    } catch (e) {
      // Put server-side field errors next to the matching input as well as in the summary.
      if (e instanceof ApiError) {
        e.problem.errors?.forEach((fe) => setError(toFormPath(fe.field), { message: fe.message }));
      }
    }
  }

  return (
    <section>
      <h1>Submit a claim</h1>
      <form className="claim-form" onSubmit={handleSubmit(onSubmit)} noValidate aria-label="Submit claim">
        <fieldset>
          <legend>Claim</legend>
          <div className="grid-3">
            <Field label="Member ID" error={errors.memberId?.message} id="memberId">
              <input id="memberId" placeholder="AET100000001" {...register("memberId")} />
            </Field>
            <Field label="Provider NPI" error={errors.providerNpi?.message} id="providerNpi">
              <input id="providerNpi" list="provider-list" inputMode="numeric" {...register("providerNpi")} />
              <datalist id="provider-list">
                {providers.data?.map((p) => (
                  <option key={p.npi} value={p.npi}>
                    {p.name} ({p.inNetwork ? "in network" : "out of network"})
                  </option>
                ))}
              </datalist>
            </Field>
            <Field label="Date of service" error={errors.serviceDate?.message} id="serviceDate">
              <input id="serviceDate" type="date" max={todayIso()} {...register("serviceDate")} />
            </Field>
          </div>
        </fieldset>

        <fieldset>
          <legend>Service lines</legend>
          {errors.lines?.root?.message && <p className="field-error">{errors.lines.root.message}</p>}
          {fields.map((field, i) => {
            const e = errors.lines?.[i];
            const n = i + 1;
            return (
              <div className="line-row" key={field.id} role="group" aria-label={`Line ${n}`}>
                <Field label={`CPT code (line ${n})`} error={e?.procedureCode?.message} id={`cpt-${i}`}>
                  <CodeSelect
                    id={`cpt-${i}`}
                    groups={CPT_GROUPS}
                    placeholder="Select a procedure…"
                    hint={describeCpt(lines?.[i]?.procedureCode ?? "")}
                    {...register(`lines.${i}.procedureCode`)}
                  />
                </Field>
                <Field label={`Diagnosis (line ${n})`} error={e?.diagnosisCode?.message} id={`icd-${i}`}>
                  <CodeSelect
                    id={`icd-${i}`}
                    groups={ICD10_GROUPS}
                    placeholder="Select a diagnosis…"
                    hint={describeIcd(lines?.[i]?.diagnosisCode ?? "")}
                    {...register(`lines.${i}.diagnosisCode`)}
                  />
                </Field>
                <Field label={`Units (line ${n})`} error={e?.units?.message} id={`units-${i}`}>
                  <input id={`units-${i}`} inputMode="numeric" {...register(`lines.${i}.units`)} />
                </Field>
                <Field
                  label={`Billed amount (line ${n})`}
                  error={e?.billedAmount?.message}
                  id={`amount-${i}`}
                >
                  <input
                    id={`amount-${i}`}
                    inputMode="decimal"
                    placeholder="125.50"
                    {...register(`lines.${i}.billedAmount`)}
                  />
                </Field>
                <button
                  type="button"
                  className="button link"
                  onClick={() => remove(i)}
                  disabled={fields.length === 1}
                  aria-label={`Remove line ${n}`}
                >
                  Remove
                </button>
              </div>
            );
          })}
          <div className="lines-footer">
            <button
              type="button"
              className="button"
              onClick={() => append({ ...emptyLine })}
              disabled={fields.length >= 50}
            >
              Add line
            </button>
            <span>
              Total billed: <strong data-testid="total-billed">{formatMoney(total)}</strong>
            </span>
          </div>
        </fieldset>

        <ProblemAlert error={submit.error} title="The claim was not submitted" />

        <div className="form-actions">
          <button type="submit" className="button primary" disabled={isSubmitting}>
            {isSubmitting ? "Submitting…" : "Submit claim"}
          </button>
        </div>
      </form>
    </section>
  );
}

/** A grouped code picker; the selected code's full description is shown underneath, since narrow
 * columns truncate the option text. */
function CodeSelect({
  id,
  groups,
  placeholder,
  hint,
  ...field
}: {
  id: string;
  groups: [string, MedicalCode[]][];
  placeholder: string;
  hint?: string;
} & UseFormRegisterReturn) {
  return (
    <>
      <select id={id} aria-describedby={hint ? `${id}-hint` : undefined} {...field}>
        <option value="">{placeholder}</option>
        {groups.map(([group, codes]) => (
          <optgroup key={group} label={group}>
            {codes.map((c) => (
              <option key={c.code} value={c.code}>
                {c.code} · {c.description}
              </option>
            ))}
          </optgroup>
        ))}
      </select>
      {hint && (
        <span className="hint" id={`${id}-hint`}>
          {hint}
        </span>
      )}
    </>
  );
}

function Field({
  label,
  error,
  id,
  children,
}: {
  label: string;
  error?: string;
  id: string;
  children: ReactNode;
}) {
  return (
    <div className={`field${error ? " has-error" : ""}`}>
      <label htmlFor={id}>{label}</label>
      {children}
      {error && (
        <span className="field-error" id={`${id}-error`} role="alert">
          {error}
        </span>
      )}
    </div>
  );
}
