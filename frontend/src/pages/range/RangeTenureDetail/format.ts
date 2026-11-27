const nf = new Intl.NumberFormat('en-CA');

/** A value, or an em dash when it is empty. */
export const dash = (v: string | number | null | undefined) =>
  v === null || v === undefined || v === '' ? '—' : v;

/** A number in the Canadian format, or an em dash. */
export const num = (v: number | null | undefined) =>
  v === null || v === undefined ? '—' : nf.format(v);

/** Y / N as Yes / No, or an em dash. */
export const yesNo = (v: string | null | undefined) => (v === 'Y' ? 'Yes' : v === 'N' ? 'No' : '—');

/** "CODE - Description", or the code alone when the description is unknown. */
export const codeDesc = (code: string | null, desc: string | null) =>
  code ? (desc ? `${code} - ${desc}` : code) : '—';

/** A range file status code's Carbon Tag colour. */
export function statusTagType(status: string | null): 'green' | 'blue' | 'gray' | 'red' {
  switch (status) {
    case 'PA':
    case 'PI':
    case 'PP':
    case 'PL':
      return 'blue';
    case 'CA':
    case 'RRS':
      return 'red';
    case 'EE':
    case 'D':
      return 'gray';
    default:
      return 'green';
  }
}
