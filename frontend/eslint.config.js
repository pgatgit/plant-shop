// ESLint flat config. Rules: docs/plan/10-code-quality-and-conventions.md §2 and §5.
import js from '@eslint/js';
import vitest from '@vitest/eslint-plugin';
import jsxA11y from 'eslint-plugin-jsx-a11y';
import reactHooks from 'eslint-plugin-react-hooks';
import reactRefresh from 'eslint-plugin-react-refresh';
import testingLibrary from 'eslint-plugin-testing-library';
import globals from 'globals';
import tseslint from 'typescript-eslint';

/** Feature folders must not import each other; shared code lives in components/lib/api. */
const featureBoundary = (feature, others) => ({
  files: [`src/features/${feature}/**/*.{ts,tsx}`],
  rules: {
    'no-restricted-imports': [
      'error',
      {
        patterns: others.map((other) => ({
          group: [
            `**/features/${other}`,
            `**/features/${other}/**`,
            `../${other}`,
            `../${other}/**`,
            `../../${other}`,
            `../../${other}/**`,
          ],
          message: `features/${feature} must not import from features/${other}.`,
        })),
      },
    ],
  },
});

export default tseslint.config(
  {
    ignores: [
      'dist',
      'coverage',
      'playwright-report',
      'test-results',
      'node_modules',
      'src/api/schema.d.ts',
    ],
  },
  js.configs.recommended,
  ...tseslint.configs.strictTypeChecked,
  ...tseslint.configs.stylisticTypeChecked,
  {
    languageOptions: {
      parserOptions: {
        projectService: true,
        tsconfigRootDir: import.meta.dirname,
      },
    },
  },
  {
    files: ['src/**/*.{ts,tsx}'],
    languageOptions: { globals: globals.browser },
    plugins: { 'react-refresh': reactRefresh },
    extends: [reactHooks.configs.flat.recommended, jsxA11y.flatConfigs.strict],
    rules: {
      'react-refresh/only-export-components': ['error', { allowConstantExport: true }],
      'no-restricted-syntax': [
        'error',
        {
          selector: "JSXAttribute[name.name='dangerouslySetInnerHTML']",
          message: 'dangerouslySetInnerHTML is banned (XSS would expose in-memory tokens).',
        },
      ],
      'no-restricted-globals': [
        'error',
        { name: 'localStorage', message: 'Tokens and app state must not be persisted.' },
        { name: 'sessionStorage', message: 'Only src/auth/pkce.ts may use sessionStorage.' },
      ],
      'no-restricted-properties': [
        'error',
        { object: 'window', property: 'localStorage', message: 'Not allowed.' },
        { object: 'window', property: 'sessionStorage', message: 'Only src/auth/pkce.ts.' },
      ],
    },
  },
  {
    // The PKCE transaction (state, verifier, returnTo) is the only browser-stored value.
    files: ['src/auth/pkce.ts'],
    rules: { 'no-restricted-globals': 'off', 'no-restricted-properties': 'off' },
  },
  featureBoundary('catalog', ['cart']),
  featureBoundary('cart', ['catalog']),
  {
    files: ['src/components/ui/**/*.{ts,tsx}'],
    rules: {
      'no-restricted-imports': [
        'error',
        {
          patterns: [{ group: ['**/features/**'], message: 'UI primitives are feature-agnostic.' }],
        },
      ],
    },
  },
  {
    files: ['src/**/*.test.{ts,tsx}', 'src/test/**/*.{ts,tsx}'],
    extends: [testingLibrary.configs['flat/react'], vitest.configs.recommended],
  },
  {
    files: ['*.config.{js,ts}', 'scripts/**/*.mjs', 'e2e/**/*.ts'],
    languageOptions: { globals: globals.node },
  },
  {
    files: ['**/*.{js,mjs}'],
    extends: [tseslint.configs.disableTypeChecked],
  },
);
