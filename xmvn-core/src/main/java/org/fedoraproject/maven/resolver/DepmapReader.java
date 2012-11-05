/*-
 * Copyright (c) 2012 Red Hat, Inc.
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
package org.fedoraproject.maven.resolver;

import static org.fedoraproject.maven.utils.Logger.debug;
import static org.fedoraproject.maven.utils.Logger.warn;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.nio.CharBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.util.Arrays;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.fedoraproject.maven.Configuration;
import org.fedoraproject.maven.model.Artifact;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

class DepmapReader
{

    private Document buildFragmentModel( File file )
        throws IOException
    {
        try
        {
            DocumentBuilderFactory fact = DocumentBuilderFactory.newInstance();
            fact.setNamespaceAware( true );
            DocumentBuilder builder = fact.newDocumentBuilder();
            String contents = wrapFragment( file );
            Reader reader = new StringReader( contents );
            try
            {
                InputSource source = new InputSource( reader );
                return builder.parse( source );
            }
            finally
            {
                reader.close();
            }
        }
        catch ( ParserConfigurationException e )
        {
            throw new IOException( e );
        }
        catch ( SAXException e )
        {
            throw new IOException( e );
        }
    }

    /**
     * Read artifact dependency mappings from file system and store in given depmap object. Mappings are in increasing
     * preference so that mappings added earlier can be overridden by mappings added later. Local depmap has highest
     * preference, followed by the versioned depmaps, followed by the versionless depmap from maven2-common-poms.
     * 
     * @param map where to store mappings
     */
    public void readArtifactMap( File root, DependencyMap map )
    {
        tryProcessFragment( map, new File( root, Configuration.VERSIONLESS_DEPMAP ) );

        for ( String path : Configuration.FRAGMENT_DIRS )
        {
            processFragmentDirectory( map, new File( root, path ) );
        }

        File localDepmap = new File( Configuration.LOCAL_DEPMAP );
        if ( localDepmap.exists() )
            tryProcessFragment( map, localDepmap );
    }

    private void processFragmentDirectory( DependencyMap jppArtifactMap, File fragmentDir )
    {
        String flist[] = fragmentDir.list();
        if ( flist != null )
        {
            Arrays.sort( flist );
            for ( String fragFilename : flist )
                tryProcessFragment( jppArtifactMap, new File( fragmentDir, fragFilename ) );
        }
    }

    private Artifact getArtifactDefinition( Element root, String childTag )
        throws IOException
    {
        NodeList jppNodeList = (NodeList) root.getElementsByTagName( childTag );

        if ( jppNodeList.getLength() == 0 )
            return Artifact.DUMMY;

        Element element = (Element) jppNodeList.item( 0 );

        NodeList nodes = element.getElementsByTagName( "groupId" );
        if ( nodes.getLength() != 1 )
            throw new IOException();
        String groupId = nodes.item( 0 ).getTextContent().trim();

        nodes = element.getElementsByTagName( "artifactId" );
        if ( nodes.getLength() != 1 )
            throw new IOException();
        String artifactId = nodes.item( 0 ).getTextContent().trim();

        nodes = element.getElementsByTagName( "version" );
        if ( nodes.getLength() > 1 )
            throw new IOException();
        String version = null;
        if ( nodes.getLength() != 0 )
            version = nodes.item( 0 ).getTextContent().trim();

        return new Artifact( groupId, artifactId, version );
    }

    private void tryProcessFragment( DependencyMap map, File fragment )
    {
        try
        {
            processFragment( map, fragment );
        }
        catch ( IOException e )
        {
            warn( "Could not process depmap file ", fragment.getAbsolutePath(), ": ", e );
            e.printStackTrace();
        }
    }

    private void processFragment( DependencyMap map, File file )
        throws IOException
    {

        debug( "Loading depmap file: ", file );
        Document mapDocument = buildFragmentModel( file );

        NodeList depNodes = (NodeList) mapDocument.getElementsByTagName( "dependency" );

        for ( int i = 0; i < depNodes.getLength(); i++ )
        {
            Element depNode = (Element) depNodes.item( i );

            Artifact from = getArtifactDefinition( depNode, "maven" );
            if ( from == Artifact.DUMMY )
                throw new IOException();

            Artifact to = getArtifactDefinition( depNode, "jpp" );
            map.addMapping( from.clearVersionAndExtension(), to.clearVersionAndExtension() );
        }
    }

    private CharBuffer readFile( File fragmentFile )
        throws IOException
    {
        FileInputStream fragmentStream = new FileInputStream( fragmentFile );
        try
        {
            FileChannel channel = fragmentStream.getChannel();
            MappedByteBuffer buffer = channel.map( FileChannel.MapMode.READ_ONLY, 0, channel.size() );
            return Charset.defaultCharset().decode( buffer );
        }
        finally
        {
            fragmentStream.close();
        }
    }

    private String wrapFragment( File fragmentFile )
        throws IOException
    {
        String openingTag = "<dependencies>";
        String closingTag = "</dependencies>";
        StringBuilder buffer = new StringBuilder();
        buffer.append( openingTag );
        buffer.append( readFile( fragmentFile ) );
        buffer.append( closingTag );
        String docString = buffer.toString();
        return docString;
    }
}
