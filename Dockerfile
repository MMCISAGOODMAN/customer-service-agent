FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
COPY ai-common/pom.xml ai-common/
COPY ai-observability/pom.xml ai-observability/
COPY ai-llm/pom.xml ai-llm/
COPY ai-agent/pom.xml ai-agent/
COPY ai-rag/pom.xml ai-rag/
COPY ai-workflow/pom.xml ai-workflow/
COPY ai-multimodal/pom.xml ai-multimodal/
COPY customer-service-app/pom.xml customer-service-app/
RUN mvn dependency:go-offline -B -pl customer-service-app -am
COPY . .
RUN mvn package -DskipTests -B -pl customer-service-app -am

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser
COPY --from=build /app/customer-service-app/target/*.jar app.jar
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD wget -qO- http://localhost:8080/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
