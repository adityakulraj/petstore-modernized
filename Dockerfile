FROM mirror.gcr.io/library/maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml ./
RUN --mount=type=cache,target=/root/.m2 mvn -B -ntp dependency:go-offline
COPY src ./src
# The image build cannot access the host Docker socket, so Testcontainers belongs in CI/`mvn verify`, not BuildKit.
RUN --mount=type=cache,target=/root/.m2 mvn -B -ntp -DskipTests package

FROM mirror.gcr.io/library/eclipse-temurin:21-jre
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*
RUN groupadd --system petstore && useradd --system --gid petstore --home-dir /app petstore
WORKDIR /app
COPY --from=build /workspace/target/petstore-*.jar app.jar
RUN mkdir -p /app/logs && chown petstore:petstore /app/logs
USER petstore
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-Djava.security.egd=file:/dev/urandom", "-jar", "/app/app.jar"]
