# Investigation 08 — Should Code Formatting Be Enforced?

## Question

Should a code formatting tool be adopted to enforce consistent style across the codebase?

## Reason Investigated

Manual code formatting leads to inconsistent style, noisy diffs, and wasted time on formatting debates. Enforcing a standard formatter eliminates this overhead and ensures all contributors produce uniformly formatted code.

## Files Examined

- `pom.xml`
- `src/main/java/com/lms/**/*.java` (87 files)
- `src/test/java/com/lms/**/*.java` (3 files)
- `.github/workflows/ci.yml` (or equivalent CI config)

## Search Commands Used

```bash
grep -rn "spotless\|google-java-format\|formatter" pom.xml
```

```bash
find src -name "*.java" | wc -l
```

```bash
grep -rn "spotless:check\|spotless:apply" pom.xml .github/workflows/
```

## Evidence

- Google Java Format `1.25.2` selected as the standard.
- `spotless-maven-plugin` version `2.44.3` configured in `pom.xml`.
- **87 source files + 3 test files** were reformatted on initial adoption.
- CI check added: `mvn spotless:check` runs before build to enforce formatting.
- `mvn spotless:apply` can be used to auto-fix formatting locally.

## Findings

**Spotless with Google Java Format has been adopted.** All existing files have been reformatted. CI enforcement ensures no unformatted code is merged.

## Alternatives

| Alternative | Pros | Cons |
|---|---|---|
| Spotless + Google Java Format (chosen) | Industry standard, auto-formats | Opinionated style |
| Checkstyle | Highly configurable | More setup, doesn't auto-fix |
| EditorConfig | Minimal, editor-native | No enforcement, inconsistent |
| PMD + SpotBugs | Focus on bugs, not style | Different purpose |
| Manual formatting | No tooling | Inconsistent, time-consuming |

## Decision

Adopt Spotless with Google Java Format. Enforced in CI before build. All existing files reformatted.

## Verification

- `spotless-maven-plugin` confirmed in `pom.xml` with Google Java Format 1.25.2.
- 87 source + 3 test files reformatted.
- CI pipeline includes `mvn spotless:check` step.
- `mvn spotless:apply` works for local formatting.

## Remaining Uncertainty

- None. Formatting is enforced and automated.
