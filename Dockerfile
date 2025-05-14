# Minimalist image: 100MB smaller than jdk-slim
FROM eclipse-temurin:17-jdk-alpine AS build
# Add:
ENV JAVA_TOOL_OPTIONS="-XX:+UseSerialGC -Xmx384m -Xms128m -XX:MaxRAMPercentage=75"
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
