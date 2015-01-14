
package com.github.harmanpa.github.wagon;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import org.apache.maven.wagon.AbstractWagon;
import org.apache.maven.wagon.ConnectionException;
import org.apache.maven.wagon.ResourceDoesNotExistException;
import org.apache.maven.wagon.TransferFailedException;
import org.apache.maven.wagon.authentication.AuthenticationException;
import org.apache.maven.wagon.authorization.AuthorizationException;
import org.codehaus.plexus.util.FileUtils;
import org.kohsuke.github.GHContent;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;

/**
 *
 * @author Peter Harman
 */
public class GithubWagon extends AbstractWagon {

    @Override
    protected void openConnectionInternal() throws ConnectionException, AuthenticationException {

    }

    @Override
    protected void closeConnection() throws ConnectionException {
    }

    @Override
    public void get(String string, File file) throws TransferFailedException, ResourceDoesNotExistException, AuthorizationException {
        try {
            // Parse the URL
            URI uri = new URI(getRepository().getUrl());
            String owner = uri.getHost();
            String path = uri.getPath();
            String[] pathElements = path.split("/");
            String repo = null;
            String branch = null;
            int i = 0;
            for (String element : pathElements) {
                if (!element.trim().isEmpty()) {
                    switch (i) {
                        case 0:
                            repo = element.trim();
                            i++;
                            break;
                        case 1:
                            branch = element.trim();
                            i++;
                            break;
                        default:
                    }
                }
            }
            // Connect to GitHub API
            GitHub github = GitHub.connectUsingPassword(getAuthenticationInfo().getUserName(), getAuthenticationInfo().getPassword());
            if (repo == null) {
                throw new ResourceDoesNotExistException("No repository specified");
            }
            GHRepository ghRepo = github.getRepository(owner + "/" + repo);
            if (ghRepo == null) {
                throw new ResourceDoesNotExistException("Not connected to repository");
            }
            GHContent content = branch == null ? ghRepo.getFileContent(string) : ghRepo.getFileContent(string, branch);
            FileUtils.fileWrite(file, content.getContent());
        } catch (URISyntaxException ex) {
            throw new TransferFailedException("Failed to connect to URL", ex);
        } catch (IOException ex) {
            throw new TransferFailedException("Failed to transfer " + string, ex);
        }
    }

    @Override
    public boolean getIfNewer(String string, File file, long l) throws TransferFailedException, ResourceDoesNotExistException, AuthorizationException {
        get(string, file);
        return true;
    }

    @Override
    public void put(File file, String string) throws TransferFailedException, ResourceDoesNotExistException, AuthorizationException {
    }

}
