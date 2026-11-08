const money = new Intl.NumberFormat("en-US", { style: "currency", currency: "USD" });
const dateTime = new Intl.DateTimeFormat("en-US", { dateStyle: "medium", timeStyle: "short" });
const date = new Intl.DateTimeFormat("en-US", { dateStyle: "medium", timeZone: "UTC" });

export const formatMoney = (amount: number | null | undefined): string =>
  amount === null || amount === undefined ? "—" : money.format(amount);

export const formatDateTime = (iso: string): string => dateTime.format(new Date(iso));

/** Dates of service are calendar dates with no time zone: format them in UTC so they never shift a day. */
export const formatDate = (isoDate: string): string => date.format(new Date(`${isoDate}T00:00:00Z`));

export const humanStatus = (status: string): string =>
  status.charAt(0) + status.slice(1).toLowerCase().replace("_", " ");
