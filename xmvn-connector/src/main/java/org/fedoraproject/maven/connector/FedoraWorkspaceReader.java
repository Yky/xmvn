/*-
 * Copyright (c) 2012-2013 Red Hat, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.fedoraproject.maven.connector;

import java.io.File;
import java.util.Collections;
import java.util.List;

import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.fedoraproject.maven.resolver.ResolutionRequest;
import org.fedoraproject.maven.resolver.ResolutionResult;
import org.fedoraproject.maven.resolver.Resolver;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.repository.WorkspaceReader;
import org.eclipse.aether.repository.WorkspaceRepository;

/**
 * @author Mikolaj Izdebski
 */
@Component( role = WorkspaceReader.class, hint = "ide" )
public class FedoraWorkspaceReader
    implements WorkspaceReader
{
    @Requirement
    private Resolver resolver;

    private static final WorkspaceRepository repository = new WorkspaceRepository();

    @Override
    public File findArtifact( Artifact artifact )
    {
        String groupId = artifact.getGroupId();
        String artifactId = artifact.getArtifactId();
        String version = artifact.getVersion();
        String extension = artifact.getExtension();

        ResolutionRequest request = new ResolutionRequest( groupId, artifactId, version, extension );
        ResolutionResult result = resolver.resolve( request );
        return result.getArtifactFile();
    }

    @Override
    public List<String> findVersions( Artifact artifact )
    {
        return Collections.singletonList( "SYSTEM" );
    }

    @Override
    public WorkspaceRepository getRepository()
    {
        return repository;
    }
}
