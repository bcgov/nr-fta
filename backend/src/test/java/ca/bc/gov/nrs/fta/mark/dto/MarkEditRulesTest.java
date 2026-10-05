package ca.bc.gov.nrs.fta.mark.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Pins the FTA510 edit rules ported from legacy {@code Fta510PrivateMarkForm.setProtectionStates}
 * and {@code FTA_510_PRIVATE_MARK.GET}'s save gate. The detail page opens fields by these rules
 * and the update endpoint accepts fields by them, so a regression here silently lets a field
 * be written that legacy protected — or locks one it allowed.
 */
@DisplayName("Unit Test | MarkEditRules")
class MarkEditRulesTest {

  /** A mark with just the fields the rules read. */
  private static MarkDetailDto mark(
      String status, String fileType, String timberMark, String client, String amendStatus) {
    return new MarkDetailDto(
        timberMark, "150557", timberMark, fileType, status, null, null, null, null, null, 60,
        "1867", "DVA", client, client == null ? null : "00", "Holder", "S", "H", null, null,
        "PINCHI MINE RD", null, "Legal", null, null, null, null, null, null, "012-982-971",
        "U", "24", null, "E", null, "68", "071", null, 0, null, amendStatus, null, 1L,
        amendStatus == null ? null : 1L, true, List.of(), List.of(), List.of(), List.of(),
        null);
  }

  private static MarkEditRules hq(MarkDetailDto mark) {
    return MarkEditRules.of(mark, true);
  }

  @Nested
  @DisplayName("save gate")
  class SaveGate {

    @Test
    void viewerCannotEdit() {
      MarkEditRules rules = MarkEditRules.of(mark("HI", "B01", "12A345", "00001", null), false);

      assertThat(rules.editable()).isFalse();
      assertThat(rules.location()).isFalse();
    }

    @Test
    void dvIsReadOnly() {
      MarkEditRules rules = hq(mark("DV", "B01", "12A345", "00001", null));

      assertThat(rules.editable()).isFalse();
      assertThat(rules.reason()).isNull();
    }

    @Test
    void b15AndB16AreViewOnly() {
      assertThat(hq(mark("HI", "B15", "12A345", "00001", null)).editable()).isFalse();
      assertThat(hq(mark("HI", "B16", "12A345", "00001", null)).editable()).isFalse();
    }

    @Test
    void viewOnlyTypeDisablesEverythingWithoutSayingWhy() {
      MarkEditRules rules = hq(mark("HI", "B15", "12A345", "00001", null));

      assertThat(rules.reason()).isNull();
      assertThat(rules.landIndex()).isFalse();
      assertThat(rules.landIndexReason()).isNull();
      assertThat(rules.clients()).isFalse();
      assertThat(rules.clientsReason()).isNull();
      assertThat(rules.amendments()).isFalse();
      assertThat(rules.amendmentsReason()).isNull();
    }

    @Test
    void headquartersStatusesAreEditable() {
      for (String status : List.of("HI", "HX", "PI", "PA", "HN")) {
        assertThat(hq(mark(status, null, null, "00001", null)).editable())
            .as(status)
            .isTrue();
      }
    }
  }

  @Nested
  @DisplayName("field groups")
  class FieldGroups {

    @Test
    void issuedMarkWithClientOpensBranchStatusAndMarking() {
      MarkEditRules rules = hq(mark("HI", "B01", "12A345", "00001", null));

      assertThat(rules.location()).isTrue();
      assertThat(rules.term()).isTrue();
      assertThat(rules.branch()).isTrue();
      assertThat(rules.marking()).isTrue();
      assertThat(rules.status()).isTrue();
      assertThat(rules.statusOptions()).containsExactly("HI", "HX");
      // The application date and client open only for a mark with no client.
      assertThat(rules.applicationDate()).isFalse();
      assertThat(rules.amendmentStatus()).isFalse();
    }

    @Test
    void markingIsOnlyOpenOnceIssued() {
      assertThat(hq(mark("PI", null, null, "00001", null)).marking()).isFalse();
      assertThat(hq(mark("HX", "B01", "12A345", "00001", null)).marking()).isFalse();
    }

    @Test
    void markingIsOpenAtHnAndHiForHeadquartersAndDistrict() {
      for (String status : new String[] {"HN", "HI"}) {
        MarkDetailDto issued = mark(status, "B01", "12A345", "00001", null);
        assertThat(hq(issued).marking()).as("HQ at %s", status).isTrue();
        assertThat(MarkEditRules.of(issued, true, true).marking())
            .as("district at %s", status).isTrue();
      }
    }

    @Test
    void noClientLocksBranchAndStatusButOpensApplicationDate() {
      MarkEditRules rules = hq(mark("PA", null, null, null, null));

      assertThat(rules.branch()).isFalse();
      assertThat(rules.status()).isFalse();
      assertThat(rules.applicationDate()).isTrue();
      assertThat(rules.location()).isTrue();
    }

