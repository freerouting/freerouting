# Stage 1: Build the application
FROM eclipse-temurin:25-jdk-jammy AS build

# Set the working directory in the container
WORKDIR /app

# Copy the current directory contents into the container at /app
COPY . /app

# Gradle build the application and produce the executable fat jar.
# Tests are intentionally skipped here: they require a functional loopback
# network interface that is not reliably available under QEMU emulation used
# for cross-platform (linux/arm64) builds. Tests are run separately on the
# native CI runner before this image is built.
RUN ./gradlew executableJar -x test

# Stage 2: Create the final image
FROM eclipse-temurin:25-jre-jammy

# Set the working directory in the container
WORKDIR /app

# Copy the built application from the build stage
COPY --from=build /app/build/libs/freerouting-current-executable.jar /app/freerouting-executable.jar

# Expose the port the app runs on
EXPOSE 37864

# Define a writable volume
VOLUME /mnt/freerouting

# Run the application
CMD ["java", "-jar", "/app/freerouting-executable.jar", "--api_server-enabled=true", "--gui-enabled=false", "--feature_flags-save_jobs=1", "--user_data_path=/mnt/freerouting", "--profile-email=api@freerouting.app"]