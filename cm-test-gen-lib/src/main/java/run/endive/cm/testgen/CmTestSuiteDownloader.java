package run.endive.cm.testgen;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.stream.Stream;
import net.lingala.zip4j.ZipFile;

final class CmTestSuiteDownloader {

    CmTestSuiteDownloader() {}

    public void downloadTestsuite(
            String testSuiteRepo,
            String testSuiteRepoRef,
            String testSuiteSubdir,
            File testSuiteFolder)
            throws IOException {
        if (testSuiteFolder.exists() && !hasWastFiles(testSuiteFolder)) {
            try (Stream<Path> files =
                    Files.walk(testSuiteFolder.toPath()).sorted(Comparator.reverseOrder())) {
                files.map(Path::toFile).forEach(File::delete);
            }
        }

        if (!testSuiteFolder.exists()) {
            String zipName = testSuiteRepoRef + ".zip";
            URL url = new URL(testSuiteRepo + "/archive/" + zipName);
            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            con.setRequestMethod("GET");

            try (InputStream in = con.getInputStream()) {
                var zipFile = Paths.get("target/" + zipName);
                Files.createDirectories(zipFile.getParent());
                Files.write(zipFile, in.readAllBytes());

                String repoName = extractRepoName(testSuiteRepo);
                String archivePrefix = repoName + "-" + testSuiteRepoRef;

                try (var zip = new ZipFile(zipFile.toFile())) {
                    zip.extractAll("target/");
                }

                Path extractedTestDir = Paths.get("target", archivePrefix, testSuiteSubdir);
                if (!extractedTestDir.toFile().isDirectory()) {
                    throw new RuntimeException(
                            "Test subdirectory not found in archive: " + extractedTestDir);
                }

                Files.move(extractedTestDir, testSuiteFolder.toPath());

                Path extractedRoot = Paths.get("target", archivePrefix);
                if (extractedRoot.toFile().exists()) {
                    try (Stream<Path> files =
                            Files.walk(extractedRoot).sorted(Comparator.reverseOrder())) {
                        files.map(Path::toFile).forEach(File::delete);
                    }
                }
            } finally {
                con.disconnect();
            }
        }
    }

    private static boolean hasWastFiles(File folder) {
        String[] wastFiles = folder.list((dir, name) -> name.endsWith(".wast"));
        if (wastFiles != null && wastFiles.length > 0) {
            return true;
        }
        File[] subdirs = folder.listFiles(File::isDirectory);
        if (subdirs != null) {
            for (File subdir : subdirs) {
                if (hasWastFiles(subdir)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static String extractRepoName(String repoUrl) {
        String url = repoUrl.endsWith("/") ? repoUrl.substring(0, repoUrl.length() - 1) : repoUrl;
        int lastSlash = url.lastIndexOf('/');
        return url.substring(lastSlash + 1);
    }
}
