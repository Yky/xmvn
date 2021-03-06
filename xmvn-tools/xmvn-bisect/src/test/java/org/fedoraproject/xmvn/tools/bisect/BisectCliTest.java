/*-
 * Copyright (c) 2017-2018 Red Hat, Inc.
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
package org.fedoraproject.xmvn.tools.bisect;

import static org.junit.Assert.assertTrue;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

/**
 * @author Mikolaj Izdebski
 */
public class BisectCliTest
{
    @Rule
    public TestName testName = new TestName();

    @Before
    public void setUp()
    {
        Path resDir = Paths.get( "../../src/test/resources" ).toAbsolutePath();
        System.setProperty( "xmvn.home", resDir.resolve( testName.getMethodName() ).toString() );
    }

    private List<String> run( String... args )
        throws Exception
    {
        PrintStream origErr = System.err;
        PrintStream origOut = System.out;
        ByteArrayOutputStream errStrm = new ByteArrayOutputStream();
        try ( PrintStream ps = new PrintStream( errStrm ) )
        {
            System.setOut( ps );
            System.setErr( ps );
            BisectCli.main( args );
        }
        finally
        {
            System.setOut( origOut );
            System.setErr( origErr );
        }
        ByteArrayInputStream bis = new ByteArrayInputStream( errStrm.toByteArray() );
        BufferedReader br = new BufferedReader( new InputStreamReader( bis ) );
        return br.lines().collect( Collectors.toList() );
    }

    @Test
    public void testBisect()
        throws Exception
    {
        List<String> out = run();
        assertTrue( out.stream().anyMatch( x -> x.equals( "Bisection build finished" ) ) );
        assertTrue( out.stream().anyMatch( x -> x.matches( "Failed build: +42, see bisect-build-42.log" ) ) );
        assertTrue( out.stream().anyMatch( x -> x.matches( "Successful build: +43, see bisect-build-43.log" ) ) );
    }
}
