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

import java.io.File;
import java.security.Security;

import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.jsse.provider.BouncyCastleJsseProvider;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.connector.basic.BasicRepositoryConnectorFactory;
import org.eclipse.aether.impl.DefaultServiceLocator;
import org.eclipse.aether.internal.impl.DefaultChecksumPolicyProvider;
import org.eclipse.aether.internal.impl.DefaultRepositoryLayoutProvider;
import org.eclipse.aether.internal.impl.DefaultTransporterProvider;
import org.eclipse.aether.internal.impl.Maven2RepositoryLayoutFactory;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.LocalRepositoryManager;
import org.eclipse.aether.repository.MirrorSelector;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory;
import org.eclipse.aether.spi.connector.checksum.ChecksumPolicyProvider;
import org.eclipse.aether.spi.connector.layout.RepositoryLayoutFactory;
import org.eclipse.aether.spi.connector.layout.RepositoryLayoutProvider;
import org.eclipse.aether.spi.connector.transport.TransporterFactory;
import org.eclipse.aether.spi.connector.transport.TransporterProvider;
import org.eclipse.aether.transport.file.FileTransporterFactory;
import org.eclipse.aether.transport.http.HttpTransporterFactory;

/**
 * @author Gael Lalire
 */
public class VestigeVerifyMojoTest {

    static {
        Security.addProvider(new BouncyCastleJsseProvider(new BouncyCastleProvider()));
    }

    public static void main(final String[] args) throws Exception {
        DefaultServiceLocator locator = MavenRepositorySystemUtils.newServiceLocator();

        locator.addService(TransporterFactory.class, FileTransporterFactory.class);
        locator.addService(TransporterFactory.class, HttpTransporterFactory.class);

        locator.addService(TransporterProvider.class, DefaultTransporterProvider.class);

        locator.addService(RepositoryConnectorFactory.class, BasicRepositoryConnectorFactory.class);
        locator.addService(RepositoryLayoutProvider.class, DefaultRepositoryLayoutProvider.class);
        locator.addService(ChecksumPolicyProvider.class, DefaultChecksumPolicyProvider.class);

        locator.addService(RepositoryLayoutFactory.class, Maven2RepositoryLayoutFactory.class);

        RepositorySystem repositorySystem = locator.getService(RepositorySystem.class);

        LocalRepository localRepo = new LocalRepository(new File("target/repo"));

        DefaultRepositorySystemSession repositorySystemSession = MavenRepositorySystemUtils.newSession();
        repositorySystemSession.setMirrorSelector(new MirrorSelector() {

            @Override
            public RemoteRepository getMirror(final RemoteRepository repository) {
                if ("central".equals(repository.getId())) {
                    return new RemoteRepository.Builder("central", "default", "https://repo.maven.apache.org/maven2").build();
                }
                return repository;
            }
        });
        LocalRepositoryManager lrm = repositorySystem.newLocalRepositoryManager(repositorySystemSession, localRepo);
        repositorySystemSession.setLocalRepositoryManager(lrm);

        VestigeVerifyMojo vestigeVerifyMojo = new VestigeVerifyMojo();
        vestigeVerifyMojo.setRepositorySystem(repositorySystem);
        vestigeVerifyMojo.setRepositorySystemSession(repositorySystemSession);
        // vestigeVerifyMojo.setRemoteRepositories(remoteRepositories);
        vestigeVerifyMojo.execute();

    }

}
