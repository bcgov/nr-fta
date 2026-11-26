package ca.bc.gov.nrs.fta.tenure.tab.details;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.fta.tenure.tab.details.ChangeMarkDesignateService.MarkDesignate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.web.server.ResponseStatusException;

/**
 * FTA101 Change Mark Designate: when FTA100 offers it, and Go / Save Designate through
 * FTA_101_CHANGE_MARK_DESIG.mainline with legacy's messages.
 */
@DisplayName("Unit Test | FTA101 Change Mark Designate")
class ChangeMarkDesignateTest {

  private final TenureDetailsPackage fta100 = mock(TenureDetailsPackage.class);
  private final TenureDetailsLookups lookups = mock(TenureDetailsLookups.class);
  private final ChangeMarkDesignatePackage fta101 = mock(ChangeMarkDesignatePackage.class);
  private final TenureDetailsService details = new TenureDetailsService(fta100, lookups);
  private final ChangeMarkDesignateService service =
      new ChangeMarkDesignateService(fta101, details);

  /** FTA_100_TENURE's GET of the target. */
  private final Map<String, String> tenure = new HashMap<>();
  /** FTA_101's GET and SAVE answers. */
  private final Map<String, String> fta101Get = new HashMap<>();
  private String fta101SaveMessage = "";

  @BeforeEach
  void setUp() {
    tenure.put("p_forest_file_id", "A12345");
    tenure.put("p_file_type_code", "A01");
    tenure.put("p_file_status_st", "HI");
    tenure.put("p_pfu_revision_count", "3");
    tenure.put("p_disable_save_ind", "N");
    tenure.put("p_error_message", "");
    when(lookups.headquarters()).thenReturn(new String[] {"1", "HQ"});
    when(lookups.validLicenceToCutType(anyString())).thenReturn(false);
    when(fta100.call(anyMap())).thenAnswer(inv -> new HashMap<>(tenure));

    fta101Get.put("p_mark_designate", "AB");
    fta101Get.put("p_revision_count", "7");
    fta101Get.put("p_error_message", "");
    when(fta101.call(anyMap())).thenAnswer(inv -> {
      Map<String, String> in = inv.getArgument(0);
      if ("GET".equals(in.get("p_action"))) {
        Map<String, String> out = new HashMap<>(fta101Get);
        out.put("p_forest_file_id_src", in.get("p_forest_file_id_src"));
        return out;
      }
      return new HashMap<>(Map.of("p_error_message", fta101SaveMessage));
    });
  }

  @SuppressWarnings("unchecked")
  private List<Map<String, String>> fta101Calls() {
    ArgumentCaptor<Map<String, String>> in = ArgumentCaptor.forClass(Map.class);
    verify(fta101, atLeastOnce()).call(in.capture());
    return in.getAllValues();
  }

  // ─── When FTA100 offers it ─────────────────────────────────────────────────

  @ParameterizedTest
  @ValueSource(strings = {"A01", "A02", "A03", "A18", "A25", "A26", "A41", "U2"})
  void offeredForLegacysFileTypes(String type) {
    assertThat(ChangeMarkDesignateRules.changesMarkDesignate(type, false)).isTrue();
  }

  @ParameterizedTest
  @ValueSource(strings = {"A04", "A06", "A44", "A20", "B07", "U02", ""})
  void notOfferedForOtherFileTypes(String type) {
    assertThat(ChangeMarkDesignateRules.changesMarkDesignate(type, false)).isFalse();
  }

  @Test
  void notOfferedWhenSaveIsDisabled() {
    assertThat(ChangeMarkDesignateRules.changesMarkDesignate("A01", true)).isFalse();
    assertThat(ChangeMarkDesignateRules.changesMarkDesignate(null, false)).isFalse();
  }

  @Test
  void theDetailsRulesCarryTheFlag() {
    assertThat(details.get("A12345").rules().changesMarkDesignate()).isTrue();
    tenure.put("p_file_type_code", "A04");
    assertThat(details.get("A12345").rules().changesMarkDesignate()).isFalse();
  }

  // ─── Go ────────────────────────────────────────────────────────────────────

