# CareerAI backend — container image for Render, Railway or any Docker host.
# Build: docker build -t careerai-backend .   (run from careerai-backend/)
# Runs with the "prod" profile: every setting comes from environment variables (docs/deployment-guide.md).

# ---------- Build stage: Java 21 + Maven ----------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
# Tests run locally / in CI (./mvnw test); the image build only packages.
RUN mvn -q -B package -DskipTests

# ---------- Runtime stage: Java 21 JRE only ----------
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN groupadd --system careerai && useradd --system --gid careerai --uid 10001 careerai
COPY --from=build /app/target/careerai-backend-*.jar /app/app.jar
USER careerai

# Heap = 65% of the container limit (leaves room for metaspace and threads on a 512 MB instance);
# exit cleanly on OOM so the platform restarts the service. Override JAVA_TOOL_OPTIONS on the host if needed.
ENV SPRING_PROFILES_ACTIVE=prod \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=65 -XX:+ExitOnOutOfMemoryError -XX:+UseSerialGC"

# The prod profile listens on $PORT (set by the platform), default 8080.
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
