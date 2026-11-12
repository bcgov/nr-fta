package ca.bc.gov.nrs.fta.billing.service;

import static org.assertj.core.api.Assertions.assertThat;

import ca.bc.gov.nrs.fta.billing.service.BillingInstructionRules.EditFacts;
import ca.bc.gov.nrs.fta.billing.service.BillingInstructionRules.Outcome;
import ca.bc.gov.nrs.fta.billing.service.BillingInstructionRules.RowFacts;
import ca.bc.gov.nrs.fta.billing.service.BillingInstructionRules.RowState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Unit Test | BillingInstructionRules")
class BillingInstructionRulesTest {

  private static final String YEAR = "2025";

  private static RowFacts facts(
      boolean client, String latest, String acct, boolean gbs,
      String rbs, String rln, boolean rlf, String effYear) {
    return new RowFacts(client, latest, acct, gbs, rbs, rln, rlf, effYear);
  }

  @Test
  void noClientIsLockedAtN() {
    RowState s = BillingInstructionRules.derive(
        facts(false, "Y", "OPN", false, null, null, false, "2020"), YEAR);
    assertThat(s.instruction()).isEqualTo("N");
    assertThat(s.locked()).isTrue();
  }

  @Test
  void anAccountThatIsNotOpenIsN() {
    RowState s = BillingInstructionRules.derive(
        facts(true, "Y", "CLS", false, null, null, false, "2020"), YEAR);
    assertThat(s.instruction()).isEqualTo("N");
    assertThat(s.locked()).isFalse();
  }

  @Test
  void billedThroughGbsIsLockedAndBlank() {
    RowState s = BillingInstructionRules.derive(
        facts(true, "Y", "OPN", true, "123", null, false, "2020"), YEAR);
    assertThat(s.instruction()).isEmpty();
    assertThat(s.invoiceNumber()).isEqualTo("GBS");
    assertThat(s.locked()).isTrue();
  }

  @Test
  void notYetInvoicedDefaultsToY() {
    assertThat(BillingInstructionRules.derive(
        facts(true, null, "OPN", false, null, null, false, "2020"), YEAR).instruction())
        .isEqualTo("Y");
    assertThat(BillingInstructionRules.derive(
        facts(true, "B", "OPN", false, null, null, false, "2020"), YEAR).instruction())
        .isEqualTo("Y");
    assertThat(BillingInstructionRules.derive(
        facts(true, "C", "OPN", false, null, null, false, "2020"), YEAR).instruction())
        .isEqualTo("C");
  }

  @Test
  void invoicedShowsTheInvoiceWithNoInstruction() {
    RowState s = BillingInstructionRules.derive(
        facts(true, "B", "OPN", false, "9001", "ORG", true, "2020"), YEAR);
    assertThat(s.invoiceNumber()).isEqualTo("9001");
    assertThat(s.instruction()).isEmpty();
    assertThat(s.accountOpen()).isTrue();
  }

  @Test
  void aCancelledInvoiceIsHiddenAndN() {
    RowState s = BillingInstructionRules.derive(
        facts(true, "Y", "OPN", false, "9001", "CAN", false, YEAR), YEAR);
    assertThat(s.invoiceNumber()).isEmpty();
    assertThat(s.instruction()).isEqualTo("N");
  }

  private final RowState notInvoiced = new RowState("Y", "", true, false);
  private final RowState invoiced = new RowState("", "9001", true, false);

  private EditFacts open(String rbs, String rln, boolean rlf, String previous) {
    return new EditFacts(true, "OPN", rbs, rln, rlf, previous);
  }

  @Test
  void needsARangeProvisionToBill() {
    EditFacts f = new EditFacts(false, "OPN", null, null, false, null);
    assertThat(BillingInstructionRules.edit(notInvoiced, "E01", "Y", f).error())
        .isEqualTo(BillingInstructionRules.GRAZING_NOT_FOUND);
    assertThat(BillingInstructionRules.edit(notInvoiced, "H01", "Y", f).error())
        .isEqualTo(BillingInstructionRules.HAY_NOT_FOUND);
    assertThat(BillingInstructionRules.edit(notInvoiced, "E01", "N", f).error()).isNull();
  }

  @Test
  void needsAnOpenAccountToBill() {
    EditFacts f = new EditFacts(true, "CLS", null, null, false, null);
    assertThat(BillingInstructionRules.edit(notInvoiced, "E01", "Y", f).error())
        .isEqualTo(BillingInstructionRules.ACCOUNT_NOT_OPEN);
  }

  @Test
  void withNoInvoiceOnlyNewBillingInstructionsAreAllowed() {
    assertThat(BillingInstructionRules.edit(
        notInvoiced, "E01", "C", open(null, null, false, null)).error())
        .isEqualTo(BillingInstructionRules.NO_ACTIVE_INVOICE);
  }

  @Test
  void withAnActiveInvoiceOnlyRebillingInstructionsAreAllowed() {
    assertThat(BillingInstructionRules.edit(
        invoiced, "E01", "Y", open("9001", "ORG", false, null)).error())
        .isEqualTo(BillingInstructionRules.ACTIVE_INVOICE);
    assertThat(BillingInstructionRules.edit(
        invoiced, "E01", "Y", open("9001", "ORG", true, null)).error())
        .isEqualTo(BillingInstructionRules.ACTIVE_ASSURED);
    assertThat(BillingInstructionRules.edit(
        invoiced, "E01", "R", open("9001", "ORG", false, null)).insert())
        .isEqualTo("R");
  }

  @Test
  void gbsRowsTakeNoInstruction() {
    RowState gbs = new RowState("", "GBS", true, true);
    assertThat(BillingInstructionRules.edit(
        gbs, "E01", "Y", open(null, null, false, null)).error())
        .isEqualTo(BillingInstructionRules.PREVIOUSLY_PREPARED);
    Outcome o = BillingInstructionRules.edit(gbs, "E01", "", open(null, null, false, "Y"));
    assertThat(o.error()).isNull();
    assertThat(o.insert()).isNull();
  }

  @Test
  void aNewInstructionIsRecorded() {
    Outcome o = BillingInstructionRules.edit(
        notInvoiced, "E01", "Y", open(null, null, false, null));
    assertThat(o.insert()).isEqualTo("Y");
  }

  @Test
  void theSameInstructionAgainRecordsNothingButUndoesAnyAfterTheLastB() {
    Outcome o = BillingInstructionRules.edit(
        notInvoiced, "E01", "Y", open(null, null, false, "Y"));
    assertThat(o.insert()).isNull();
    assertThat(o.deleteAfterLastB()).isTrue();
  }

  @Test
  void billingAgainAfterBIsNotRecordedUnlessCancelled() {
    Outcome kept = BillingInstructionRules.edit(
        invoiced, "E01", "Y", open("9001", "CAN", false, "B"));
    assertThat(kept.insert()).isEqualTo("Y");
    Outcome none = BillingInstructionRules.edit(
        notInvoiced, "E01", "Y", open(null, null, false, "B"));
    assertThat(none.insert()).isNull();
    assertThat(none.deleteAfterLastB()).isFalse();
  }
}
