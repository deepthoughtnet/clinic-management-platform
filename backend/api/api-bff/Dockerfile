FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace

COPY pom.xml /workspace/pom.xml
COPY backend /workspace/backend

RUN mvn -pl backend/api/api-bff -am -DskipTests clean package

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --create-home --uid 10001 appuser

COPY --from=build /workspace/backend/api/api-bff/target/*.jar /app/app.jar

ENV SPRING_PROFILES_ACTIVE=docker
EXPOSE 8089

HEALTHCHECK --interval=20s --timeout=5s --retries=5 --start-period=45s \
  CMD curl -fsS http://127.0.0.1:8089/actuator/health || exit 1

USER appuser
ENTRYPOINT ["java","-XX:MaxRAMPercentage=75.0","-jar","/app/app.jar"]
