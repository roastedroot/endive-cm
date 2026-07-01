package run.endive.cm.maven;

import static org.apache.maven.plugins.annotations.LifecyclePhase.GENERATE_TEST_SOURCES;

import java.io.File;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.maven.model.Resource;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import run.endive.cm.testgen.CmTestGen;

@Mojo(name = "cm-test-gen", defaultPhase = GENERATE_TEST_SOURCES, threadSafe = true)
public class CmTestGenMojo extends AbstractMojo {

    @Parameter(required = true, defaultValue = "https://github.com/WebAssembly/component-model")
    private String testSuiteRepo;

    @Parameter(required = true, defaultValue = "main")
    private String testSuiteRepoRef;

    @Parameter(required = true, defaultValue = "test")
    private String testSuiteSubdir;

    @Parameter(required = true, defaultValue = "${project.basedir}/../component-model-testsuite")
    private File testsuiteFolder;

    @Parameter(
            required = true,
            defaultValue = "${project.build.directory}/generated-test-sources/cm-test-gen")
    private File sourceDestinationFolder;

    @Parameter(
            required = true,
            defaultValue = "${project.build.directory}/generated-resources/compiled-wast")
    private File compiledWastTargetFolder;

    @Parameter(required = true)
    private List<String> includedWasts;

    @Parameter(required = false, defaultValue = "[]")
    private List<String> excludedTests;

    @Parameter(defaultValue = "[]")
    private List<String> excludedWasts;

    @Parameter(property = "cm-test-gen.skip", defaultValue = "false")
    private boolean skip;

    @Parameter(property = "project", required = true, readonly = true)
    private MavenProject project;

    @Override
    public void execute() throws MojoExecutionException {
        if (skip) {
            return;
        }

        JavaParserMavenUtils.makeJavaParserLogToMavenOutput(getLog());

        var excludedTests =
                this.excludedTests.stream().map(String::trim).collect(Collectors.toList());

        try {
            CmTestGen.execute(
                    testSuiteRepo,
                    testSuiteRepoRef,
                    testSuiteSubdir,
                    testsuiteFolder,
                    sourceDestinationFolder,
                    compiledWastTargetFolder,
                    includedWasts,
                    excludedTests,
                    excludedWasts);
        } catch (RuntimeException e) {
            throw new MojoExecutionException("Failed executing CmTestGen", e);
        }

        project.addTestCompileSourceRoot(sourceDestinationFolder.getAbsolutePath());
        Resource resource = new Resource();
        resource.setDirectory(compiledWastTargetFolder.getPath());
        project.addTestResource(resource);
    }
}
