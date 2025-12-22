import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.zip.ZipInputStream;

public class GradleWrapperBootstrap {
    public static void main(String[] args) throws Exception {
        String gradleVersion = "7.4.2";
        String zipUrl = "https://services.gradle.org/distributions/gradle-" + gradleVersion + "-bin.zip";
        String zipFile = "gradle-" + gradleVersion + "-bin.zip";
        String wrapperJar = "gradle-wrapper-" + gradleVersion + ".jar";
        String wrapperDir = "gradle/wrapper";

        // Download the Gradle distribution
        try (var in = new URL(zipUrl).openStream()) {
            Files.copy(in, Paths.get(zipFile), StandardCopyOption.REPLACE_EXISTING);
        }

        // Create the wrapper directory
        Files.createDirectories(Paths.get(wrapperDir));

        // Extract the wrapper JAR from the zip
        try (var zin = new ZipInputStream(Files.newInputStream(Paths.get(zipFile)))) {
            var entry = zin.getNextEntry();
            while (entry != null) {
                if (entry.getName().endsWith(wrapperJar)) {
                    Files.copy(zin, Paths.get(wrapperDir, "gradle-wrapper.jar"), StandardCopyOption.REPLACE_EXISTING);
                    break;
                }
                entry = zin.getNextEntry();
            }
        }

        // Clean up the downloaded zip file
        Files.delete(Paths.get(zipFile));

        System.out.println("Gradle wrapper setup complete.");
    }
}
