package ca.bc.gov.nrs.fta.tenure.tab.saleinfo;

import static org.assertj.core.api.Assertions.assertThat;

import ca.bc.gov.nrs.fta.tenure.tab.saleinfo.SaleInfoDtos.SaleInfoUpdateRequest;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Pins the FTA940 gating, protection states and field checks: the page opens its fields by
 * these rules and the save enforces them, so a regression here lets a write through that
 * legacy refused.
 */
@DisplayName("Unit Test | SaleInfoRules and SaleInfoFieldChecks")
class SaleInfoRulesTest {

  private static SaleInfoRules.Facts facts(String id, String type, String status, String payment) {
    return new SaleInfoRules.Facts(
        id, type, status, true, payment, "N", null, "N", null, false, false, true);
  }

  private static SaleInfoRules rules(String type, String status) {
    return SaleInfoRules.of(facts("A12345", type, status, "A"));
  }

  /** A record that passes everything for an A01 in HI. */
  private static SaleInfoUpdateRequest base() {
    return new SaleInfoUpdateRequest(
        null, null, "N", "N", "N", null, null, null, null, null, null, 0, null, null, null,
        null, null, null, null, null, 1L, 1L, null);
  }

  private static SaleInfoUpdateRequest with(
      SaleInfoUpdateRequest b, String method, String saleType, String salvage,
      BigDecimal scrtyAmt, String scrtyCode, BigDecimal saleVolume) {
    return new SaleInfoUpdateRequest(
        method, saleType, salvage, b.minorFacilityInd(), b.adminAreaInd(), b.adminAreaFile(),
        b.plannedSaleDate(), b.tenderOpeningDate(), b.cashSaleEstVol(), b.cashSaleTotDol(),
        saleVolume, b.totalBidders(), b.ftaBonusBid(), b.ftaBonusOffer(),
        b.plannedSbCatCode(), b.soldSbCatCode(), scrtyCode, scrtyAmt, b.otherDepositCode(),
        b.otherDepositAmt(), b.hsRevisionCount(), b.tdRevisionCount(), b.auRevisionCount());
  }

  @Nested
  @DisplayName("rules")
  class Rules {

    @Test
    void peFilesCannotBeSaved() {
      SaleInfoRules r = rules("A01", "PE");
      assertThat(r.editable()).isFalse();
      assertThat(r.reason()).isEqualTo(SaleInfoRules.PE_MESSAGE);
    }

    @Test
    void invalidFileTypes() {
      for (String type : new String[] {"E01", "H01", "F01", "S01"}) {
        assertThat(rules(type, "HI").reason())
            .isEqualTo("The File Type (" + type + ") associated with the queried File is"
                + " invalid for this screen.");
      }
      assertThat(SaleInfoRules.of(new SaleInfoRules.Facts(
          "X", "B15", "HI", true, null, null, null, null, null, false, true, true)).editable())
          .isFalse();
    }

    @Test
    void headquartersNeedsFileAuthority() {
      SaleInfoRules r = SaleInfoRules.of(new SaleInfoRules.Facts(
          "X", "A01", "HI", true, null, null, null, null, null, false, false, false));
      assertThat(r.editable()).isFalse();
      assertThat(r.reason()).contains("A01");
    }

    @Test
    void saleMethodMandatory() {
      assertThat(rules("A20", "HI").saleMethodMandatory()).isTrue();
      assertThat(rules("A01", "HI").saleMethodMandatory()).isFalse();
      SaleInfoRules.Facts a03 = new SaleInfoRules.Facts(
          "X", "A03", "HI", true, null, null, null, null, new BigDecimal("9999"), false, false,
          true);
      assertThat(SaleInfoRules.of(a03).saleMethodMandatory()).isTrue();
      SaleInfoRules.Facts big = new SaleInfoRules.Facts(
          "X", "A03", "HI", true, null, null, null, null, new BigDecimal("10000"), false, false,
          true);
      assertThat(SaleInfoRules.of(big).saleMethodMandatory()).isFalse();
    }

