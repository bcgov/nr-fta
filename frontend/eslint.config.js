import js from '@eslint/js';
import eslintConfigPrettier from 'eslint-config-prettier';
import importX from 'eslint-plugin-import-x';
import reactHooks from 'eslint-plugin-react-hooks';
import reactRefresh from 'eslint-plugin-react-refresh';
import globals from 'globals';
import tseslint from 'typescript-eslint';

/**
 * Flat ESLint config for the FTA frontend.
 *
 * Formatting is NOT enforced here. `eslint-config-prettier` (last in each
 * `extends`) switches off every stylistic rule that would fight Prettier, and
 * Prettier runs on its own via `npm run format` / `format:check`. Running it as
 * an ESLint rule instead is slower and reports formatting as lint errors, which
 * buries the real findings.
 *
 * Two `import-x/order` notes: it is the maintained fork of `eslint-plugin-import`
 * (the original has no ESLint 10 support), and the `@/**` alias is matched by
 * `pathGroups` rather than a resolver — nothing here needs to resolve a module
 * to disk, so that avoids pulling in a TypeScript resolver just to sort imports.
 */
export default tseslint.config([
  {
    ignores: [
      'dist/**',
      'coverage/**',
      'playwright-report/**',
      'test-results/**',
      'e2e/.auth/**',
      'public/**',
      'src/styles/bcgov/**',
    ],
  },

  // ── Application source ──────────────────────────────────────────────
  {
    files: ['src/**/*.{ts,tsx}'],
    extends: [
      js.configs.recommended,
      tseslint.configs.recommended,
      reactRefresh.configs.vite,
      eslintConfigPrettier,
    ],
    plugins: { 'react-hooks': reactHooks, 'import-x': importX },
    languageOptions: {
      ecmaVersion: 2022,
      sourceType: 'module',
      globals: globals.browser,
    },
    rules: {
      // The two classic hook rules, enabled by name rather than by extending
      // the plugin's recommended set. In eslint-plugin-react-hooks v7 that set
      // also turns on the React Compiler suite (set-state-in-effect, purity,
      // immutability, …), which is a much larger opinion than this config was
      // written for: it rejects the `setLoading(true)` prologue that every
      // data-fetching effect in this app opens with. Worth adopting on purpose,
      // as its own change — not as a side effect of switching lint on.
      'react-hooks/rules-of-hooks': 'error',
      'react-hooks/exhaustive-deps': 'warn',
      // Underscore-prefixed args are the codebase's "deliberately unused" marker.
      '@typescript-eslint/no-unused-vars': ['error', { argsIgnorePattern: '^_' }],
      'import-x/order': [
        'warn',
        {
          'groups': [
            'builtin',
            'external',
            'internal',
            'parent',
            'sibling',
            'index',
            'object',
            'type',
          ],
          'pathGroups': [{ pattern: '@/**', group: 'internal', position: 'before' }],
          'pathGroupsExcludedImportTypes': ['builtin'],
          'newlines-between': 'always',
          'alphabetize': { order: 'asc', caseInsensitive: true },
        },
      ],
      // The few deliberate diagnostics carry an eslint-disable-next-line.
      'no-console': 'warn',
      'no-debugger': 'warn',
    },
  },

  // ── The route table ────────────────────────────────────────────────
  // App.tsx imports ~40 page components grouped by business area under
  // comment headers (Core pages, Search, Tenures, Harvesting, Admin, …) that
  // mirror the route table right below them. Alphabetising the list would
  // scatter those groups and strand the comments, making the file harder to
  // read to satisfy a rule whose only purpose is readability.
  {
    files: ['src/App.tsx'],
    rules: { 'import-x/order': 'off' },
  },

  // ── Playwright suite — Node, not a browser ─────────────────────────
  {
    files: ['e2e/**/*.ts'],
    extends: [js.configs.recommended, tseslint.configs.recommended, eslintConfigPrettier],
    plugins: { 'import-x': importX },
    languageOptions: {
      ecmaVersion: 2022,
      sourceType: 'module',
      globals: { ...globals.node, ...globals.browser },
    },
    rules: {
      '@typescript-eslint/no-unused-vars': ['error', { argsIgnorePattern: '^_' }],
      // Specs assert on shapes the API returns; `any` is not worth fighting here.
      '@typescript-eslint/no-explicit-any': 'off',
      'no-console': 'off',
    },
  },

  // ── Build/tooling config at the package root ───────────────────────
  {
    files: ['*.{ts,js,mjs}'],
    extends: [js.configs.recommended, tseslint.configs.recommended, eslintConfigPrettier],
    languageOptions: {
      ecmaVersion: 2022,
      sourceType: 'module',
      globals: globals.node,
    },
    rules: { 'no-console': 'off' },
  },
]);
