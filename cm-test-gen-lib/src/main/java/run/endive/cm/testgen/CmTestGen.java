package run.endive.cm.testgen;

import static run.endive.cm.testgen.CmConstants.SPEC_JSON;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.javaparser.utils.SourceRoot;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import run.endive.cm.testgen.wast.CmWast;
import run.endive.cm.tools.JsonFromWast;

public final class CmTestGen {

    private CmTestGen() {}

    public static void execute(
            String testSuiteRepo,
            String testSuiteRepoRef,
            String testSuiteSubdir,
            File testsuiteFolder,
            File sourceDestinationFolder,
            File compiledWastTargetFolder,
            List<String> includedWasts,
            List<String> excludedTests,
            List<String> excludedWasts) {
        validate(includedWasts, "includedWasts");
        validate(excludedTests, "excludedTests");
        validate(excludedWasts, "excludedWasts");

        var downloader = new CmTestSuiteDownloader();
        var testGen = new CmJavaTestGen(excludedTests);

        if (!compiledWastTargetFolder.isDirectory() && !compiledWastTargetFolder.mkdirs()) {
            throw new RuntimeException("Failed to create folder: " + compiledWastTargetFolder);
        }
        if (!sourceDestinationFolder.isDirectory() && !sourceDestinationFolder.mkdirs()) {
            throw new RuntimeException("Failed to create folder: " + sourceDestinationFolder);
        }

        try {
            downloader.downloadTestsuite(
                    testSuiteRepo, testSuiteRepoRef, testSuiteSubdir, testsuiteFolder);

            Set<String> allWastFiles = new HashSet<>();
            collectWastFiles(testsuiteFolder.toPath(), testsuiteFolder.toPath(), allWastFiles);
            includedWasts.forEach(allWastFiles::remove);
            excludedWasts.forEach(allWastFiles::remove);
            if (!allWastFiles.isEmpty()) {
                throw new RuntimeException(
                        "Some wast files are not included or excluded: " + allWastFiles);
            }

            final SourceRoot dest = new SourceRoot(sourceDestinationFolder.toPath());

            TestGenerator generator =
                    new TestGenerator(testGen, dest, testsuiteFolder, compiledWastTargetFolder);

            includedWasts.parallelStream().forEach(generator::generateTests);

            dest.saveAll();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void collectWastFiles(Path root, Path dir, Set<String> result) {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path entry : stream) {
                if (Files.isDirectory(entry)) {
                    collectWastFiles(root, entry, result);
                } else if (entry.toString().endsWith(".wast")) {
                    result.add(root.relativize(entry).toString());
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void validate(List<String> items, String name) {
        Set<String> set = new HashSet<>();
        for (String item : items) {
            if (!set.add(item)) {
                throw new RuntimeException(name + " contains duplicate: " + item);
            }
        }
    }

    private static final class TestGenerator {

        private final CmJavaTestGen testGen;
        private final SourceRoot dest;
        private final File testsuiteFolder;
        private final File compiledWastTargetFolder;

        private TestGenerator(
                CmJavaTestGen testGen,
                SourceRoot dest,
                File testsuiteFolder,
                File compiledWastTargetFolder) {
            this.testGen = testGen;
            this.dest = dest;
            this.testsuiteFolder = testsuiteFolder;
            this.compiledWastTargetFolder = compiledWastTargetFolder;
        }

        private void generateTests(String spec) {
            var wastFile = testsuiteFolder.toPath().resolve(spec).toFile();
            if (!wastFile.exists()) {
                throw new IllegalArgumentException(
                        "Wast file " + wastFile.getAbsolutePath() + " not found");
            }

            var plainName = spec.replace(".wast", "").replace("/", "-");
            File wasmFilesFolder = compiledWastTargetFolder.toPath().resolve(plainName).toFile();
            File specFile = wasmFilesFolder.toPath().resolve(SPEC_JSON).toFile();
            if (!wasmFilesFolder.isDirectory() && !wasmFilesFolder.mkdirs()) {
                throw new RuntimeException("Could not create folder: " + wasmFilesFolder);
            }

            JsonFromWast.builder().withFile(wastFile).withOutput(wasmFilesFolder).build().process();

            var name = specFile.toPath().getParent().toFile().getName();
            var cu = testGen.generate(name, readWast(specFile), "/" + plainName);
            dest.add(
                    cu.getPackageDeclaration().orElseThrow().getName().toString(),
                    cu.getType(0).getNameAsString() + ".java",
                    cu);
        }

        private CmWast readWast(File file) {
            try {
                return new ObjectMapper().readValue(file, CmWast.class);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }
}
