package com.github.harmanpa.github.wagon;

import com.google.common.base.Splitter;
import com.google.common.collect.Iterables;
import com.google.common.io.Files;
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
import org.apache.maven.wagon.events.TransferEvent;
import org.apache.maven.wagon.resource.Resource;
import org.codehaus.plexus.util.Base64;
import org.eclipse.egit.github.core.Blob;
import org.eclipse.egit.github.core.IRepositoryIdProvider;
import org.eclipse.egit.github.core.Repository;
import org.eclipse.egit.github.core.TreeEntry;
import org.eclipse.egit.github.core.client.GitHubClient;
import org.eclipse.egit.github.core.service.DataService;
import org.eclipse.egit.github.core.service.RepositoryService;

/**
 *
 * @author Peter Harman
 */
public class GithubWagon extends AbstractWagon {

    @Override
    public boolean resourceExists(String resourceName) throws TransferFailedException, AuthorizationException {
        fireSessionDebug("Checking existence of " + resourceName);
        try {
            transfer(resourceName, null, true);
            return true;
        } catch (ResourceDoesNotExistException ex) {
            return false;
        }
    }

    @Override
    protected void openConnectionInternal() throws ConnectionException, AuthenticationException {

    }

    @Override
    protected void closeConnection() throws ConnectionException {
    }

    @Override
    public void get(String string, File file) throws TransferFailedException, ResourceDoesNotExistException, AuthorizationException {
        transfer(string, file, false);
    }

    private void transfer(String string, File file, boolean dryRun) throws TransferFailedException, ResourceDoesNotExistException, AuthorizationException {
        Resource resource = new Resource(string);
        if (!dryRun) {
            fireGetInitiated(resource, file);
            fireGetStarted(resource, file);
        }
        try {
            // Parse the URL
            URI uri = new URI(getRepository().getUrl());
            String owner = uri.getHost();
            String path = uri.getPath();
            String[] pathElements = Iterables.toArray(Splitter.on('/').omitEmptyStrings().split(path), String.class);
            if (pathElements.length == 0 || pathElements.length > 2) {
                throw new ResourceDoesNotExistException("Invalid repository path " + path);
            }
            String repo = pathElements[0];
            String branch = pathElements.length == 2 ? pathElements[1] : null;
            GitHubClient client = new GitHubClient();
            client.setCredentials(getAuthenticationInfo().getUserName(), getAuthenticationInfo().getPassword());
            RepositoryService repositoryService = new RepositoryService(client);
            Repository gitRepo = repositoryService.getRepository(owner, repo);
            DataService dataService = new DataService(client);
            Blob blob = findBlob(dataService, gitRepo, string, branch);
            if (blob == null) {
                throw new ResourceDoesNotExistException("Could not find " + string);
            }
            byte[] data;
            if ("base64".equals(blob.getEncoding())) {
                data = Base64.decodeBase64(blob.getContent().getBytes());
            } else {
                data = blob.getContent().getBytes();
            }
            if (!dryRun) {
                Files.write(data, file);
                fireGetCompleted(resource, file);
            }
        } catch (URISyntaxException ex) {
            fireTransferError(resource, ex, TransferEvent.REQUEST_GET);
            throw new ResourceDoesNotExistException("Could not parse repository URL");
        } catch (IOException ex) {
            fireTransferError(resource, ex, TransferEvent.REQUEST_GET);
            throw new TransferFailedException("Error during transfer", ex);
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

    private Blob findBlob(DataService service, IRepositoryIdProvider repo, String path, String branch) throws IOException {
        String branchSha = service.getReference(repo, "heads/" + (branch == null ? "master" : branch)).getObject().getSha();
        Iterable<String> pathSplit = Splitter.on('/').omitEmptyStrings().split(path);
        return findBlob(service, repo, pathSplit, branchSha, 0);
    }

    private Blob findBlob(DataService service, IRepositoryIdProvider repo, Iterable<String> path, String sha, int depth) throws IOException {
        if (depth == Iterables.size(path)) {
            return service.getBlob(repo, sha);
        }
        String name = Iterables.get(path, depth);
        for (TreeEntry entry : service.getTree(repo, sha).getTree()) {
            if (name.equals(entry.getPath())) {
                return findBlob(service, repo, path, entry.getSha(), depth + 1);
            }
        }
        return null;
    }
}
