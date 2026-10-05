package ca.bc.gov.nrs.fta.tenure.tab.rotations;

import static org.assertj.core.api.Assertions.assertThat;

import ca.bc.gov.nrs.fta.tenure.tab.rotations.RotationsRules.RotationKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Pins the rotation tabs' gates, ported from legacy FTA611/612/613's GET (file type, PE,
 * application in the Inbox). The page disables its buttons by these rules and every write
 * enforces them, so a regression here silently opens a tab legacy kept closed — or the reverse.
 */
@DisplayName("Unit Test | RotationsRules")
class RotationsRulesTest {

  private static RotationsTenure tenure(
      String type, String status, boolean grazing, boolean hay, boolean range, boolean inbox,
      boolean term) {
    return new RotationsTenure(
        type, status, 3L, term ? 2020 : null, term ? 2029 : null, grazing, hay, range, inbox);
  }

  private static RotationsTenure grazingFile(String status, boolean inbox) {
    return tenure("E01", status, true, false, true, inbox, true);
  }

  private static RotationsTenure hayFile(String status) {
    return tenure("H01", status, false, true, true, false, true);
  }

  @Nested
  @DisplayName("Grazing (FTA611)")
  class Grazing {

    @Test
    @DisplayName("an issued grazing file is open")
    void open() {
      assertThat(RotationsRules.grazing(grazingFile("HI", false)))
          .isEqualTo(new RotationsRules(true, true, null));
    }

    @Test
    @DisplayName("a file that is not a grazing type does not take the tab, and says its type")
    void notGrazing() {
      RotationsRules r = RotationsRules.grazing(hayFile("HI"));
      assertThat(r.applies()).isFalse();
      assertThat(r.edit()).isFalse();
      assertThat(r.reason()).contains("grazing").contains("H01");
    }

    @Test
    @DisplayName("PE closes saving with the form's message")
    void pe() {
      RotationsRules r = RotationsRules.grazing(grazingFile("PE", false));
      assertThat(r.applies()).isTrue();
      assertThat(r.edit()).isFalse();
      assertThat(r.reason()).isEqualTo(RotationsRules.STATUS_PE);
    }

    @Test
    @DisplayName("an application in the Inbox closes saving")
    void inbox() {
      RotationsRules r = RotationsRules.grazing(grazingFile("HI", true));
      assertThat(r.edit()).isFalse();
      assertThat(r.reason()).isEqualTo(RotationsRules.INBOX);
    }

    @Test
    @DisplayName("no term means no year to save to")
    void noTerm() {
      RotationsRules r =
          RotationsRules.grazing(tenure("E01", "HI", true, false, true, false, false));
      assertThat(r.edit()).isFalse();
      assertThat(r.reason()).isEqualTo(RotationsRules.NO_TERM);
    }
  }

  @Nested
  @DisplayName("Hay cutting (FTA612)")
  class HayCutting {

    @Test
    @DisplayName("an issued hay file is open")
    void open() {
      assertThat(RotationsRules.hayCutting(hayFile("HI")).edit()).isTrue();
    }

    @Test
    @DisplayName("a grazing file does not take the tab")
    void notHay() {
      assertThat(RotationsRules.hayCutting(grazingFile("HI", false)).applies()).isFalse();
    }

    @Test
    @DisplayName("legacy FTA612 does not check for an application in the Inbox")
    void inboxIgnored() {
      RotationsTenure t = new RotationsTenure("H02", "HI", 1L, 2020, 2029, false, true, true, true);
      assertThat(RotationsRules.hayCutting(t).edit()).isTrue();
    }

    @Test
    @DisplayName("PE closes saving")
    void pe() {
      assertThat(RotationsRules.hayCutting(hayFile("PE")).reason())
          .isEqualTo(RotationsRules.STATUS_PE);
    }

    @Test
    @DisplayName("a hay type that does not start with H shows but cannot be saved")
    void notH() {
      RotationsRules r =
          RotationsRules.hayCutting(tenure("X01", "HI", false, true, true, false, true));
      assertThat(r.applies()).isTrue();
      assertThat(r.edit()).isFalse();
    }
  }

  @Nested
  @DisplayName("Copy (FTA613)")
  class Copy {

    @Test
    @DisplayName("E01–E03 copy livestock rotations, H01–H03 meadow rotations")
    void kinds() {
      assertThat(RotationsRules.kind("E02")).isEqualTo(RotationKind.GRAZING);
      assertThat(RotationsRules.kind("H03")).isEqualTo(RotationKind.HAY);
      assertThat(RotationsRules.kind("A01")).isNull();
      assertThat(RotationsRules.kind(null)).isNull();
    }

    @Test
    @DisplayName("a range file of another type cannot be copied to")
    void otherRangeType() {
      RotationsRules r =
          RotationsRules.copy(tenure("E04", "HI", true, false, true, false, true));
      assertThat(r.applies()).isFalse();
      assertThat(r.reason()).contains("E04");
    }

    @Test
    @DisplayName("a type not in RANGE_FILE_TYPE_CODE cannot be copied to")
    void notRange() {
      assertThat(RotationsRules.copy(tenure("E01", "HI", true, false, false, false, true))
          .applies()).isFalse();
    }

    @Test
    @DisplayName("open for an issued grazing or hay file; PE closes it")
    void status() {
      assertThat(RotationsRules.copy(grazingFile("HI", true)).edit()).isTrue();
      assertThat(RotationsRules.copy(hayFile("HI")).edit()).isTrue();
      assertThat(RotationsRules.copy(hayFile("PE")).reason()).isEqualTo(RotationsRules.STATUS_PE);
    }
  }
}
