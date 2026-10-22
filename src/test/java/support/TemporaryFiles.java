package support;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

public abstract class TemporaryFiles {
    protected Path directory;

    @BeforeMethod(alwaysRun = true)
    public void createDirectory() throws IOException {
        directory = Files.createTempDirectory("pdf-meta-viewer-test-");
    }

    @AfterMethod(alwaysRun = true)
    public void removeDirectory() throws IOException {
        if (directory != null) {
            try (var paths = Files.walk(directory)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(path);
                }
            }
        }
    }
}
