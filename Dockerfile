# Use OpenJDK 17 as the base image
FROM openjdk:17-jdk-slim

# Set working directory
WORKDIR /app

# Copy the entire project (including Gradle wrapper and wrapper JAR)
COPY . /app

# Ensure the Gradle wrapper is executable
RUN chmod +x ./gradlew

# Build the fat JAR using Shadow plugin (bundles dependencies)
RUN ./gradlew clean shadowJar --no-daemon

# Copy .env (optional if you mount with --env-file)
# COPY .env /app/.env    # if needed, otherwise use --env-file

# Expose any ports if necessary
EXPOSE 8080

# Run the fat JAR (Shadow output contains all dependencies)
CMD ["java", "-jar", "build/libs/discord-bot-final-1.0-SNAPSHOT-all.jar"]
