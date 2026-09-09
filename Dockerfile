# syntax=docker/dockerfile:1

# --- Etapa de build ---
FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace

# Cachea la resolucion de dependencias
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
RUN chmod +x gradlew && ./gradlew --no-daemon help > /dev/null 2>&1 || true

# Compila y empaqueta
COPY src ./src
RUN ./gradlew --no-daemon clean bootJar -x test

# --- Etapa de runtime ---
FROM eclipse-temurin:25-jre AS runtime
WORKDIR /app

RUN groupadd --system app && useradd --system --gid app app
COPY --from=build /workspace/build/libs/*.jar app.jar
USER app

EXPOSE 8080
ENV JAVA_OPTS=""
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
