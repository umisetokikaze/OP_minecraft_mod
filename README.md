# momo_optimizer

NeoForge-based Minecraft mod project for profiling and optimization-related tooling.

## Development

If dependencies look out of sync in the IDE, run:

- `./gradlew --refresh-dependencies`

To clear local build output, run:

- `./gradlew clean`

## CI and test policy

Pull requests are validated by GitHub Actions in two stages:

- `./gradlew test`
- `./gradlew assemble`

`test` is the minimum quality gate for PRs, while `assemble` confirms the mod jar can still be produced. `check` is kept as the future entry point for broader verification, and `build` remains the local/pre-release all-in-one command.

Detailed CI notes are documented in [docs/ci.md](docs/ci.md).

## Useful commands

- Run the JUnit suite: `./gradlew test`
- Build the mod jar: `./gradlew assemble`
- Run broader local verification: `./gradlew build`

## References

- Community Documentation: https://docs.neoforged.net/
- NeoForged Discord: https://discord.neoforged.net/
- Mojang mapping license reference: https://github.com/NeoForged/NeoForm/blob/main/Mojang.md
