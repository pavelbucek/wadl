package org.jvnet.ws.wadl.maven;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.DirectoryScanner;
import org.jvnet.ws.wadl2java.Wadl2Java;

import com.sun.codemodel.JClassAlreadyExistsException;
import com.sun.codemodel.writer.FileCodeWriter;

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
     * Specify a remote URLs for the target where a local version is not 
     * avaliable
     *
     * @parameter
     */
    private URL targets[];
    /**
     * The patterns of the files to be included in the transformation.
     *
     * @parameter expression="*.wadl"
     */
    private String includes;
    /**
     * The names of customization files.
     *
     * @parameter
     */
    private List<String> customizations;
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
     * A list of key/value pairs with the key being the Base URL in the WADL file. The value is the
     * desired name of the resulting class.
     *
     * <pre>
     *
     * <customClassNames>
     *  <property>
     *   <name>http://localhost:8080</name>
     *   <value>MyApi</value>
     *  </property>
     *  <property>
     *   <name>http://example.com/api</name>
     *   <value>MyApi</value>
     *  </property>
     * </customClassNames>
     * </pre>
     *
     * Properties used instead of map because colon is not allowed in xml element name.
     *
     * @parameter expression="${customClassNames}"
     *
     *
     * @see <a href="http://java.net/jira/browse/WADL-43">WADL-43</a>
     */
    private Properties customClassNames;
    /**
     * A boolean, indicating if the mojo should fail entirely if it fails to
     * generate code from a single WADL file.
     *
     * @parameter default="true"
     */
    private boolean failOnError = true;

    public void execute() throws MojoExecutionException, MojoFailureException {
        doExecute(failOnError ? new FailOnErrorPolicy()
                : new LogOnlyErrorPolicy());
    }

    private void doExecute(ErrorPolicy policy) throws MojoExecutionException {

        List<URI> toProcess = new ArrayList<URI>();

        // Look for files in disk
        if (sourceDirectory!=null && sourceDirectory.exists() && sourceDirectory.canRead()) {
            String[] matches = getWadlFileMatches();
            for (int i = matches.length - 1; i >= 0; i--) {
                File file = new File(sourceDirectory, matches[i]);
                toProcess.add(file.toURI());
            }
        }

        // Look for remote URLs
        if (targets!=null)
        {
            for (URL target : targets)
            {
                try {
                    toProcess.add(target.toURI());
                } catch (URISyntaxException ex) {
                    Logger.getLogger(Wadl2JavaMojo.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        }

        // Process if we have found any URLS to work on
        try {
            assureTargetDirExistence();
            Wadl2Java processor = createProcessor();
            for (URI next : toProcess) {
                try {
                    processor.process(next);
                } catch (JClassAlreadyExistsException jcae) {
                    policy.process(jcae.getExistingClass().fullName()
                            + " already exists.", jcae);
                } catch (Exception e) {
                    policy.process("Failed to generate sources from "
                            + next.toASCIIString() + ".", e);
                    throw new MojoExecutionException(
                            e.getMessage(), e);
                }
            }
            project.addCompileSourceRoot(targetDirectory.getAbsolutePath());
        } catch (IOException ioe) {
            // This case cannot happen as we already check to
            // see if the directory in in place
            //

            policy.process("Unexpected exception creating processor", ioe);
        }
    }

    /**
     * Returns the WADL files to be processed.
     *
     * @return The WADL files to be processed, based on the {@link #includes}
     *         Mojo parameter.
     */
    private String[] getWadlFileMatches() {
        String[] patterns = includes.split(",");
        DirectoryScanner scanner = new DirectoryScanner();
        scanner.setBasedir(sourceDirectory);
        scanner.setIncludes(patterns);
        scanner.scan();
        String[] matches = scanner.getIncludedFiles();
        return matches;
    }

    /**
     * Create a new {@link Wadl2Java} processor, based on the Mojo parameters.
     *
     * @return A new {@link Wadl2Java} instance.
     */
    private Wadl2Java createProcessor() throws IOException {
        List<File> customizationFiles = null;
        if (customizations != null) {
            customizationFiles = new ArrayList<File>(customizations.size());
            for (String customization : customizations) {
                customizationFiles.add(new File(customization));
            }
        } else {
            customizationFiles = Collections.EMPTY_LIST;
        }

        if (customClassNames == null) {
            customClassNames = new Properties();
        }

        // convert objects from properties to String.
        HashMap<String, String> customClassNamesMap = new HashMap<String, String>();
        for (Map.Entry<Object, Object> entry : customClassNames.entrySet()) {
            customClassNamesMap.put(entry.getKey().toString(), entry.getValue().toString());
        }

        Wadl2Java.Parameters parameters = new Wadl2Java.Parameters();
        parameters.setAutoPackage(autoPackaging);
        parameters.setCustomClassNames(customClassNamesMap);
        parameters.setCustomizationsAsFiles(customizationFiles);
        parameters.setPkg(packageName);
        parameters.setRootDir(targetDirectory.toURI());
        parameters.setCodeWriter(new FileCodeWriter(targetDirectory));
        Wadl2Java processor = new Wadl2Java(parameters);

        return processor;
    }

    /**
     * Verifies if the target directory exists, and if it doesn't exist, creates
     * it.
     *
     * @throws MojoExecutionException If it failed to create the target directory.
     */
    private void assureTargetDirExistence() throws MojoExecutionException {
        if (!targetDirectory.exists()) {
            if (!targetDirectory.mkdirs()) {
                throw new MojoExecutionException("Failed to create "
                        + targetDirectory.getAbsolutePath());
            }
        }
    }

    private interface ErrorPolicy {

        void process(String message, Throwable cause)
                throws MojoExecutionException;
    }

    private class FailOnErrorPolicy implements ErrorPolicy {

        public void process(String message, Throwable cause)
                throws MojoExecutionException {
            throw new MojoExecutionException(message, cause);
        }
    }

    private class LogOnlyErrorPolicy implements ErrorPolicy {

        public void process(String message, Throwable cause)
                throws MojoExecutionException {
            getLog().error(message, cause);
        }
    }
}
