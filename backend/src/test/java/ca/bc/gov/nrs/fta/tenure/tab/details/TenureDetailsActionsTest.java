package ca.bc.gov.nrs.fta.tenure.tab.details;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.server.ResponseStatusException;

/** FTA100's Delete, Retire and Assign Mark Designate, through mainline, gated as legacy did. */
@DisplayName("Unit Test | TenureDetailsService actions")
class TenureDetailsActionsTest {

  private final TenureDetailsPackage pkg = mock(TenureDetailsPackage.class);
  private final TenureDetailsLookups lookups = mock(TenureDetailsLookups.class);
  private final TenureDetailsService service = new TenureDetailsService(pkg, lookups);

  private final Map<String, String> get = new HashMap<>();

  @BeforeEach
  void setUp() {
    get.put("p_forest_file_id", "W1234");
    get.put("p_file_type_code", "A04");
    get.put("p_file_status_st", "EE");
    get.put("p_pfu_revision_count", "3");
    get.put("p_disable_save_ind", "N");
    get.put("p_disable_delete_ind", "N");
    get.put("p_disable_retire_ind", "Y");
    get.put("p_disable_assign_mark_ind", "N");
    get.put("p_error_message", "");
    when(lookups.headquarters()).thenReturn(new String[] {"1", "HQ"});
    when(lookups.validLicenceToCutType(anyString())).thenReturn(false);
    when(pkg.call(anyMap())).thenAnswer(inv -> {
      Map<String, String> in = inv.getArgument(0);
      return "GET".equals(in.get("p_action")) ? new HashMap<>(get)
          : new HashMap<>(Map.of("p_error_message", ""));
    });
  }

  private static Map<String, String> revisions() {
    Map<String, String> r = new HashMap<>();
    TenureDetailsService.REVISION_PARAMS.forEach(p -> r.put(TenureDetailsForm.key(p), ""));
    r.put("pfuRevisionCount", "3");
    return r;
  }

  @SuppressWarnings("unchecked")
  private Map<String, String> lastCall() {
    ArgumentCaptor<Map<String, String>> in = ArgumentCaptor.forClass(Map.class);
    verify(pkg, atLeastOnce()).call(in.capture());
    List<Map<String, String>> calls = in.getAllValues();
    return calls.get(calls.size() - 1);
  }

  @Test
  void deleteRunsMainlineDelete() {
    service.delete("W1234", revisions(), "IDIR\\USER");

    Map<String, String> in = lastCall();
    assertThat(in.get("p_action")).isEqualTo("DELETE");
    assertThat(in.get("p_userid")).isEqualTo("IDIR\\USER");
    assertThat(in.get("p_pfu_revision_count")).isEqualTo("3");
  }

  @Test
  void deleteIsRefusedWhenThePackageDisablesIt() {
    get.put("p_disable_delete_ind", "Y");

    assertThatThrownBy(() -> service.delete("W1234", revisions(), "IDIR\\USER"))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("cannot be deleted");
  }

  @Test
  void aStaleFormIsRefused() {
    Map<String, String> stale = revisions();
    stale.put("pfuRevisionCount", "2");

    assertThatThrownBy(() -> service.delete("W1234", stale, "IDIR\\USER"))
        .hasMessageContaining("changed by someone else");
  }

  @Test
  void retireOnlyWhenThePackageSaysN() {
    assertThatThrownBy(() -> service.retire("W1234", revisions(), "IDIR\\USER"))
        .hasMessageContaining("cannot be retired");

    get.put("p_disable_retire_ind", "N");
    service.retire("W1234", revisions(), "IDIR\\USER");
    assertThat(lastCall().get("p_action")).isEqualTo("RETIRE");
  }

  @Test
  void aPackageErrorFailsTheAction() {
    get.put("p_disable_retire_ind", "N");
    when(pkg.call(anyMap())).thenAnswer(inv -> {
      Map<String, String> in = inv.getArgument(0);
      return "GET".equals(in.get("p_action")) ? new HashMap<>(get)
          : new HashMap<>(Map.of("p_error_message", "fta.web.error.user.custom.msg:All blocks"
              + " must be in LC - S - HX or EE status before this file can be spatially"
              + " retired."));
    });

    assertThatThrownBy(() -> service.retire("W1234", revisions(), "IDIR\\USER"))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("All blocks must be in LC");
  }

  @Test
  void assignMarkDesignateOnlyForLegacysFileTypes() {
    service.assignMarkDesignate("W1234", revisions(), "IDIR\\USER");
    assertThat(lastCall().get("p_action")).isEqualTo("ASSIGN_MARK_DESIGNATE");

    get.put("p_file_type_code", "A01");
    assertThatThrownBy(() -> service.assignMarkDesignate("W1234", revisions(), "IDIR\\USER"))
        .hasMessageContaining("cannot be assigned");
  }

  @Test
  void theGetCarriesTheButtonRules() {
    get.put("p_disable_retire_ind", "N");
    TenureDetailsRules rules = service.get("W1234").rules();
    assertThat(rules.deletable()).isTrue();
    assertThat(rules.retirable()).isTrue();
    assertThat(rules.assignsMarkDesignate()).isTrue();
  }
}
