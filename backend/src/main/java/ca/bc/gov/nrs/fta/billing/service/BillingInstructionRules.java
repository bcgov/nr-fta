package ca.bc.gov.nrs.fta.billing.service;

import java.util.Set;

/**
 * FTA680's billing-instruction logic, from {@code THE.FTA_680_TENURE_BILLING}, with
 * the database reads done by the caller. {@link #derive} is the GET's per-row
 * decision of what to show; {@link #edit} is EDIT's check and save decision for
 * one row on Prepare.
 *
 * <p>Legacy used a single space for "no instruction" and "no invoice"; here
 * that is the empty string.
 *
 * <p>One deliberate difference: legacy GET kept the account status in a variable
 * that was not reset between rows, so a row whose client account was not open
 * could inherit "OPN" from the row before it. Each row uses its own status here.
 */
final class BillingInstructionRules {

  static final String GBS = "GBS";
  static final String OPEN = "OPN";
  static final String CANCELLED = "CAN";
  static final String NOT_FOUND = "NOT FOUND";

  static final String GRAZING_NOT_FOUND = "Grazing AUM information not found for billing year.";
  static final String HAY_NOT_FOUND =
      "Hay Cutting Tenure information not found for billing year.";
  static final String ACCOUNT_NOT_OPEN = "Account not open for tenure client.";
  static final String NO_ACTIVE_INVOICE = "No active invoice. Valid Instructions are Y, YI, N.";
  static final String ACTIVE_INVOICE = "Active invoice exists. Valid Instructions are C, R, RI.";
  static final String ACTIVE_ASSURED =
      "Active invoice exists with issuance. Valid Instructions are C, R, RI.";
  static final String PREVIOUSLY_PREPARED =
      "Invoice previously prepared. Instructions not allowed.";

  private static final Set<String> NONE_OR_N = Set.of("N", "");
  private static final Set<String> NEW_BILL = Set.of("Y", "YI", "N", "");
  private static final Set<String> REBILL = Set.of("C", "R", "RI", "");

  private BillingInstructionRules() {}

  /**
   * What GET read for one tenure.
   *
   * @param clientFound      the tenure has a main ('A') client
   * @param latestInstr      the most recently certified instruction for the year
   * @param accountStatus    the main client's receivable account status
   * @param billedThroughGbs a GNRL_INVC_TXN row exists for the year
   * @param rbsInvoice       the highest-numbered RBS invoice for the year, or null
   * @param rbsRelationType  its FOREST_INVOICE.INVOICE_RLN_TYP_CD
   * @param rbsHasRlf        it has an RLF (issuance) detail line
   * @param effectiveYear    the year of the tenure's legal effective date
   */
  record RowFacts(
      boolean clientFound,
      String latestInstr,
      String accountStatus,
      boolean billedThroughGbs,
      String rbsInvoice,
      String rbsRelationType,
      boolean rbsHasRlf,
      String effectiveYear) {}

  /**
   * What GET shows for one tenure, and what EDIT later needs of it.
   *
   * @param instruction   the instruction shown
   * @param invoiceNumber the invoice shown: a number, {@link #GBS}, or empty
   * @param accountOpen   the main client's account is open
   * @param locked        the instruction cannot be changed
   */
  record RowState(String instruction, String invoiceNumber, boolean accountOpen, boolean locked) {}

  /** GET: the instruction and invoice shown for one tenure. */
  static RowState derive(RowFacts f, String calendarYear) {
    String instr;
    boolean accountOpen = false;
    if (!f.clientFound()) {
      instr = "N";
    } else {
      instr = blank(f.latestInstr());
      if (OPEN.equals(f.accountStatus())) {
        accountOpen = true;
      } else {
        instr = "N";
      }
    }

    String invoice = "";
    if (f.billedThroughGbs()) {
      invoice = GBS;
      instr = "";
    }

    if (accountOpen && !GBS.equals(invoice) && f.clientFound()) {
      if (f.rbsInvoice() == null) {
        if (isBOrBlank(instr)) {
          instr = "Y";
        }
      } else {
        invoice = f.rbsInvoice();
        boolean sameYear = calendarYear.equals(f.effectiveYear());
        boolean cancelled = CANCELLED.equals(f.rbsRelationType());
        if (!f.rbsHasRlf() && sameYear && !"N".equals(instr)) {
          if (cancelled) {
            invoice = "";
            instr = "N";
          } else if (isBOrBlank(instr)) {
            instr = "";
          }
        } else if (f.rbsHasRlf() || !sameYear) {
          if (cancelled) {
            invoice = "";
            instr = "N";
          } else if (isBOrBlank(instr)) {
            instr = "";
          }
        }
      }
    }

    boolean locked = GBS.equals(invoice) || !f.clientFound();
    return new RowState(instr, invoice, accountOpen, locked);
  }

