/**
 * User-facing error sanitisation.
 *
 * Backend/API failures can carry raw technical text — Oracle errors
 * (ORA-…), SQL, Java stack traces, Spring's default error JSON, etc. None
 * of that should ever reach a toast or an inline error message. These
 * helpers gate what the UI is allowed to show: short, clean sentences pass
 * through (e.g. a validation message like "Summary of changes is
 * required."), anything technical is swapped for a friendly fallback.
 */

/** Default friendly message when we can't show the original safely. */
export const GENERIC_ERROR_MESSAGE = 'Please try again later.';

// Markers that identify a string as raw/technical rather than a message
// meant for a human.
const TECHNICAL_MARKERS: readonly RegExp[] = [
  /ORA-\d/i, // Oracle error codes
  /\bSQL(?:State|Exception|Data|Syntax)?\b/i,
  /\bException\b/,
  /\bat [\w$.]+\([\w.]+:\d+\)/, // Java stack frame: at pkg.Class(File.java:12)
  /\bat (?:ca\.bc|org\.springframework|java\.|jakarta\.|oracle\.|io\.undertow)/,
  /value larger than specified precision/i,
  /nested exception|Caused by:/i,
  /\bBEGIN\b[\s\S]*\bEND\b/i, // PL/SQL call block
  /"(?:timestamp|status|error|path|trace)"\s*:/, // Spring default error JSON
  /server_http_/,
  /JdbcTemplate|Undertow|Servlet|DataIntegrityViolation|DataAccess/i,
  // Spring request-plumbing phrases — technical, never meant for a user.
  /malformed json/i,
  /json parse error/i,
  /required request (?:body|parameter)/i,
  /failed to read (?:request|http message)/i,
  /HttpMessage(?:NotReadable|NotWritable|Conversion)/i,
  /MethodArgument(?:NotValid|TypeMismatch)/i,
  /could not (?:read|parse|resolve)/i,
  // A Java exception's class name (java.lang.IllegalStateException, ...OutOfMemoryError).
  /\b(?:java|javax|jakarta|org|ca|com|oracle)\.[\w$.]+(?:Exception|Error)\b/,
  // Raw SQL. Upper case only, so "Select a district from the list" is not caught.
  /\b(?:SELECT\b[\s\S]*\bFROM|INSERT INTO|DELETE FROM|UPDATE\s+[\w.]+\s+SET)\b/,
  // An HTML error page (e.g. a proxy's 502), which is multi-line but has no other marker.
  /<(?:!doctype|html|head|body|title|h1|pre)\b/i,
];

/** Longest line a human-readable message may have; longer is a serialized body or dump. */
const MAX_LINE_LENGTH = 240;

/**
 * True when `text` looks like a raw/technical error dump rather than a
 * human-readable message: it matches a technical marker anywhere, or any one
 * of its lines is very long (a serialized body). A message of several short,
 * clean lines — e.g. a refused Prepare listing one refusal per row — is not
 * technical; a stack trace still is, through its markers ("Exception",
 * "at pkg.Class(File.java:12)", "Caused by:").
 */
export function isTechnicalError(text: string): boolean {
  const t = text.trim();
  if (!t) return false;
  if (TECHNICAL_MARKERS.some((re) => re.test(t))) return true;
  return t.split(/\r\n|\r|\n/).some((line) => line.trim().length > MAX_LINE_LENGTH);
}

/**
 * Convert any caught value (Error, string, unknown) into a message that
 * is safe to show a user. Clean short messages are returned as-is;
 * empty/technical input yields `fallback`.
 */
export function safeErrorMessage(input: unknown, fallback: string = GENERIC_ERROR_MESSAGE): string {
  const raw =
    typeof input === 'string'
      ? input
      : input instanceof Error
        ? input.message
        : input == null
          ? ''
          : String(input);
  const t = raw.trim();
  if (!t || isTechnicalError(t)) return fallback;
  return t;
}
