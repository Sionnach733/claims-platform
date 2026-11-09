// A curated set of CPT and ICD-10 codes for the demo form. The API accepts any well-formed code; this list
// just makes the UI easy to drive. It deliberately covers every fraud-service signal: all priced CPT codes
// (PRICE_BENCHMARKS in fraud-service scoring.py), musculoskeletal procedures (CPT 20000-29999), and both
// related (M/S) and unrelated diagnoses for the procedure/diagnosis mismatch rule.

export interface MedicalCode {
  code: string;
  description: string;
  group: string;
}

export const CPT_CODES: readonly MedicalCode[] = [
  { group: "Office visits", code: "99202", description: "Office visit, new patient, straightforward" },
  { group: "Office visits", code: "99203", description: "Office visit, new patient, low complexity" },
  { group: "Office visits", code: "99213", description: "Office visit, established patient, low complexity" },
  {
    group: "Office visits",
    code: "99214",
    description: "Office visit, established patient, moderate complexity",
  },
  {
    group: "Office visits",
    code: "99215",
    description: "Office visit, established patient, high complexity",
  },
  { group: "Emergency", code: "99285", description: "Emergency department visit, high severity" },
  { group: "Preventive", code: "99395", description: "Preventive check-up, established patient, age 18-39" },
  { group: "Preventive", code: "90471", description: "Vaccine administration, single injection" },
  { group: "Lab", code: "36415", description: "Blood draw (venipuncture)" },
  { group: "Lab", code: "80053", description: "Comprehensive metabolic panel" },
  { group: "Lab", code: "85025", description: "Complete blood count (CBC) with differential" },
  { group: "Diagnostics", code: "93000", description: "Electrocardiogram (ECG) with interpretation" },
  { group: "Diagnostics", code: "71046", description: "Chest X-ray, 2 views" },
  { group: "Diagnostics", code: "73721", description: "MRI of knee, without contrast" },
  { group: "Therapy", code: "94640", description: "Nebulizer (inhaled medication) treatment" },
  { group: "Therapy", code: "97110", description: "Physical therapy, therapeutic exercise, 15 min" },
  { group: "Musculoskeletal", code: "20610", description: "Joint injection or aspiration, major joint" },
  { group: "Musculoskeletal", code: "29881", description: "Knee arthroscopy with meniscectomy" },
  { group: "Musculoskeletal", code: "27447", description: "Total knee replacement" },
];

export const ICD10_CODES: readonly MedicalCode[] = [
  { group: "Chronic conditions", code: "I10", description: "Essential (primary) hypertension" },
  { group: "Chronic conditions", code: "E11.9", description: "Type 2 diabetes without complications" },
  { group: "Chronic conditions", code: "E78.5", description: "High cholesterol (hyperlipidemia)" },
  { group: "Respiratory", code: "J45.909", description: "Asthma, uncomplicated" },
  { group: "Respiratory", code: "J06.9", description: "Acute upper respiratory infection" },
  { group: "Respiratory", code: "J18.9", description: "Pneumonia, unspecified organism" },
  { group: "Symptoms", code: "R07.9", description: "Chest pain, unspecified" },
  { group: "Symptoms", code: "R51.9", description: "Headache, unspecified" },
  { group: "Other conditions", code: "F41.1", description: "Generalized anxiety disorder" },
  { group: "Other conditions", code: "K21.9", description: "Acid reflux (GERD) without esophagitis" },
  { group: "Other conditions", code: "N39.0", description: "Urinary tract infection" },
  { group: "Preventive", code: "Z00.00", description: "Routine adult health check, no abnormal findings" },
  { group: "Preventive", code: "Z23", description: "Encounter for immunization" },
  { group: "Musculoskeletal and injury", code: "M17.11", description: "Osteoarthritis of right knee" },
  { group: "Musculoskeletal and injury", code: "M54.50", description: "Low back pain" },
  { group: "Musculoskeletal and injury", code: "M25.561", description: "Pain in right knee" },
  {
    group: "Musculoskeletal and injury",
    code: "S83.241A",
    description: "Torn meniscus, right knee, initial visit",
  },
  {
    group: "Musculoskeletal and injury",
    code: "S93.401A",
    description: "Sprained right ankle, initial visit",
  },
];

export function groupCodes(codes: readonly MedicalCode[]): [string, MedicalCode[]][] {
  const groups = new Map<string, MedicalCode[]>();
  for (const c of codes) groups.set(c.group, [...(groups.get(c.group) ?? []), c]);
  return [...groups];
}

const byCode = (codes: readonly MedicalCode[]) => new Map(codes.map((c) => [c.code, c.description]));
const cptIndex = byCode(CPT_CODES);
const icdIndex = byCode(ICD10_CODES);

export const describeCpt = (code: string): string | undefined => cptIndex.get(code);
export const describeIcd = (code: string): string | undefined => icdIndex.get(code.toUpperCase());
