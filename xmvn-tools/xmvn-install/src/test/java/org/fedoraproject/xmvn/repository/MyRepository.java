/*-
 * Copyright (c) 2013-2014 Red Hat, Inc.
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
package org.fedoraproject.xmvn.repository;

import static org.junit.Assert.fail;

import org.fedoraproject.xmvn.artifact.Artifact;

/**
 * @author Mikolaj Izdebski
 */
class MyRepository
    implements Repository
{
    @Override
    public RepositoryPath getPrimaryArtifactPath( Artifact artifact )
    {
        fail( "getPrimaryArtifactPath() was not expected to be called" );
        throw null;
    }

    @Override
    public String getNamespace()
    {
        fail( "getNamespace was not expected to be called" );
        throw null;
    }
}
