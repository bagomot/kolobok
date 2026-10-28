# Releasing

Each release puts two jars, one for NeoForge and one for Fabric, built from the same commit and tagged `v<version>`, into a GitHub release on [bagomot/kolobok](https://github.com/bagomot/kolobok/releases), and onto Modrinth and CurseForge once their projects are set up.

## Once

1. Create the project on Modrinth and on CurseForge, and put their project ids (shown on each project's page) into `stonecutter.properties.toml`: `publish.modrinth` and `publish.curseforge`. A platform left empty is skipped; with both empty, a release goes to GitHub only.
2. Get the tokens: a Modrinth personal access token with the "Create versions" scope, and a CurseForge API token from the account's API tokens page.
3. Keep the tokens out of the repository:
   - to publish from your machine, copy `.env.example` to `.env` (git-ignored) and fill it in, or set `MODRINTH_TOKEN` and `CURSEFORGE_TOKEN` in the environment;
   - to publish from GitHub, add the same two names as the repository's Actions secrets.

## Every release

1. Set `mod.version` in `stonecutter.properties.toml`.
2. Add a `## <version>` section at the top of `CHANGELOG.md`; it becomes the release notes everywhere.
3. Check what would be uploaded, without uploading anything and without tokens:

   ```sh
   ./gradlew publishMods -PdryRun
   ```

4. Commit, then tag the commit: `git tag -a v1.3.0 -m "The Tale of Kolobok 1.3.0"`.
5. Push the commit and the tag: `git push origin main v1.3.0`. The Release workflow builds and tests both jars, publishes them to Modrinth and CurseForge and makes a GitHub release with both jars and the changelog section as its notes; with `publish.type` beta or alpha the GitHub release is a pre-release. Publish from one place only: the pushed tag uploads to every platform with a project id, so a version also published from your machine with `./gradlew publishMods` would go up twice.

Before uploading, `publishMods` and the workflow's `checkRelease` build and test both jars and refuse to go on unless HEAD is tagged `v<mod.version>`, the checkout is clean and `CHANGELOG.md` has the version's section.

The Build workflow builds and tests every push to `main` and every pull request; the jars of each run are in its `kolobok-jars` artifact.

## Screenshots

The README's pictures come from a scripted client scene. To retake them, with Jade and JEI for the integration shots:

```sh
./gradlew :26.3-fabric:runClientGameTest -Pshowcase -Pcompat=jei,jade
```

The other scenes run as well. The pictures land in `media/screenshots/`. `media/icon.png` is the project icon for the platforms' pages.
