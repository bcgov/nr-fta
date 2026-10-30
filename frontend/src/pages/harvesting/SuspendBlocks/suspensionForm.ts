export interface SuspensionForm {
  partitionCode: string;
  orderNumber: string;
  startDate: string;
  endDate: string;
}

export const EMPTY_SUSPENSION: SuspensionForm = {
  partitionCode: '',
  orderNumber: '',
  startDate: '',
  endDate: '',
};

/** Whether every field a suspension requires holds something usable. */
export const suspensionComplete = (f: SuspensionForm) =>
  !!f.partitionCode && /^\d{1,10}$/.test(f.orderNumber.trim()) && !!f.startDate && !!f.endDate;
