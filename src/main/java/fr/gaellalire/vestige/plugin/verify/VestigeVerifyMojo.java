/*
 * This file is part of Vestige.
 *
 * Vestige is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Vestige is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Vestige.  If not, see <http://www.gnu.org/licenses/>.
 */

package fr.gaellalire.vestige.plugin.verify;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.Security;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.Unmarshaller;
import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.collection.CollectResult;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.DependencyRequest;
import org.eclipse.aether.resolution.DependencyResult;
import org.eclipse.aether.util.artifact.JavaScopes;
import org.eclipse.aether.util.graph.visitor.PreorderNodeListGenerator;

import fr.gaellalire.vestige.plugin.schema.verify.AddDependency;
import fr.gaellalire.vestige.plugin.schema.verify.AdditionalRepository;
import fr.gaellalire.vestige.plugin.schema.verify.Attachment;
import fr.gaellalire.vestige.plugin.schema.verify.Config;
import fr.gaellalire.vestige.plugin.schema.verify.ExceptIn;
import fr.gaellalire.vestige.plugin.schema.verify.MavenClassType;
import fr.gaellalire.vestige.plugin.schema.verify.MavenConfig;
import fr.gaellalire.vestige.plugin.schema.verify.Mode;
import fr.gaellalire.vestige.plugin.schema.verify.ModifyDependency;
import fr.gaellalire.vestige.plugin.schema.verify.ObjectFactory;
import fr.gaellalire.vestige.plugin.schema.verify.ReplaceDependency;
import fr.gaellalire.vestige.plugin.schema.verify.Verify;
import fr.gaellalire.vestige.spi.job.DummyJobHelper;
import fr.gaellalire.vestige.spi.resolver.ResolvedClassLoaderConfiguration;
import fr.gaellalire.vestige.spi.resolver.Scope;
import fr.gaellalire.vestige.spi.resolver.maven.CreateClassLoaderConfigurationRequest;
import fr.gaellalire.vestige.spi.resolver.maven.MavenContext;
import fr.gaellalire.vestige.spi.resolver.maven.MavenContextBuilder;
import fr.gaellalire.vestige.spi.resolver.maven.ModifyDependencyRequest;
import fr.gaellalire.vestige.spi.resolver.maven.ReplaceDependencyRequest;
import fr.gaellalire.vestige.spi.resolver.maven.ResolveMavenArtifactRequest;
import fr.gaellalire.vestige.spi.resolver.maven.ResolveMode;
import fr.gaellalire.vestige.spi.resolver.maven.ResolvedMavenArtifact;
import fr.gaellalire.vestige.spi.resolver.maven.VestigeMavenResolver;

/**
 * @author Gael Lalire
 */
@Mojo(name = "create-verification-metadata", defaultPhase = LifecyclePhase.GENERATE_RESOURCES)
public class VestigeVerifyMojo extends AbstractMojo {

    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @Component
    private RepositorySystem repositorySystem;

    @Parameter(defaultValue = "${repositorySystemSession}", readonly = true, required = true)
    private RepositorySystemSession repositorySystemSession;

    @Parameter(defaultValue = "${project.remoteProjectRepositories}", readonly = true, required = true)
    private List<RemoteRepository> remoteRepositories;

    /**
     * The ear modules configuration.
     */
    @Parameter(required = true)
    private Verification[] verifications;

    @Parameter(defaultValue = "10.4.3")
    private String vestigeVersion;

    private Properties properties;

    public void setVerifications(final Verification[] verifications) {
        this.verifications = verifications;
    }

    public void setRepositorySystem(final RepositorySystem repositorySystem) {
        this.repositorySystem = repositorySystem;
    }

    public void setRepositorySystemSession(final RepositorySystemSession repositorySystemSession) {
        this.repositorySystemSession = repositorySystemSession;
    }

