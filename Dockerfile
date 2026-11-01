# Stage 1: Build JAR with Maven
FROM maven:3.9.6-eclipse-temurin-21-alpine AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn clean package -DskipTests

# Stage 2: Minimal JRE Runtime
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Create unprivileged user for security
RUN addgroup -S scantaker && adduser -S scantaker -G scantaker && mkdir -p uploads && chown -R scantaker:scantaker /app
USER scantaker

COPY --from=build --chown=scantaker:scantaker /app/target/api-0.0.1-SNAPSHOT.jar app.jar

# Cloud platform sets PORT
ENV PORT=8081
EXPOSE 8081

# Cap JVM heap to 380MB so it stays well within Render's 512MB free RAM limit
ENTRYPOINT ["java", "-Xmx380m", "-Xms180m", "-XX:+UseG1GC", "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]