    @Test
    void outstandingAmendmentIsWorkedInsteadOfTermAndStatus() {
      MarkEditRules rules = hq(mark("HI", "B01", "12A345", "00001", "PI"));

      assertThat(rules.amendmentStatus()).isTrue();
      assertThat(rules.amendmentStatusOptions()).containsExactly("PI", "HN", "DV");
      assertThat(rules.term()).isFalse();
      assertThat(rules.status()).isFalse();
    }
  }

  @Nested
  @DisplayName("assign mark")
  class AssignMark {

    @Test
    void pendingApplicationWithClientAndNoTypeCanBeAssigned() {
      assertThat(hq(mark("PI", null, null, "00001", null)).markType()).isTrue();
      // Legacy shows Headquarters a PA application as PI.
      assertThat(hq(mark("PA", null, null, "00001", null)).markType()).isTrue();
    }

    @Test
    void noClientNoAssignment() {
      assertThat(hq(mark("PI", null, null, null, null)).markType()).isFalse();
    }

    @Test
    void aTypedOrIssuedMarkCannotBeAssignedAgain() {
      assertThat(hq(mark("PI", "B08", null, "00001", null)).markType()).isFalse();
      assertThat(hq(mark("HI", "B08", "E12345", "00001", null)).markType()).isFalse();
    }

    @Test
    void viewerCannotAssign() {
      assertThat(MarkEditRules.of(mark("PI", null, null, "00001", null), false).markType())
          .isFalse();
    }
  }

  @Nested
  @DisplayName("land index")
  class LandIndex {

    @Test
    void openAtMostStatusesIncludingOnesTheMarkSaveRefuses() {
      for (String status : List.of("PA", "PI", "HN", "HI", "EE")) {
        assertThat(hq(mark(status, "B08", "E12345", "00001", null)).landIndex())
            .as(status)
            .isTrue();
      }
    }

    @Test
    void lockedAtCancelledAndDisallowedStatuses() {
      for (String status : List.of("HX", "DV", "DD")) {
        MarkEditRules rules = hq(mark(status, "B08", "E12345", "00001", null));
        assertThat(rules.landIndex()).as(status).isFalse();
        assertThat(rules.landIndexReason()).as(status).contains(status);
      }
    }

    @Test
    void reasonIsNullWhenOpen() {
      assertThat(hq(mark("HI", "B08", "E12345", "00001", null)).landIndexReason()).isNull();
    }

    @Test
    void lockedForViewOnlyTypesAndForViewers() {
      assertThat(hq(mark("HI", "B15", "E12345", "00001", null)).landIndex()).isFalse();
      assertThat(MarkEditRules.of(mark("HI", "B08", "E12345", "00001", null), false).landIndex())
          .isFalse();
    }
  }

  @Nested
  @DisplayName("associated clients")
  class Clients {

    @Test
    void openAtIssuedAndPendingStatuses() {
      for (String status : List.of("HI", "PI", "PA")) {
        MarkEditRules rules = hq(mark(status, "B08", "E12345", "00001", null));
        assertThat(rules.clients()).as(status).isTrue();
        assertThat(rules.clientsReason()).as(status).isNull();
      }
    }

    @Test
    void lockedAtOtherStatusesWithAReason() {
      for (String status : List.of("HN", "HX", "DV", "DD", "EE")) {
        MarkEditRules rules = hq(mark(status, "B08", "E12345", "00001", null));
        assertThat(rules.clients()).as(status).isFalse();
        assertThat(rules.clientsReason()).as(status).contains("HI, PI or PA");
      }
    }

    @Test
    void lockedForViewOnlyTypesAndViewers() {
      MarkEditRules viewOnly = hq(mark("HI", "B16", "E12345", "00001", null));
      assertThat(viewOnly.clients()).isFalse();
      assertThat(viewOnly.clientsReason()).isNull();
      assertThat(MarkEditRules.of(mark("HI", "B08", "E12345", "00001", null), false).clients())
          .isFalse();
    }
  }

  @Nested
  @DisplayName("amendments")
  class Amendments {

    @Test
    void openOnAnIssuedMarkWithNoAmendmentOutstanding() {
      MarkEditRules rules = hq(mark("HI", "B08", "E12345", "00001", null));
      assertThat(rules.amendments()).isTrue();
      assertThat(rules.amendmentsReason()).isNull();
    }

    @Test
    void lockedWithoutAReasonWhileOneIsOutstanding() {
      for (String amend : List.of("PI", "HN")) {
        MarkEditRules rules = hq(mark("HI", "B08", "E12345", "00001", amend));
        assertThat(rules.amendments()).as(amend).isFalse();
        assertThat(rules.amendmentsReason()).as(amend).isNull();
      }
    }