    @Test
    void protectionStates() {
      assertThat(rules("A31", "HI").salvage()).isFalse();
      assertThat(rules("A04", "HI").minorFacility()).isTrue();
      assertThat(rules("A01", "HI").minorFacility()).isFalse();
      assertThat(rules("A01", "PP").withinAdminArea()).isTrue();
      assertThat(rules("A01", "HI").withinAdminArea()).isFalse();
      assertThat(rules("A20", "HI").bcts()).isTrue();
      assertThat(SaleInfoRules.of(facts("X", "B21", "PI", "C")).cashSale()).isTrue();
      assertThat(SaleInfoRules.of(facts("X", "B21", "PI", "C")).cashFieldsRequired()).isTrue();
      assertThat(SaleInfoRules.of(facts("X", "A01", "HI", "C")).cashSale()).isFalse();
      assertThat(SaleInfoRules.of(facts("D1", "A20", "HI", "A")).tenderRequired()).isTrue();
      assertThat(rules("B20", "HI").saleMethodAllowed()).containsExactly("A", "T");
    }

    @Test
    void newRecordsOfSomeTypesDefaultToD() {
      SaleInfoRules.Facts f = new SaleInfoRules.Facts(
          "X", "A41", "HI", false, null, null, null, null, null, false, false, true);
      assertThat(SaleInfoRules.of(f).defaultSaleMethodCode()).isEqualTo("D");
      assertThat(rules("A41", "HI").defaultSaleMethodCode()).isNull();
    }
  }

  @Nested
  @DisplayName("field checks")
  class Checks {

    private SaleInfoFieldChecks.Result check(
        SaleInfoUpdateRequest v, String type, String status, String purpose, String payment) {
      return SaleInfoFieldChecks.check(
          v, SaleInfoRules.of(facts("A1", type, status, payment)), type, status, purpose,
          payment, false);
    }

    @Test
    void aPlainRecordPasses() {
      assertThat(check(base(), "A01", "HI", null, "A").errors()).isEmpty();
    }

    @Test
    void depositsPair() {
      SaleInfoUpdateRequest v = with(base(), null, null, "N", null, "CA", null);
      assertThat(check(v, "A01", "HI", null, "A").errors())
          .containsExactly("Security Deposit Amount must not be blank when Security Deposit"
              + " Type is provided.");
      v = with(base(), null, null, "N", new BigDecimal("10"), null, null);
      assertThat(check(v, "A01", "HI", null, "A").errors())
          .containsExactly("Security Deposit Type must not be blank when Security Deposit"
              + " Amount is provided.");
      // An N… type needs no amount.
      v = with(base(), null, null, "N", null, "NIL", null);
      assertThat(check(v, "A01", "HI", null, "A").errors()).isEmpty();
    }

    @Test
    void decimals() {
      SaleInfoUpdateRequest v = with(base(), null, null, "N", new BigDecimal("1.234"), "CA",
          new BigDecimal("10.25"));
      assertThat(check(v, "A01", "HI", null, "A").errors())
          .containsExactly("Security Deposit Amount cannot contain more than 2 places of"
              + " decimal.", "Only one decimal-place is permitted for Sales Volume");
    }

    @Test
    void a29Rules() {
      SaleInfoUpdateRequest v = with(base(), "D", null, "N", null, null, null);
      assertThat(check(v, "A29", "HI", null, "A").errors())
          .containsExactly("Sale method code must be N for A29 file types",
              "Security Deposit Amount greater than 0 is mandatory for A29 file types");
    }

    @Test
    void directAwardFirstNations() {
      SaleInfoUpdateRequest v = with(base(), "N", null, "N", null, null, null);
      assertThat(check(v, "A01", "HI", null, "A").errors())
          .containsExactly("Sale method code - N - Direct Award First Nations only applies to"
              + " file types A41 A44 A18 A31 and B04");
      assertThat(check(v, "A41", "HI", null, "A").errors()).isEmpty();
    }

