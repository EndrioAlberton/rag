# ------------------------------------------------------------------------------
# Stage 1: Build frontend (Vue/Vite) — output goes to src/main/resources/META-INF/resources
# ------------------------------------------------------------------------------
FROM node:20-alpine AS frontend
WORKDIR /app
COPY . /app
WORKDIR /app/frontend
RUN npm ci && npm run build

# ------------------------------------------------------------------------------
# Stage 2: Build JVM (Maven + JDK)
# ------------------------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-21-alpine AS jvm-build
WORKDIR /build

COPY --from=frontend /app/pom.xml .
COPY --from=frontend /app/mvnw .
COPY --from=frontend /app/.mvn .mvn
COPY --from=frontend /app/src src

RUN mvn package -DskipITs=true -DskipFrontend=true

# ------------------------------------------------------------------------------
# Stage 3: Runtime (JRE)
# ------------------------------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine
WORKDIR /work

RUN adduser -D -u 1001 jvm && chown -R jvm:jvm /work

COPY --from=jvm-build --chown=jvm:jvm /build/target/quarkus-app/lib/ /work/lib/
COPY --from=jvm-build --chown=jvm:jvm /build/target/quarkus-app/*.jar /work/
COPY --from=jvm-build --chown=jvm:jvm /build/target/quarkus-app/app/ /work/app/
COPY --from=jvm-build --chown=jvm:jvm /build/target/quarkus-app/quarkus/ /work/quarkus/
COPY --from=frontend --chown=jvm:jvm /app/src/main/resources/rag /work/rag

ENV QUARKUS_PROFILE=prod
ENV RAG_LOCATION=/work/rag

EXPOSE 8080
USER jvm

ENTRYPOINT ["java", "-Dquarkus.http.host=0.0.0.0", "-Dquarkus.http.port=8080", "-jar", "/work/quarkus-run.jar"]