  /**
   * What EDIT read for one tenure.
   *
   * @param hasProvision         a RANGE_PROVISION row exists for the year
   * @param accountStatus        the main client's receivable account status
   * @param firstRbsInvoice      the earliest-entered RBS invoice for the year, or null
   * @param firstRbsRelationType its FOREST_INVOICE.INVOICE_RLN_TYP_CD
   * @param firstRbsHasRlf       it has an RLF (issuance) detail line
   * @param previousInstr        the most recently updated instruction for the year
   */
  record EditFacts(
      boolean hasProvision,
      String accountStatus,
      String firstRbsInvoice,
      String firstRbsRelationType,
      boolean firstRbsHasRlf,
      String previousInstr) {}

  /**
   * EDIT's outcome for one row.
   *
   * @param error           why the instruction is refused, or null
   * @param insert          the instruction to record, or null for none
   * @param deleteAfterLastB remove the instructions certified after the last "B"
   */
  record Outcome(String error, String insert, boolean deleteAfterLastB) {
    static Outcome refused(String error) {
      return new Outcome(error, null, false);
    }
  }

  /** EDIT: whether the chosen instruction is allowed, and what to save. */
  static Outcome edit(RowState row, String fileTypeCode, String chosen, EditFacts f) {
    String newInstr = blank(chosen);
    String invoice = blank(row.invoiceNumber());
    String relationType = null;

    if (!GBS.equals(invoice)) {
      if (!NONE_OR_N.contains(newInstr) && !f.hasProvision()) {
        return Outcome.refused(
            fileTypeCode != null && fileTypeCode.startsWith("E")
                ? GRAZING_NOT_FOUND
                : HAY_NOT_FOUND);
      }
      boolean open = OPEN.equals(f.accountStatus());
      if (!NONE_OR_N.contains(newInstr) && !open) {
        return Outcome.refused(ACCOUNT_NOT_OPEN);
      }
      if (open && invoice.isEmpty() && !NEW_BILL.contains(newInstr)) {
        return Outcome.refused(NO_ACTIVE_INVOICE);
      }
      if (!invoice.isEmpty()) {
        relationType = blank(f.firstRbsRelationType());
        boolean cancelled = CANCELLED.equals(relationType);
        if (open && !cancelled && !f.firstRbsHasRlf() && !REBILL.contains(newInstr)) {
          return Outcome.refused(ACTIVE_INVOICE);
        }
        // Legacy checks the status GET showed here, not the one just read.
        if (row.accountOpen() && !cancelled && f.firstRbsHasRlf()
            && !REBILL.contains(newInstr)) {
          return Outcome.refused(ACTIVE_ASSURED);
        }
        if (open && cancelled && !NEW_BILL.contains(newInstr)) {
          return Outcome.refused(NO_ACTIVE_INVOICE);
        }
      }
    } else if (!newInstr.isEmpty()) {
      return Outcome.refused(PREVIOUSLY_PREPARED);
    }

    String previous = blank(f.previousInstr());
    String toInsert = "";
    if (!GBS.equals(invoice) && !previous.equals(newInstr)) {
      if ("B".equals(previous)) {
        if (!Set.of("Y", "YI", "").contains(newInstr) || CANCELLED.equals(relationType)) {
          toInsert = newInstr;
        }
      } else if (!invoice.isEmpty() && ("Y".equals(newInstr) || "YI".equals(newInstr))) {
        toInsert = "B";
      } else {
        toInsert = newInstr;
      }
    }

    if (!toInsert.isEmpty()) {
      return new Outcome(null, toInsert, false);
    }
    return new Outcome(null, null, !"B".equals(previous));
  }

  private static boolean isBOrBlank(String instr) {
    return "B".equals(instr) || instr.isEmpty();
  }

  static String blank(String s) {
    return s == null ? "" : s.trim();
  }
}