    @Test
    void lockedUnlessIssued() {
      for (String status : List.of("PI", "PA", "HN", "HX", "DV", "DD", "EE")) {
        MarkEditRules rules = hq(mark(status, "B08", "E12345", "00001", null));
        assertThat(rules.amendments()).as(status).isFalse();
        assertThat(rules.amendmentsReason()).as(status).contains("HI (Issued)");
      }
    }

    @Test
    void lockedWithAReasonWhenTheMarkHasNoTimberMarkRecord() {
      MarkDetailDto m = mark("HI", "B08", "E12345", "00001", null);
      MarkDetailDto noRecord = new MarkDetailDto(
          m.timberMark(), m.certificate(), m.forestFileId(), m.fileTypeCode(),
          m.markStatusCode(), m.markStatusDate(), m.markApplicationDate(), m.markIssueDate(),
          m.markExpiryDate(), m.markCancelDate(), m.tenureTerm(), m.forestDistrict(),
          m.orgUnitCode(), m.clientNumber(), m.clientLocnCode(), m.clientName(),
          m.markingMethodCode(), m.markingInstrumentCode(), m.crownGrantedAcqDesc(),
          m.grantedAcqrdDate(), m.permitBlockLocn(), m.permitBlockArea(),
          m.proofOfCrownOrLegal(), m.markStatusDesc(), m.fileTypeDesc(), m.districtDesc(),
          m.regionDesc(), m.markingMethodDesc(), m.markingInstrumentDesc(),
          m.bcaaFolioNumber(), m.mgmtUnitTypeCode(), m.mgmtUnitId(), m.mgmtUnitDesc(),
          m.cascadeSplitCode(), m.cascadeSplitDesc(), m.mapReferenceReg(),
          m.mapReferenceComp(), m.markExtendDate(), m.markExtendCount(), m.markAmendDate(),
          m.outstandingAmendStatus(), m.outstandingAmendStatusDesc(), m.revisionCount(),
          m.amendRevisionCount(), false, m.landIndex(), m.clients(), m.amendments(),
          m.notes(), null);
      MarkEditRules rules = hq(noRecord);
      assertThat(rules.amendments()).isFalse();
      assertThat(rules.amendmentsReason()).contains("TIMBER_MARK record");
    }

    @Test
    void lockedForViewOnlyTypesAndViewers() {
      MarkEditRules viewOnly = hq(mark("HI", "B15", "E12345", "00001", null));
      assertThat(viewOnly.amendments()).isFalse();
      assertThat(viewOnly.amendmentsReason()).isNull();
      MarkEditRules viewer = MarkEditRules.of(mark("HI", "B08", "E12345", "00001", null), false);
      assertThat(viewer.amendments()).isFalse();
      assertThat(viewer.amendmentsReason()).contains("Your role");
    }
  }

  @Nested
  @DisplayName("submit to Headquarters")
  class Submit {

    private MarkEditRules district(MarkDetailDto mark) {
      return MarkEditRules.of(mark, true, true);
    }

    @Test
    void aDistrictSubmitsItsPaApplicationWithAClient() {
      MarkEditRules rules = district(mark("PA", null, null, "00001", null));
      assertThat(rules.submit()).isTrue();
      assertThat(rules.submitReason()).isNull();
    }

    @Test
    void headquartersDoesNotSubmit() {
      MarkEditRules rules = hq(mark("PA", null, null, "00001", null));
      assertThat(rules.submit()).isFalse();
      assertThat(rules.submitReason()).contains("Only a district");
    }

    @Test
    void onlyFromPa() {
      for (String status : List.of("PI", "HN", "HI", "HX", "DV")) {
        MarkEditRules rules = district(mark(status, null, null, "00001", null));
        assertThat(rules.submit()).as(status).isFalse();
        assertThat(rules.submitReason()).as(status).contains("PA status");
      }
    }

    @Test
    void needsAClient() {
      MarkEditRules rules = district(mark("PA", null, null, null, null));
      assertThat(rules.submit()).isFalse();
      assertThat(rules.submitReason()).contains("Associated clients");
    }

    @Test
    void viewersCannotSubmit() {
      MarkEditRules rules = MarkEditRules.of(mark("PA", null, null, "00001", null), false, true);
      assertThat(rules.submit()).isFalse();
      assertThat(rules.submitReason()).contains("Your role");
    }
  }

  @Nested
  @DisplayName("status transitions")
  class StatusTransitions {

    @Test
    void onlyTransitionsTheLegacySaveWritesAreOffered() {
      assertThat(hq(mark("PI", null, null, "00001", null)).statusOptions())
          .containsExactly("PI", "DV", "EE");
      assertThat(hq(mark("HX", "B01", "12A345", "00001", null)).statusOptions())
          .containsExactly("HX", "HI", "PI", "DD", "DV");
    }

    @Test
    void statusesWithNoTransitionKeepStatusClosed() {
      MarkEditRules pa = hq(mark("PA", null, null, "00001", null));

      assertThat(pa.status()).isFalse();
      assertThat(pa.statusOptions()).containsExactly("PA");
    }
  }
}
