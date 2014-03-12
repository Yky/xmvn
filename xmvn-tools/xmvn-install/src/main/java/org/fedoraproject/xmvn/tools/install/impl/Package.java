/*-
 * Copyright (c) 2012-2014 Red Hat, Inc.
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
package org.fedoraproject.xmvn.tools.install.impl;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.fedoraproject.xmvn.artifact.Artifact;
import org.fedoraproject.xmvn.artifact.DefaultArtifact;
import org.fedoraproject.xmvn.config.InstallerSettings;
import org.fedoraproject.xmvn.config.PackagingRule;
import org.fedoraproject.xmvn.utils.ArtifactUtils;
import org.fedoraproject.xmvn.utils.FileUtils;

/**
 * @author Mikolaj Izdebski
 */
public class Package
    implements Comparable<Package>
{
    private final String suffix;

    public static final String MAIN = "";

    private static final String DEFAULT_SUFFIX = "__default";

    private static final String NOINSTALL_SUFFIX = "__noinstall";

    private final InstallerSettings settings;

    /** installed devel artifacts (no aliases) */
    private final Set<Artifact> develArtifacts = new LinkedHashSet<>();

    /** installed user artifacts (no aliases) */
    private final Set<Artifact> userArtifacts = new LinkedHashSet<>();

    /** map generic artifact => provided artifact */
    private final Map<Artifact, Artifact> providedArtifacts = new LinkedHashMap<>();

    private final List<PackagePreInstallHook> preInstallHooks = new ArrayList<>();

    private final Map<String, Object> properties = new LinkedHashMap<>();

    public Package( String name, InstallerSettings settings )
    {
        this.settings = settings;
        metadata = new FragmentFile();
        suffix = ( name.equals( "" ) || name.equals( DEFAULT_SUFFIX ) ) ? "" : "-" + name;
    }

    private final FragmentFile metadata;

    class TargetFile
    {
        Path sourceFile;

        Path dirPath;

        Path targetName;

        int mode;
    }

    public String getSuffix()
    {
        return suffix;
    }

    private final List<TargetFile> targetFiles = new LinkedList<>();

    public void addFile( Path file, Path dirPath, Path fileName, int mode )
    {
        TargetFile target = new TargetFile();
        target.sourceFile = file;
        target.dirPath = dirPath;
        target.targetName = fileName;
        target.mode = mode;
        targetFiles.add( target );
    }

    public void addFile( Path file, Path target, int mode )
    {
        Path parent = target.getParent();
        if ( parent == null )
            parent = Paths.get( "." );

        addFile( file, parent, target.getFileName(), mode );
    }

    public void addSymlink( Path symlinkFile, Path symlinkTarget )
        throws IOException
    {
        if ( symlinkFile.isAbsolute() )
            throw new IllegalArgumentException( "symlinkFile is absolute path: " + symlinkFile );
        if ( symlinkTarget.isAbsolute() )
            throw new IllegalArgumentException( "symlinkTarget is absolute path: " + symlinkTarget );

        symlinkFile = symlinkFile.normalize();
        symlinkTarget = symlinkTarget.normalize();
        if ( symlinkFile.getParent() != null )
            symlinkTarget = symlinkFile.getParent().relativize( symlinkTarget );

        Path symlinkTempFile = FileUtils.createAnonymousSymlink( symlinkTarget );
        addFile( symlinkTempFile, symlinkFile, 0644 );
    }

    private Path installDirectory( Path root, Path target )
        throws IOException
    {
        Path dir = root.resolve( target );
        return Files.createDirectories( dir );
    }

    private Path installFile( Path root, Path source, Path targetDir, Path targetName, int mode )
        throws IOException
    {
        Path dir = installDirectory( root, targetDir );

        Path target = dir.resolve( targetName );
        FileUtils.linkOrCopy( source, target );
        FileUtils.chmod( target, mode );

        return target;
    }

    private void installFiles( Path root )
        throws IOException
    {
        for ( TargetFile target : targetFiles )
            installFile( root, target.sourceFile, target.dirPath, target.targetName, target.mode );
    }

    public void createDepmaps( String groupId, String artifactId, String version, Path jppGroup, Path jppName,
                               PackagingRule rule )
    {
        Artifact artifact = new DefaultArtifact( groupId, artifactId, ArtifactUtils.DEFAULT_EXTENSION, version );
        Artifact jppArtifact =
            new DefaultArtifact( jppGroup.toString(), jppName.toString(), ArtifactUtils.DEFAULT_EXTENSION, version );

        getMetadata().addMapping( artifact, jppArtifact );

        for ( org.fedoraproject.xmvn.config.Artifact alias2 : rule.getAliases() )
        {
            Artifact alias =
                new DefaultArtifact( alias2.getGroupId(), alias2.getArtifactId(), ArtifactUtils.DEFAULT_EXTENSION,
                                     alias2.getVersion() );
            getMetadata().addMapping( alias, jppArtifact );
        }
    }

    private void installMetadata( String packageName )
        throws IOException
    {
        if ( !getMetadata().isEmpty() )
        {
            Path file = Files.createTempFile( "xmvn", ".xml" );
            getMetadata().write( file, settings );
            Path depmapName = Paths.get( packageName + suffix + ".xml" );
            Path depmapDir = Paths.get( settings.getMetadataDir() );
            addFile( file, depmapDir, depmapName, 0644 );
        }
    }

    private void createFileList()
        throws IOException
    {
        Set<Path> targetNames = new TreeSet<>();
        for ( TargetFile target : targetFiles )
            targetNames.add( target.dirPath.resolve( target.targetName ) );

        try (PrintStream ps = new PrintStream( ".mfiles" + suffix ))
        {
            for ( Path path : targetNames )
                ps.println( "/" + path );
        }
    }

    public void install( String packageName, Path root )
        throws IOException
    {
        for ( PackagePreInstallHook hook : preInstallHooks )
            hook.beforePackageInstallation( this );

        installMetadata( packageName );
        installFiles( root );
        createFileList();
    }

    public boolean isInstallable()
    {
        return !suffix.equals( "-" + NOINSTALL_SUFFIX );
    }

    public FragmentFile getMetadata()
    {
        return metadata;
    }

    @Override
    public int compareTo( Package rhs )
    {
        return suffix.compareTo( rhs.suffix );
    }

    public void addDevelArtifact( Artifact artifact )
    {
        develArtifacts.add( artifact );
    }

    public Set<Artifact> getDevelArtifacts()
    {
        return Collections.unmodifiableSet( develArtifacts );
    }

    public void addUserArtifact( Artifact artifact )
    {
        userArtifacts.add( artifact );
    }

    public Set<Artifact> getUserArtifacts()
    {
        return Collections.unmodifiableSet( userArtifacts );
    }

    public void addArtifactMetadata( Artifact artifact, List<Artifact> aliases, List<Artifact> jppArtifacts )
    {
        for ( Artifact jppArtifact : jppArtifacts )
        {
            String providedVersion = jppArtifact.getVersion();
            Artifact providedArtifact = artifact.setVersion( providedVersion ).setFile( null ).setProperties( null );

            String scope = jppArtifact.getScope();
            Artifact scopedArtifact = artifact.setScope( scope );
            Artifact scopedProvidedArtifact = providedArtifact.setScope( scope );

            providedArtifacts.put( providedArtifact, scopedProvidedArtifact );
            metadata.addMapping( scopedArtifact, jppArtifact );

            for ( Artifact alias : aliases )
            {
                Artifact providedAlias = alias.setVersion( providedVersion ).setProperties( null );
                Artifact scopedAlias = alias.setScope( scope );
                Artifact scopedProvidedAlias = providedAlias.setScope( scope );

                providedArtifacts.put( providedAlias, scopedProvidedAlias );
                metadata.addMapping( scopedAlias, jppArtifact );
            }
        }
    }

    /**
     * Get all artifacts and aliases for which this package provides depmap.
     * 
     * @return
     */
    public Set<Artifact> getTrackedArtifacts()
    {
        return Collections.unmodifiableSet( providedArtifacts.keySet() );
    }

    /**
     * Given an artifact or alias return real artifact provided by this package or null.
     * 
     * @return
     */
    public Artifact getProvidedArtifact( Artifact artifact )
    {
        return providedArtifacts.get( artifact );
    }

    public void addPreInstallHook( PackagePreInstallHook hook )
    {
        preInstallHooks.add( hook );
    }

    public void setProperty( String key, Object value )
    {
        properties.put( key, value );
    }

    public Object getProperty( String key )
    {
        return properties.get( key );
    }
}