    public void setRemoteRepositories(final List<RemoteRepository> remoteRepositories) {
        this.remoteRepositories = remoteRepositories;
    }

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {

        properties = new Properties(project.getProperties());
        properties.put("project.groupId", project.getGroupId());
        properties.put("project.artifactId", project.getArtifactId());
        properties.put("project.version", project.getVersion());

        Artifact aetherArtifact = new DefaultArtifact("fr.gaellalire.vestige", "vestige.resolver.maven", null, "jar", vestigeVersion);

        try {
            CollectRequest collectRequest = new CollectRequest(new Dependency(aetherArtifact, JavaScopes.RUNTIME), remoteRepositories);
            RemoteRepository.Builder builder = new RemoteRepository.Builder("gaellalire", "default", "https://gaellalire.fr/maven/repository");
            collectRequest.addRepository(builder.build());

            CollectResult collectResult = repositorySystem.collectDependencies(repositorySystemSession, collectRequest);

            DependencyRequest dependencyRequest = new DependencyRequest(collectResult.getRoot(), null);

            DependencyResult dependencyResult = repositorySystem.resolveDependencies(repositorySystemSession, dependencyRequest);

            PreorderNodeListGenerator nlg = new PreorderNodeListGenerator();
            dependencyResult.getRoot().accept(nlg);

            List<Artifact> artifacts = nlg.getArtifacts(true);
            List<URL> urlList = new ArrayList<URL>();
            for (Artifact artifact : artifacts) {
                urlList.add(artifact.getFile().toURI().toURL());
            }

            final ClassLoader systemClassLoader = ClassLoader.getSystemClassLoader();
            ClassLoader classLoader = new URLClassLoader(urlList.toArray(new URL[urlList.size()]), systemClassLoader.getParent()) {
                @Override
                public Class<?> loadClass(final String name) throws ClassNotFoundException {
                    if (name.startsWith("fr.gaellalire.vestige.core")) {
                        return VestigeVerifyMojo.class.getClassLoader().loadClass(name);
                    }
                    if (name.startsWith("fr.gaellalire.vestige.spi")) {
                        return VestigeVerifyMojo.class.getClassLoader().loadClass(name);
                    }
                    return super.loadClass(name);
                }
            };
            Class<?> mavenArtifactResolverClass = classLoader.loadClass("fr.gaellalire.vestige.resolver.maven.MavenArtifactResolver");
            Class<?> vestigePlatformClass = classLoader.loadClass("fr.gaellalire.vestige.platform.VestigePlatform");
            Class<?> vestigeWorkerClass = classLoader.loadClass("fr.gaellalire.vestige.core.executor.VestigeWorker");
            Class<?> sslContextAccessorClass = classLoader.loadClass("fr.gaellalire.vestige.resolver.maven.SSLContextAccessor");
            Class<?> urlFactoryClass = classLoader.loadClass("fr.gaellalire.vestige.resolver.maven.URLFactory");
            Class<?> withHandlerUrlFactoryClass = classLoader.loadClass("fr.gaellalire.vestige.resolver.maven.WithHandlerURLFactory");
            Object arrayInstance = Array.newInstance(vestigeWorkerClass, 1);

            Constructor<?> constructor = mavenArtifactResolverClass.getConstructor(vestigePlatformClass, arrayInstance.getClass(), File.class, sslContextAccessorClass,
                    urlFactoryClass);

            File mavenSettingsFile = new File(System.getProperty("user.home"), ".m2" + File.separator + "settings.xml");

            VestigeMavenResolver vestigeMavenResolver = (VestigeMavenResolver) constructor.newInstance(null, arrayInstance, mavenSettingsFile, null,
                    withHandlerUrlFactoryClass.getConstructor(File.class).newInstance(new Object[] {null}));

            execute(vestigeMavenResolver);

        } catch (Exception e) {
            throw new MojoExecutionException("Artifact could not be resolved.", e);
        }
    }

    @SuppressWarnings("unchecked")
    public Verify getVerify(final InputStream inputStream) throws Exception {
        Unmarshaller unMarshaller = null;
        JAXBContext jc = JAXBContext.newInstance(ObjectFactory.class.getPackage().getName());
        unMarshaller = jc.createUnmarshaller();

        URL xsdURL = VestigeVerifyMojo.class.getResource("verify.xsd");
        SchemaFactory schemaFactory = SchemaFactory.newInstance("http://www.w3.org/2001/XMLSchema");
        Schema schema = schemaFactory.newSchema(new Source[] {new StreamSource(xsdURL.toExternalForm())});
        unMarshaller.setSchema(schema);
        return ((JAXBElement<Verify>) unMarshaller.unmarshal(inputStream)).getValue();
    }

    public String getString(final String value) {
        if (value == null) {
            return null;
        }
        return PropertyExpander.expand(value, properties);
    }

