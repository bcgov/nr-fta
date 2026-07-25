package ca.bc.gov.nrs.fta.tenure.dto;

/**
 * Request body for adjudicating a tenure application ({@code POST
 * /api/fta/applications/{esfId}/actions}). Fields mirror the write parameters of
 * the legacy {@code THE.FTA_302_ADJUDCOMMENT} mainline ({@code p_action},
 * {@code p_adjudication_comment}, {@code p_revision_count}); the tenure
 * application id ({@code p_tenure_app_id}) is taken from the path, and the audit
 * user id ({@code p_update_userid}) from the authenticated JWT.
 *
 * <p>{@code action} selects the mainline branch — {@code ADJUDICATION} (full
 * adjudication of the application) or {@code SAVE} (comment-only save). The
 * screen's adjudication buttons pass their own label so the acting decision is
 * recorded on the application. {@code revisionCount} carries the legacy
 * optimistic-lock guard; when {@code null} the guard is skipped.
 */
public record ApplicationAdjudicateRequest(
    String action, String adjudicationComment, Long revisionCount) {}