    @Test
    void smallScaleSalvage() {
      SaleInfoUpdateRequest v = with(base(), null, null, "N", null, null, new BigDecimal("2500"));
      assertThat(check(v, "B07", "HI", "SS", "A").errors())
          .containsExactly(
              "Sale type must be Conventional Application or Professional Application for"
                  + " Small Scale Salvage",
              "Salvage Ind must be Y for Small Scale Salvage",
              "Sales Volume cannot exceed 2000m3 for Small Scale Salvage");
      v = with(base(), null, "CA", "Y", null, null, new BigDecimal("100"));
      SaleInfoFieldChecks.Result ok = check(v, "B07", "HI", "SS", "A");
      assertThat(ok.errors()).isEmpty();
      assertThat(ok.salvageInd()).isEqualTo("Y");
    }

    @Test
    void cashSaleWarningDoesNotBlock() {
      SaleInfoUpdateRequest v = new SaleInfoUpdateRequest(
          null, null, "N", "N", "N", null, null, null, new BigDecimal("60"),
          new BigDecimal("5"), new BigDecimal("60"), 0, null, null, null, null, null, null,
          null, null, 1L, 1L, null);
      SaleInfoFieldChecks.Result r = check(v, "B07", "HI", "GT", "C");
      assertThat(r.warnings())
          .containsExactly("Warning: Sales Volume should not exceed 50m3 for cash sales");
      assertThat(r.errors())
          .containsExactly("Sales Volume cannot exceed 50m3 for Green Timber");
    }

    @Test
    void b07OutsidePendingNeedsVolumes() {
      assertThat(check(base(), "B07", "HI", null, "C").errors())
          .contains("Sales volume is mandatory for B07",
              "Estimated Sales volume is mandatory for B07 Cash sale",
              "Total Dollars Received is mandatory for B07 Cash sale");
    }

    @Test
    void saleMethodRequired() {
      assertThat(check(base(), "A20", "HI", null, "A").errors())
          .contains("Sale Method is required.");
    }

    @Test
    void adminAreaFile() {
      SaleInfoUpdateRequest y = new SaleInfoUpdateRequest(
          null, null, "N", "N", "Y", null, null, null, null, null, null, 0, null, null, null,
          null, null, null, null, null, 1L, 1L, null);
      assertThat(check(y, "A01", "PP", null, "A").errors())
          .containsExactly("Admin Area File is required.");
      assertThat(check(y, "A01", "HI", null, "A").errors()).isEmpty();
      SaleInfoUpdateRequest n = new SaleInfoUpdateRequest(
          null, null, "N", "N", "N", "C12345", null, null, null, null, null, 0, null, null,
          null, null, null, null, null, null, 1L, 1L, null);
      assertThat(check(n, "A01", "PP", null, "A").errors())
          .containsExactly("Admin Area File must be blank when Admin Area Ind is No.");
    }

    @Test
    void bctsRequireds() {
      SaleInfoRules r = SaleInfoRules.of(new SaleInfoRules.Facts(
          "DX", "A20", "HI", true, "A", "N", null, "N", null, true, false, true));
      SaleInfoUpdateRequest v = with(base(), "A", null, "N", null, null, null);
      assertThat(SaleInfoFieldChecks.check(v, r, "A20", "HI", null, "A", false).errors())
          .containsExactly("Planned BCTS Cat. is mandatory.", "Sold BCTS Cat. is mandatory.",
              "Planned Sale Date is mandatory.",
              "Tender Date is Mandatory when not Cash Sales and when in Harvesting Status.");
    }

    @Test
    void category3() {
      SaleInfoUpdateRequest v = new SaleInfoUpdateRequest(
          "T", null, "N", "N", "N", null, LocalDate.of(2020, 1, 1), null, null, null, null, 0,
          null, null, "3", "3", null, null, null, null, 1L, 1L, null);
      assertThat(check(v, "A23", "PI", null, "A").errors())
          .contains("Planned BCTS Cat. Code of 3 is not allowed for this file type.",
              "Sold BCTS Cat. Code of 3 is not allowed for this file type.");
    }
  }
}
