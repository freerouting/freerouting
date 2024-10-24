# Stage 1: Build the application
FROM eclipse-temurin:21-jdk AS build

# Set the working directory in the container
WORKDIR /app

# Copy the current directory contents into the container at /app
COPY . /app

# Gradle build the application
RUN ./gradlew build

# Stage 2: Create the final image
FROM eclipse-temurin:21-jre-alpine

# Set the working directory in the container
WORKDIR /app

# Copy the built application from the build stage
COPY --from=build /app/build/libs/freerouting-executable.jar /app/freerouting-executable.jar

# Expose the port the app runs on
EXPOSE 37864

# Define a writable volume
VOLUME /mnt/freerouting

# Run the application
CMD ["java", "-jar", "/app/freerouting-executable.jar", "--api_server-enabled=true", "--gui-enabled=false", "--feature_flags-save_jobs=1", "--user-data-path=/mnt/freerouting"]