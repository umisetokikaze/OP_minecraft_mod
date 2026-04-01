# CI and Test Policy

## Pull request gate

Pull requests run the GitHub Actions workflow in two jobs:

- `quality`: runs `./gradlew test` and uploads JUnit/XML + HTML reports.
- `package`: runs `./gradlew assemble` after `quality` passes and uploads the built jar.

This keeps the PR gate explicit:

- Tests must pass before packaging runs.
- Packaging must succeed before a change is considered releasable.

## Gradle task roles

- `test`: runs the JUnit suite for pure Java logic. This is the minimum required PR gate.
- `check`: reserved as the aggregation point for future verification such as extra static analysis or validation tasks.
- `assemble`: produces the distributable jar without implying every future verification step.
- `build`: local or pre-release all-in-one verification that includes compilation, tests, and packaging.

## CI design notes

- The workflow uses `gradle/actions/setup-gradle` so Gradle dependencies, wrapper files, and configuration cache can be reused across runs.
- Job separation keeps future release automation straightforward because packaging is already isolated from the quality gate.
- Test reports are always uploaded so failing CI runs are diagnosable from GitHub Actions alone.

## Local commands

- Fast validation before opening a PR: `./gradlew test`
- Confirm packaging still works: `./gradlew assemble`
- Full local verification before larger changes: `./gradlew build`
