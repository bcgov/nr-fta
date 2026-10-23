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
      // A disallowed application: no mark, so nothing Headquarters keeps open either.
      MarkEditRules rules = hq(mark("DV", null, null, "00001", null));

      assertThat(rules.editable()).isFalse();
      assertThat(rules.reason()).isNull();
      assertThat(MarkEditRules.of(mark("DV", null, null, "00001", null), true, true)
          .editable()).isFalse();
    }

    @Test
    void headquartersKeepsMarkingAtAnUnsaveableStatus() {
      MarkEditRules rules = hq(mark("DV", "B01", "12A345", "00001", null));

      assertThat(rules.editable()).isTrue();
      assertThat(rules.marking()).isTrue();
      assertThat(rules.location()).isFalse();
      assertThat(rules.branch()).isFalse();
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
    void markingNeedsTheMark() {
      assertThat(hq(mark("PI", null, null, "00001", null)).marking()).isFalse();
    }

    @Test
    void markingIsAlwaysOpenOnceTheMarkHasThem() {
      for (String status : new String[] {"HN", "HI", "HX"}) {
        MarkDetailDto marked = mark(status, "B01", "12A345", "00001", null);
        assertThat(hq(marked).marking()).as("HQ at %s", status).isTrue();
        assertThat(MarkEditRules.of(marked, true, true).marking())
            .as("district at %s", status).isTrue();
      }
    }

    @Test
    void headquartersMayChangeACancelledMarksStatusWithoutAClient() {
      MarkDetailDto cancelled = mark("HX", "B01", "12A345", null, null);

      assertThat(hq(cancelled).status()).isTrue();
      assertThat(hq(cancelled).statusOptions()).containsExactly("HX", "HI", "PI", "DD", "DV");
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
    void outstandingAmendmentIsWorkedInsteadOfTerm() {
      MarkEditRules rules = hq(mark("HI", "B01", "12A345", "00001", "PI"));

      assertThat(rules.amendmentStatus()).isTrue();
      assertThat(rules.amendmentStatusOptions()).containsExactly("PI", "HN", "DV");
      assertThat(rules.term()).isFalse();
      // Headquarters may still change the status.
      assertThat(rules.status()).isTrue();
    }

    @Test
    void cancelledMarkWorksOnlyAnApprovedAmendment() {
      MarkEditRules approved = hq(mark("HX", "B01", "12A345", "00001", "HN"));
      assertThat(approved.amendmentStatus()).isTrue();
      assertThat(approved.amendmentStatusOptions()).containsExactly("PI", "HN", "DV");

      MarkEditRules pending = hq(mark("HX", "B01", "12A345", "00001", "PI"));
      assertThat(pending.amendmentStatus()).isFalse();
      assertThat(pending.amendmentStatusOptions()).isEmpty();
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
  @DisplayName("updating land index and clients")
  class Updates {

    private static final List<String> ALL =
        List.of("PA", "PI", "HN", "HI", "HX", "DV", "DD", "EE");

    @Test
    void headquartersUpdatesAtEveryStatus() {
      for (String status : ALL) {
        MarkEditRules rules = hq(mark(status, "B08", "E12345", "00001", null));
        assertThat(rules.landIndexUpdate()).as(status).isTrue();
        assertThat(rules.clientsUpdate()).as(status).isTrue();
      }
    }


    @Test
    void notForViewOnlyTypesOrViewers() {
      MarkEditRules viewOnly = hq(mark("HI", "B15", "E12345", "00001", null));
      assertThat(viewOnly.landIndexUpdate()).isFalse();
      assertThat(viewOnly.clientsUpdate()).isFalse();
      MarkEditRules viewer = MarkEditRules.of(mark("HI", "B08", "E12345", "00001", null), false);
      assertThat(viewer.landIndexUpdate()).isFalse();
      assertThat(viewer.clientsUpdate()).isFalse();
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

    @Test
    void headquartersDoesNotSubmit() {
      MarkEditRules rules = hq(mark("PA", null, null, "00001", null));
      assertThat(rules.submit()).isFalse();
      assertThat(rules.submitReason()).contains("Only a district");
    }

    @Test
    void viewersCannotSubmit() {
      MarkEditRules rules = MarkEditRules.of(mark("PA", null, null, "00001", null), false, true);
      assertThat(rules.submit()).isFalse();
      assertThat(rules.submitReason()).contains("Your role");
    }
  }

  /**
   * The district role may change the marking codes and a cancelled mark's status to HI, and
   * nothing else on the page — the business's rule, narrower than legacy's district level —
   * except its own PA application, which it completes and submits as legacy's district did.
   */
  @Nested
  @DisplayName("district")
  class District {

    private static final List<String> ALL =
        List.of("PA", "PI", "HN", "HI", "HX", "DV", "DD", "EE");

    private MarkEditRules district(MarkDetailDto mark) {
      return MarkEditRules.of(mark, true, true);
    }

    @Test
    void onlyTheMarkingCodesAreOpenOnAnIssuedMark() {
      MarkEditRules rules = district(mark("HI", "B01", "12A345", "00001", null));

      assertThat(rules.editable()).isTrue();
      assertThat(rules.marking()).isTrue();
      assertThat(rules.status()).isFalse();
      assertThat(rules.statusOptions()).containsExactly("HI");
      assertThat(rules.applicationDate()).isFalse();
      assertThat(rules.term()).isFalse();
      assertThat(rules.location()).isFalse();
      assertThat(rules.markType()).isFalse();
      assertThat(rules.branch()).isFalse();
      assertThat(rules.amendmentStatus()).isFalse();
    }

    @Test
    void aCancelledMarkMayGoBackToIssuedOnly() {
      MarkEditRules rules = district(mark("HX", "B01", "12A345", null, null));

      assertThat(rules.status()).isTrue();
      assertThat(rules.statusOptions()).containsExactly("HX", "HI");
      assertThat(rules.marking()).isTrue();
    }

    @Test
    void statusIsClosedEverywhereButHx() {
      for (String status : ALL) {
        MarkEditRules rules = district(mark(status, "B01", "12A345", "00001", null));
        assertThat(rules.status()).as(status).isEqualTo("HX".equals(status));
      }
    }

    @Test
    void aSubmittedApplicationHasNothingOpen() {
      MarkEditRules rules = district(mark("PI", null, null, "00001", null));

      assertThat(rules.editable()).isFalse();
      assertThat(rules.marking()).isFalse();
      assertThat(rules.location()).isFalse();
      assertThat(rules.clients()).isFalse();
      assertThat(rules.submit()).isFalse();
    }

    @Test
    void aPendingApplicationIsTheDistrictsToComplete() {
      // Legacy's district at PA (state1): the application is open, Status and branch aren't.
      MarkEditRules rules = district(mark("PA", null, null, null, null));

      assertThat(rules.editable()).isTrue();
      assertThat(rules.applicationDate()).isTrue();
      assertThat(rules.term()).isTrue();
      assertThat(rules.location()).isTrue();
      assertThat(rules.clients()).isTrue();
      assertThat(rules.clientsUpdate()).isTrue();
      assertThat(rules.status()).isFalse();
      assertThat(rules.statusOptions()).containsExactly("PA");
      assertThat(rules.branch()).isFalse();
      assertThat(rules.markType()).isFalse();
      assertThat(rules.marking()).isFalse();
      assertThat(rules.landIndex()).isFalse();
      assertThat(rules.amendments()).isFalse();
      assertThat(rules.notes()).isFalse();
    }

    @Test
    void aPendingApplicationIsSubmittedOnceItHasTheMainLicensee() {
      MarkEditRules without = district(mark("PA", null, null, null, null));
      assertThat(without.submit()).isFalse();
      assertThat(without.submitReason()).contains("main licensee");

      MarkEditRules with = district(mark("PA", null, null, "00001", null));
      assertThat(with.submit()).isTrue();
      assertThat(with.submitReason()).isNull();
    }

    @Test
    void outstandingAmendmentIsNotWorked() {
      MarkEditRules rules = district(mark("HI", "B01", "12A345", "00001", "PI"));

      assertThat(rules.amendmentStatus()).isFalse();
      assertThat(rules.amendmentStatusOptions()).isEmpty();
    }

    @Test
    void noOtherTabTakesChanges() {
      for (String status : ALL) {
        if ("PA".equals(status)) {
          continue; // the district's own application: see aPendingApplicationIsTheDistrictsToComplete
        }
        MarkEditRules rules = district(mark(status, "B08", "E12345", "00001", null));
        assertThat(rules.landIndex()).as(status).isFalse();
        assertThat(rules.landIndexUpdate()).as(status).isFalse();
        assertThat(rules.clients()).as(status).isFalse();
        assertThat(rules.clientsUpdate()).as(status).isFalse();
        assertThat(rules.notes()).as(status).isFalse();
      }
    }

    @Test
    void refusalsSayWhatADistrictMayChange() {
      MarkEditRules rules = district(mark("HI", "B08", "E12345", "00001", null));

      assertThat(rules.landIndexReason()).contains("marking requirements");
      assertThat(rules.clientsReason()).contains("marking requirements");
    }

    @Test
    void viewOnlyTypesStayClosed() {
      MarkEditRules rules = district(mark("HX", "B15", "E12345", "00001", null));

      assertThat(rules.editable()).isFalse();
      assertThat(rules.marking()).isFalse();
      assertThat(rules.status()).isFalse();
    }

    @Test
    void headquartersMayAddNotes() {
      assertThat(hq(mark("HI", "B08", "E12345", "00001", null)).notes()).isTrue();
      assertThat(MarkEditRules.of(mark("HI", "B08", "E12345", "00001", null), false).notes())
          .isFalse();
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
