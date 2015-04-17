package org.jenkinsci.plugins.docker.commons.impl;

import hudson.EnvVars;
import hudson.FilePath;
import org.jenkinsci.plugins.docker.commons.DockerServerCredentials;
import org.jenkinsci.plugins.docker.commons.KeyMaterial;
import org.jenkinsci.plugins.docker.commons.KeyMaterialFactory;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.UUID;

/**
 * {@link org.jenkinsci.plugins.docker.commons.KeyMaterialFactory} for talking to docker daemon.
 *
 * <p>
 * Key/certificates have to be laid out in a specific file names in this directory
 * to make docker(1) happy.
 *
 * @author Kohsuke Kawaguchi
 */
@Restricted(NoExternalUse.class)
public class ServerKeyMaterialFactory extends KeyMaterialFactory {
    
    private final DockerServerCredentials credentials;

    public ServerKeyMaterialFactory(@CheckForNull final DockerServerCredentials credentials) {
        this.credentials = credentials;
    }

    @Override
    public KeyMaterial materialize() throws IOException, InterruptedException {
        
        EnvVars e = new EnvVars();
        if (credentials != null) {
            String key = credentials.getClientSecretKeyInPEM();
            String cert = credentials.getClientCertificateInPEM();
            String ca = credentials.getServerCaCertificateInPEM();

            if (key != null && cert != null && ca != null) {
                final FilePath tempCredsDir = new FilePath(getContext().getBaseDir(), UUID.randomUUID().toString());

                // protect this information from prying eyes
                tempCredsDir.chmod(0600);

                // these file names are defined by convention by docker
                copyInto(tempCredsDir, "key.pem", key);
                copyInto(tempCredsDir,"cert.pem", cert);
                copyInto(tempCredsDir,"ca.pem", ca);

                e.put("DOCKER_TLS_VERIFY", "1");
                e.put("DOCKER_CERT_PATH", tempCredsDir.getRemote());
                return new ServerKeyMaterial(e, tempCredsDir);
            }
        }

        return new ServerKeyMaterial(e);
    }

    private void copyInto(FilePath dir, String fileName, String content) throws IOException, InterruptedException {
        if (content==null)      return;
        dir.child(fileName).write(content,"UTF-8");
    }

    private static final long serialVersionUID = 1L;
    
    private static final class ServerKeyMaterial extends KeyMaterial {

        private final FilePath[] tempDirs;

        protected ServerKeyMaterial(EnvVars envVars, FilePath... temporaryDirectories) {
            super(envVars);
            this.tempDirs = temporaryDirectories;
        }

        @Override
        public void close() throws IOException {
            Throwable first = null;
            if (tempDirs != null) {
                for (FilePath tempDir : tempDirs) {
                    try {
                        tempDir.deleteRecursive();
                    } catch (InterruptedException e) {
                        first = first == null ? e : first;
                    } catch (IOException e) {
                        first = first == null ? e : first;
                    } catch (RuntimeException e) {
                        first = first == null ? e : first;
                    } catch (Throwable e) {
                        first = first == null ? e : first;
                    }
                }
            }
            if (first != null) {
                if (first instanceof IOException) {
                    throw (IOException) first;
                } else if (first instanceof InterruptedException) {
                    throw new IOException(first);
                } else if (first instanceof RuntimeException) {
                    throw (RuntimeException) first;
                } else {
                    throw new IOException("Error closing credentials.", first);
                }
            }
        }
    }
}