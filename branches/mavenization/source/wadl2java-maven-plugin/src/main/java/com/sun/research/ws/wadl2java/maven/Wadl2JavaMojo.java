package com.sun.research.ws.wadl2java.maven;

import java.io.File;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.DirectoryScanner;

import org.jvnet.ws.wadl2java.Wadl2Java;

/**
 * A Maven plugin to generate Java code from WADL descriptions.
 * 
 * @author Wilfred Springer
 * @goal generate
 * @phase generate-sources
 */
public class Wadl2JavaMojo extends AbstractMojo {

    /**
     * The packagename for classes generated by this Mojo.
     * 
     * @parameter
     * @required
     */
    private String packageName;

    /**
     * The target directory, to which all Java code will be generated.
     * 
     * @parameter expression="${basedir}/target/generated-sources/wadl"
     */
    private File targetDirectory;

    /**
     * The directory containing the WADL files.
     * 
     * @parameter expression="${basedir}/src/main/wadl"
     */
    private File sourceDirectory;

    /**
     * The patterns of the files to be included in the transformation.
     * 
     * @parameter expression="*.wadl"
     */
    private String includes;

    /**
     * The current project.
     *
     * @parameter expression="${project}"
     */
    private MavenProject project;

    /**
     * Autopackaging.
     *
     * @parameter expression="${autoPackaging}"
     */
    private boolean autoPackaging = true;

    /**
     * A boolean, indicating if the mojo should fail entirely if it fails to
     * generate code from a single WADL file.
     * 
     * @parameter default="false"
     */
    private boolean failOnError;

    public void execute() throws MojoExecutionException, MojoFailureException {
        assureTargetDirExistence();
        String[] patterns = includes.split(",");
        DirectoryScanner scanner = new DirectoryScanner();
        scanner.setBasedir(sourceDirectory);
        scanner.setIncludes(patterns);
        scanner.scan();
        String[] matches = scanner.getIncludedFiles();
        Wadl2Java processor = new Wadl2Java(targetDirectory, packageName, autoPackaging);
        for (int i = matches.length - 1; i >= 0; i--) {
            File file = new File(sourceDirectory, matches[i]);
            try {
                processor.process(file.toURI());
            } catch (Exception e) {
                if (!failOnError) {
                    getLog().warn(
                            "Failed to generate code from "
                                    + file.getAbsolutePath(), e);
                } else {
                    throw new MojoExecutionException(
                            "Failed to generate code from "
                                    + file.getAbsolutePath());
                }
            }
        }
        project.addCompileSourceRoot(targetDirectory.getAbsolutePath());
    }

    private void assureTargetDirExistence() throws MojoExecutionException {
        if (!targetDirectory.exists()) {
            if (!targetDirectory.mkdirs()) {
                throw new MojoExecutionException("Failed to create "
                        + targetDirectory.getAbsolutePath());
            }
        }
    }

}
