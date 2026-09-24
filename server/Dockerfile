# Builds the Robot Rampage dedicated server image (design.md 3.12), deployed
# via QNAP Container Station as a single-service Docker Compose "Application"
# (see deploy/docker-compose.yml). Migrated verbatim from the StarWars
# project's identical Dockerfile, renamed for this repo/app; the account-data
# bind mount StarWars has does not apply here (design.md 3.10: this server has
# no persistence yet, autosave is not implemented).
#
# Build context is the REPO ROOT, not server/ - the Maven reactor build
# needs the root pom.xml and core/ too, and jgitver (design.md 3.9) needs
# the real .git history to compute the image's version:
#
#   docker build -f server/Dockerfile -t robotrampage-server .

FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /build
# The whole repo, not just core/server/assets: Maven has to parse every
# module the root pom.xml declares (lwjgl3 included) to build its reactor
# graph before -pl/-am can even apply - a build context missing either of
# those directories fails at that parse step, not at the build step -pl
# would otherwise restrict to. .dockerignore keeps target/ etc out.
COPY . .
# -am so core is built fresh in this reactor, matching how the client
# release build works (README/CLAUDE.md) - no local .m2 install to rely on.
# -DskipTests: a version is only tagged after the full build and tests have
# passed locally, so running them again here adds nothing - and the
# real-socket server integration tests took over 20 minutes on GitHub's
# runners. A local `docker build` therefore does not test either.
#
# APP_VERSION (build-arg, plain "0.0.1" not "v0.0.1") overrides jgitver's own
# auto-detection via -Djgitver.use-version (design.md 3.9): jgitver reliably
# miscomputes "X.Y.(Z+1)-SNAPSHOT" for a GitHub Actions checkout even exactly
# on a tag in the StarWars project this was migrated from (confirmed on
# v0.0.2/v0.0.3 there) - a known class of jgitver+CI ref-ambiguity issue,
# root cause not fully pinned down. release-server.yml already knows the
# exact tag that triggered this build, so passing it through directly
# sidesteps the auto-detection bug rather than chasing it. Empty by default
# so a local `docker build` with no --build-arg still works (falls back to
# jgitver's own - correct - auto-detection outside CI).
ARG APP_VERSION=""
RUN if [ -n "$APP_VERSION" ]; then \
      mvn -pl server -am -DskipTests "-Djgitver.use-version=$APP_VERSION" package; \
    else \
      mvn -pl server -am -DskipTests package; \
    fi

# Not eclipse-temurin's -alpine variant: libGDX's native libraries are built
# against glibc, not Alpine's musl.
FROM eclipse-temurin:25-jre AS runtime
WORKDIR /app
COPY --from=build /build/server/target/RobotRampage-Server-*.jar /app/server.jar

# TCP only (design.md 3.5: turn-based, no UDP channel) - NetworkConstants.TCP_PORT.
# Documentation only here; actual publishing happens in deploy/docker-compose.yml.
# Deliberately different from the StarWars server's 45625/45626, so both can run
# on the same NAS at once (verified against StarWars' NetworkConstants directly).
EXPOSE 45725/tcp

# --enable-native-access=ALL-UNNAMED: same flag the README documents for
# running this jar directly - unlike lwjgl3's, server/pom.xml's shade
# config doesn't bake this into the manifest.
ENTRYPOINT ["java", "--enable-native-access=ALL-UNNAMED", "-jar", "/app/server.jar"]