    public void execute(final VestigeMavenResolver mavenArtifactResolver) throws Exception {
        for (Verification verification : verifications) {

            Verify verify;
            FileInputStream inputStream = new FileInputStream(verification.getVerifyFile());
            try {
                verify = getVerify(inputStream);
            } finally {
                inputStream.close();
            }

            MavenContextBuilder mavenContextBuilder = mavenArtifactResolver.createMavenContextBuilder();

            Config configurations = verify.getConfigurations();
            if (configurations != null) {
                MavenConfig mavenConfig = configurations.getMavenConfig();
                if (mavenConfig != null) {
                    mavenContextBuilder.setPomRepositoriesIgnored(mavenConfig.isPomRepositoriesIgnored());
                    mavenContextBuilder.setSuperPomRepositoriesIgnored(mavenConfig.isSuperPomRepositoriesUsed());
                    for (Object object : mavenConfig.getModifyDependencyOrReplaceDependencyOrAdditionalRepository()) {
                        if (object instanceof ModifyDependency) {
                            ModifyDependency modifyDependency = (ModifyDependency) object;
                            ModifyDependencyRequest modifyDependencyRequest = mavenContextBuilder.addModifyDependency(getString(modifyDependency.getGroupId()),
                                    getString(modifyDependency.getArtifactId()));
                            AddDependency patch = modifyDependency.getPatch();
                            if (patch != null) {
                                modifyDependencyRequest.setPatch(getString(patch.getGroupId()), getString(patch.getArtifactId()), getString(patch.getVersion()));
                            }
                            for (AddDependency addDependency : modifyDependency.getAddDependency()) {
                                modifyDependencyRequest.addDependency(getString(addDependency.getGroupId()), getString(addDependency.getArtifactId()),
                                        getString(addDependency.getVersion()));
                            }
                            modifyDependencyRequest.execute();
                        } else if (object instanceof ReplaceDependency) {
                            ReplaceDependency replaceDependency = (ReplaceDependency) object;
                            ReplaceDependencyRequest replaceDependencyRequest = mavenContextBuilder.addReplaceDependency(getString(replaceDependency.getGroupId()),
                                    getString(replaceDependency.getArtifactId()));
                            for (AddDependency addDependency : replaceDependency.getAddDependency()) {
                                replaceDependencyRequest.addDependency(getString(addDependency.getGroupId()), getString(addDependency.getArtifactId()),
                                        getString(addDependency.getVersion()));
                            }
                            for (ExceptIn exceptIn : replaceDependency.getExceptIn()) {
                                replaceDependencyRequest.addExcept(getString(exceptIn.getGroupId()), getString(exceptIn.getArtifactId()));
                            }
                            replaceDependencyRequest.execute();
                        } else if (object instanceof AdditionalRepository) {
                            AdditionalRepository additionalRepository = (AdditionalRepository) object;
                            mavenContextBuilder.addAdditionalRepository(getString(additionalRepository.getId()), getString(additionalRepository.getLayout()),
                                    getString(additionalRepository.getUrl()));
                        }

                    }
                }
            }

            MavenContext mavenContext = mavenContextBuilder.build();

            Attachment attachment = verify.getAttachment();

            MavenClassType mavenResolver = attachment.getMavenResolver();
            if (mavenResolver != null) {
                ResolveMavenArtifactRequest resolveMavenArtifactRequest = mavenContext.resolve(getString(mavenResolver.getGroupId()), getString(mavenResolver.getArtifactId()),
                        getString(mavenResolver.getVersion()));
                resolveMavenArtifactRequest.setExtension(getString(mavenResolver.getExtension()));
                ResolvedMavenArtifact resolvedMavenArtifact = resolveMavenArtifactRequest.execute(DummyJobHelper.INSTANCE);
                Mode mode = mavenResolver.getMode();
                ResolveMode resolveMode;
                switch (mode) {
                case CLASSPATH:
                    resolveMode = ResolveMode.CLASSPATH;
                    break;
                case FIXED_DEPENDENCIES:
                default:
                    resolveMode = ResolveMode.FIXED_DEPENDENCIES;
                    break;
                }
                CreateClassLoaderConfigurationRequest classLoaderConfigurationRequest = resolvedMavenArtifact.createClassLoaderConfiguration("attachment", resolveMode,
                        Scope.ATTACHMENT);
                ResolvedClassLoaderConfiguration resolvedClassLoaderConfiguration = classLoaderConfigurationRequest.execute();
                String verificationMetadata = resolvedClassLoaderConfiguration.createVerificationMetadata();

                File verificationMetadataFile = verification.getVerificationMetadataFile();
                if (verificationMetadataFile != null) {
                    File parentFile = verificationMetadataFile.getParentFile();
                    if (!parentFile.isDirectory()) {
                        parentFile.mkdirs();
                    }
                    BufferedWriter bufferedOutputStream = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(verificationMetadataFile), "UTF-8"));
                    try {
                        bufferedOutputStream.write(verificationMetadata);
                    } finally {
                        bufferedOutputStream.close();
                    }
                }
                String mavenPropertyName = verification.getMavenPropertyName();
                if (mavenPropertyName != null) {
                    project.getProperties().setProperty(mavenPropertyName, verificationMetadata);
                }
            }
        }

    }

}