  @Test
  void blankSourceIsTheFileItself() {
    assertThat(ChangeMarkDesignateRules.sourceFileId("  ", "A12345")).isEqualTo("A12345");
    assertThat(ChangeMarkDesignateRules.sourceFileId(null, "A12345")).isEqualTo("A12345");
    assertThat(ChangeMarkDesignateRules.sourceFileId(" a999 ", "A12345")).isEqualTo("A999");
  }

  @Test
  void goShowsTheSourcesDesignate() {
    MarkDesignate shown = service.get("A12345", "a999");

    assertThat(shown.sourceFileId()).isEqualTo("A999");
    assertThat(shown.markDesignate()).isEqualTo("AB");
    assertThat(shown.revisionCount()).isEqualTo("7");
    Map<String, String> in = fta101Calls().get(0);
    assertThat(in.get("p_action")).isEqualTo("GET");
    assertThat(in.get("p_forest_file_id")).isEqualTo("A12345");
    assertThat(in.get("p_forest_file_id_src")).isEqualTo("A999");
  }

  @Test
  void goReportsAnUnknownFile() {
    fta101Get.put("p_error_message", "fta.web.error.user.custom.msg:Invalid file ID");

    assertThatThrownBy(() -> service.get("A12345", "NOPE"))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("400")
        .hasMessageContaining("Invalid file ID");
  }

  // ─── Save Designate ────────────────────────────────────────────────────────

  @Test
  void saveCopiesTheSourcesOwnDesignate() {
    service.save("A12345", "A999", "7", "IDIR\\USER");

    List<Map<String, String>> calls = fta101Calls();
    Map<String, String> save = calls.get(calls.size() - 1);
    assertThat(save.get("p_action")).isEqualTo("SAVE");
    assertThat(save.get("p_forest_file_id")).isEqualTo("A12345");
    assertThat(save.get("p_forest_file_id_src")).isEqualTo("A999");
    assertThat(save.get("p_mark_designate")).isEqualTo("AB");
    assertThat(save.get("p_update_userid")).isEqualTo("IDIR\\USER");
    assertThat(save.get("p_revision_count")).isEqualTo("7");
  }

  @Test
  void saveIsRefusedWhenFta100DoesNotOfferIt() {
    tenure.put("p_file_type_code", "A04");

    assertThatThrownBy(() -> service.save("A12345", "A999", "7", "IDIR\\USER"))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("409");
    verify(fta101, never()).call(anyMap());
  }

  @Test
  void saveReportsLegacysMismatchMessages() {
    fta101SaveMessage = "fta.web.error.user.custom.msg:The source file chosen does not match"
        + " regions with the target. Please choose a different source file;";

    assertThatThrownBy(() -> service.save("A12345", "A999", "7", "IDIR\\USER"))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("400")
        .hasMessageContaining("The source file chosen does not match regions with the target."
            + " Please choose a different source file");
  }

  @Test
  void saveReportsAStaleRevisionAsAConflict() {
    fta101SaveMessage = "results.web.usr.database.record.modified;";

    assertThatThrownBy(() -> service.save("A12345", "A999", "6", "IDIR\\USER"))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("409")
        .hasMessageContaining("changed by someone else");
  }

  @Test
  void outcomeSeparatesTheStaleKeyFromOtherMessages() {
    ChangeMarkDesignateRules.Outcome none = ChangeMarkDesignateRules.outcome("");
    assertThat(none.failed()).isFalse();

    ChangeMarkDesignateRules.Outcome stale =
        ChangeMarkDesignateRules.outcome("results.web.usr.database.record.modified;");
    assertThat(stale.modified()).isTrue();
    assertThat(stale.errors()).isEmpty();

    ChangeMarkDesignateRules.Outcome licensee = ChangeMarkDesignateRules.outcome(
        "fta.web.error.user.custom.msg:The source file chosen must match licensees with the"
            + " target. Please choose a different source file;");
    assertThat(licensee.modified()).isFalse();
    assertThat(licensee.errors()).containsExactly("The source file chosen must match licensees"
        + " with the target. Please choose a different source file");
  }
}
