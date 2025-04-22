ARG JAVA_VERSION=17

# Step 1: Use microsoft JDK image with maven3 to build the application
FROM mcr.microsoft.com/openjdk/jdk:${JAVA_VERSION}-mariner AS builder
RUN tdnf install maven3 -y
WORKDIR /app
COPY pom.xml ./
COPY src ./src
RUN mvn clean package -DskipTests

# Step 2: Use microsoft JDK image for the final image
FROM mcr.microsoft.com/openjdk/jdk:${JAVA_VERSION}-mariner
WORKDIR /app
COPY --from=builder /app/target/*.jar app.jar
COPY .env .env

# Expose port 8080
EXPOSE 8080

# Set server port explicitly
ENV SERVER_PORT=8080

ENTRYPOINT ["java", "-jar", "-Dserver.port=8080", "app.jar"]
