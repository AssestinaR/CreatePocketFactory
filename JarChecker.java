import java.io.InputStream;
import java.net.URI;
import java.nio.file.*;
import java.util.Collections;
import java.util.Map;

public class JarChecker {
    private static int totalCount = 0;

    public static void main(String[] args) {
        for (String jarPath : args) {
            checkJar(jarPath);
        }
    }

    private static void checkJar(String jarPath) {
        System.out.println("Checking JAR: " + jarPath);
        totalCount = 0;
        Path path = Paths.get(jarPath);
        if (!Files.exists(path)) {
            System.err.println("File not found: " + jarPath);
            return;
        }

        URI uri = URI.create("jar:" + path.toUri());
        Map<String, String> env = Collections.emptyMap();

        try (FileSystem fs = FileSystems.newFileSystem(uri, env)) {
            Path packMcmeta = fs.getPath("/pack.mcmeta");
            if (Files.exists(packMcmeta)) {
                try {
                    byte[] content = Files.readAllBytes(packMcmeta);
                    System.out.println("  Successfully read pack.mcmeta (" + content.length + " bytes)");
                } catch (Exception e) {
                    System.err.println("  Error reading pack.mcmeta: " + e.toString());
                }
            } else {
                System.out.println("  pack.mcmeta not found (skipping)");
            }

            int errors = 0;
            for (Path root : fs.getRootDirectories()) {
                errors += walkAndRead(root);
            }
            System.out.println("  Finished traversal. Entries checked: " + totalCount + ", Errors: " + errors);

        } catch (Exception e) {
            System.err.println("  Failed to open FileSystem: " + e.toString());
        }
        System.out.println();
    }

    private static int walkAndRead(Path start) {
        int errors = 0;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(start)) {
            for (Path entry : stream) {
                if (Files.isDirectory(entry)) {
                    errors += walkAndRead(entry);
                } else {
                    totalCount++;
                    try (InputStream is = Files.newInputStream(entry)) {
                        byte[] buffer = new byte[8192];
                        while (is.read(buffer) != -1) {
                        }
                    } catch (Exception e) {
                        System.err.println("  Error reading " + entry + ": " + e.toString());
                        errors++;
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("  Error walking " + start + ": " + e.toString());
            errors++;
        }
        return errors;
    }
}
