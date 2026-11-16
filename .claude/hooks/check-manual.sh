#!/usr/bin/env bash
# Stop hook: if source in an area with a user guide changed but its guide in
# docs/manual/ did not, send Claude back once to update the guide
# (update-manual skill) or to say why no user-visible behaviour changed.
# Never blocks twice in a row.
set -u

input=$(cat)

# Already sent back once this turn: let Claude stop (avoids a loop).
if printf '%s' "$input" | grep -Eq '"stop_hook_active"[[:space:]]*:[[:space:]]*true'; then
  exit 0
fi

cd "${CLAUDE_PROJECT_DIR:-.}" 2>/dev/null || exit 0
git rev-parse --is-inside-work-tree >/dev/null 2>&1 || exit 0

changed=$( { git diff --name-only HEAD 2>/dev/null; git ls-files --others --exclude-standard 2>/dev/null; } | sort -u )

# Source a user could notice. Tests and test resources are excluded.
src=$(printf '%s\n' "$changed" \
  | grep -E '^(frontend/src/|backend/src/main/)' \
  | grep -Ev '(\.test\.|\.spec\.|/__tests__/|/test/)' || true)
[ -n "$src" ] || exit 0

B='backend/src/main/java/ca/bc/gov/nrs/fta'
# Source → guide. Keep in step with section 2 of .claude/skills/update-manual/SKILL.md.
tm_src="^frontend/src/pages/(marks/|search/TimberMarkSearch/|admin/MarkTransfer/)|^$B/mark/"
ha_src="^frontend/src/pages/(harvesting/|search/HarvestingAuthoritySearch/|search/CutBlockSearch/|tenure/TenureDetail/(CuttingPermitsPanel|CutBlocksPanel|CpCbAmendmentsPanel)\.tsx)|^$B/tenure/.*([Cc]utting[Pp]ermit|[Cc]ut[Bb]lock|[Cc]utblock|[Hh]arvest|[Pp]ostponement|[Ss]uspen|[Pp]ermitCondition|AssignMark)"
apt_src="^frontend/src/(pages/apt/|services/apt/|routes/aptPaths\.ts)|^$B/apt/"
both_src="^frontend/src/(routes/(access|routePaths)\.ts|context/auth/types\.ts|components/Layout/HeaderPanelProfile/)|^$B/security/(ApiAuthorizationCustomizer|RoleConstants)\.java"

needs=""
guide_changed() { printf '%s\n' "$changed" | grep -Eqx "docs/manual/($1)\.md"; }
touches() { printf '%s\n' "$src" | grep -Eq "$1"; }

# Each area has a guide per role; ask when neither of the area's guides changed.
if { touches "$tm_src" || touches "$both_src"; } \
  && ! guide_changed 'timber-marks-headquarters|timber-marks-district'; then
  needs="$needs docs/manual/timber-marks-headquarters.md docs/manual/timber-marks-district.md"
fi
if { touches "$ha_src" || touches "$both_src"; } \
  && ! guide_changed 'harvest-authorities-senior|harvest-authorities-district'; then
  needs="$needs docs/manual/harvest-authorities-senior.md docs/manual/harvest-authorities-district.md"
fi

if { touches "$apt_src" || touches "$both_src"; } && ! guide_changed 'apportionment'; then
  needs="$needs docs/manual/apportionment.md"
fi

if [ -n "$needs" ]; then
  cat <<JSON
{"decision":"block","reason":"Source in an area with a user guide changed, but these guides did not:${needs}. Use the update-manual skill (.claude/skills/update-manual/SKILL.md) to update the affected sections, or state in one sentence why this change has no user-visible effect there (refactor, tests, build, logging, etc.)."}
JSON
fi
exit 0
