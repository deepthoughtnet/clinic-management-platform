FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace

COPY pom.xml /workspace/pom.xml
COPY backend /workspace/backend

RUN mvn -pl backend/api/api-bff -am -Dmaven.test.skip=true clean package

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --create-home --uid 10001 appuser

COPY --from=build /workspace/backend/api/api-bff/target/*.jar /app/app.jar

ARG JEEVANAM_RELEASE_TAG
ARG JEEVANAM_GIT_COMMIT
ARG JEEVANAM_BUILD_TIMESTAMP
ARG JEEVANAM_DEPLOYMENT_TIMESTAMP
ARG JEEVANAM_ENVIRONMENT

ENV SPRING_PROFILES_ACTIVE=docker
ENV JEEVANAM_RELEASE_TAG=${JEEVANAM_RELEASE_TAG}
ENV JEEVANAM_GIT_COMMIT=${JEEVANAM_GIT_COMMIT}
ENV JEEVANAM_BUILD_TIMESTAMP=${JEEVANAM_BUILD_TIMESTAMP}
ENV JEEVANAM_DEPLOYMENT_TIMESTAMP=${JEEVANAM_DEPLOYMENT_TIMESTAMP}
ENV JEEVANAM_ENVIRONMENT=${JEEVANAM_ENVIRONMENT}
EXPOSE 8089

HEALTHCHECK --interval=20s --timeout=5s --retries=5 --start-period=45s \
  CMD curl -fsS http://127.0.0.1:8089/actuator/health || exit 1

USER appuser
ENTRYPOINT ["java","-XX:MaxRAMPercentage=75.0","-jar","/app/app.jar"]
