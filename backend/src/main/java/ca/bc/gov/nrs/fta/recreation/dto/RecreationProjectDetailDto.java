package ca.bc.gov.nrs.fta.recreation.dto;

import ca.bc.gov.nrs.fta.recreation.dto.RecreationChildDtos.Access;
import ca.bc.gov.nrs.fta.recreation.dto.RecreationChildDtos.Attachment;
import ca.bc.gov.nrs.fta.recreation.dto.RecreationChildDtos.District;
import ca.bc.gov.nrs.fta.recreation.dto.RecreationChildDtos.Fee;
import ca.bc.gov.nrs.fta.recreation.dto.RecreationChildDtos.Permissions;
import ca.bc.gov.nrs.fta.recreation.dto.RecreationChildDtos.Tombstone;
import java.util.List;

/**
 * Everything the FTA701 screen needs for one recreation project, in one
 * response.
 *
 * <p>Legacy performs four separate package round-trips to assemble this screen
 * ({@code MAINLINE GET}, {@code PROJECT_ACCESS GET}, {@code DISTRICT_MAINLINE
 * GET}, {@code ATTACHMENT_MAINLINE GET}). One request is returned here instead:
 * the screen has no state in which it wants a subset, and four calls only
 * multiply the ways a half-loaded page can appear.
 *
 * <p>{@code project} is null when the file exists but has no
 * {@code RECREATION_PROJECT} row yet — a real state in legacy, which shows the
 * tombstone and prompts the user to save project details before adding fees or
 * access types. The tombstone and permissions are still present in that case.
 *
 * @param forestFileId the project's id, echoed for convenience
 * @param tombstone    the file header strip
 * @param project      the project fields, or null if not yet created
 * @param districts    the districts it is cross-referenced to
 * @param fees         its fees
 * @param access       its access routes
 * @param attachments  its establishment order documents
 * @param permissions  what the current user may change
 */
public record RecreationProjectDetailDto(
    String forestFileId,
    Tombstone tombstone,
    RecreationProjectDto project,
    List<District> districts,
    List<Fee> fees,
    List<Access> access,
    List<Attachment> attachments,
    Permissions permissions) {}
