ARG MAVEN_IMAGE=maven:3.9.9-eclipse-temurin-21
FROM ${MAVEN_IMAGE} AS builder
WORKDIR /workspace

COPY . .

RUN mvn -B -ntp -pl insight-server -am clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /workspace/insight-server/target/insight-server-0.1.0-SNAPSHOT.jar app.jar
EXPOSE 9966
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
