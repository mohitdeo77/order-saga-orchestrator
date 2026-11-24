# One Dockerfile for every service:  docker build --build-arg SERVICE=order-service .
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY pom.xml .
COPY orchestrator-service/pom.xml orchestrator-service/
COPY order-service/pom.xml order-service/
COPY inventory-service/pom.xml inventory-service/
COPY payment-service/pom.xml payment-service/
RUN mvn -B -q dependency:go-offline
COPY . .
ARG SERVICE
RUN mvn -B -q -pl ${SERVICE} -am package -DskipTests

FROM eclipse-temurin:17-jre
ARG SERVICE
WORKDIR /app
RUN useradd --system --uid 1001 app
COPY --from=build /workspace/${SERVICE}/target/${SERVICE}-*.jar app.jar
USER app
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
