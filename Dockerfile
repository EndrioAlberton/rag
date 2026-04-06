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
FROM maven:3.9-eclipse-temurin-25-alpine AS jvm-build
WORKDIR /build

COPY --from=frontend /app/pom.xml ./
# Checkstyle (validate) e scripts auxiliares; não são necessários para mvn package, mas mantêm o layout igual ao repositório
COPY --from=frontend /app/config ./config
COPY --from=frontend /app/mvnw .
COPY --from=frontend /app/.mvn .mvn
COPY --from=frontend /app/src src

RUN mvn package -DskipTests=true -DskipITs=true -DskipFrontend=true

# ------------------------------------------------------------------------------
# Stage 3: Runtime (JRE)
# ------------------------------------------------------------------------------
# eclipse-temurin:25-jre-alpine uses musl libc which is missing glibc symbols
# (e.g. __res_init) required by the DJL HuggingFace tokenizer native library.
# The Ubuntu-based image ships full glibc and resolves the UnsatisfiedLinkError.
FROM eclipse-temurin:25-jre
WORKDIR /work

# curl: healthcheck do docker compose (rag deve estar “ready” antes do orion-users)
RUN apt-get update -q && \
    apt-get install -y --no-install-recommends curl && \
    rm -rf /var/lib/apt/lists/*

RUN useradd -u 1001 -m jvm && chown -R jvm:jvm /work

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
