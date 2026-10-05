FROM gradle:9.1-jdk21 AS build
WORKDIR /workspace
COPY settings.gradle.kts build.gradle.kts ./
COPY src ./src
RUN gradle --no-daemon --console=plain installDist

FROM eclipse-temurin:21-jre-jammy
RUN apt-get update && apt-get install -y --no-install-recommends curl ca-certificates \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --system --uid 10001 --home-dir /app appuser
WORKDIR /app
COPY --from=build --chown=appuser:appuser /workspace/build/install/fsp-career-api/ ./
USER appuser
EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=3s --start-period=35s --retries=5 \
    CMD curl --fail --silent http://127.0.0.1:8080/health || exit 1
ENTRYPOINT ["/app/bin/fsp-career-api"]
