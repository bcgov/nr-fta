import type { AptCommitment } from '@/services/apt/commitments';

/** Total AAC, and the part of it counted towards the licences' AAC — APT2's two totals. */
export function commitmentTotals(rows: AptCommitment[]) {
  return rows.reduce(
    (t, c) => {
      const aac = c.allowableAnnualCut ?? 0;
      t.total += aac;
      if (c.includeInLicAac === 'Y') t.included += aac;
      return t;
    },
    { total: 0, included: 0 },
  );
}
