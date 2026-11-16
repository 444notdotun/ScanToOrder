import { defineConfig, globalIgnores } from "eslint/config";
import nextVitals from "eslint-config-next/core-web-vitals";
import nextTs from "eslint-config-next/typescript";

const eslintConfig = defineConfig([
  ...nextVitals,
  ...nextTs,
  // Staff pages from before the Plately rebuild. Each is removed (with its service) by the pull request that
  // replaces its screen, so loose typing is tolerated here until then. New code gets no such allowance.
  {
    files: [
      "src/app/admin/**",
      "src/app/waiter/**",
      "src/app/kitchen/**",
      "src/app/manager/**",
      "src/app/staff/**",
      "src/app/not-found.tsx",
      "src/components/ProtectedRoute.tsx",
      "src/services/**",
      "src/store/authStore.ts",
    ],
    rules: {
      "@typescript-eslint/no-explicit-any": "off",
      "@typescript-eslint/no-unused-vars": "off",
      "react/no-unescaped-entities": "off",
      "react-hooks/set-state-in-effect": "off",
      "react-hooks/purity": "off",
    },
  },
  // Override default ignores of eslint-config-next.
  globalIgnores([
    // Default ignores of eslint-config-next:
    ".next/**",
    "out/**",
    "build/**",
    "next-env.d.ts",
  ]),
]);

export default eslintConfig;
