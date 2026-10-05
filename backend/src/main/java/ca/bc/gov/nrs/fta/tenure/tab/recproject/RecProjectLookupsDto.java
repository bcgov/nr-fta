package ca.bc.gov.nrs.fta.tenure.tab.recproject;

import ca.bc.gov.nrs.fta.shared.dto.CodeOptionDto;
import java.util.List;

/**
 * The dropdowns of the Rec project tab — legacy FTA701's code lists ({@code FTA_CODE_LISTS}'s
 * {@code GET_REC_*} and {@code GET_RECREATION_DISTRICT_CODE}), current codes only, as the legacy
 * CodesManager served them. {@code accessPairs} is {@code RECREATION_ACCESS_XREF}: which sub
 * types each access type allows (legacy's access type / sub type filter).
 */
public record RecProjectLookupsDto(
    List<CodeOptionDto> riskRatings,
    List<CodeOptionDto> features,
    List<CodeOptionDto> userDays,
    List<CodeOptionDto> controlAccess,
    List<CodeOptionDto> maintainStandards,
    List<CodeOptionDto> fees,
    List<CodeOptionDto> accessTypes,
    List<CodeOptionDto> subAccessTypes,
    List<AccessPair> accessPairs,
    List<CodeOptionDto> districts) {

  /** One allowed access type / sub type combination. */
  public record AccessPair(String accessCode, String subAccessCode) {}
}
