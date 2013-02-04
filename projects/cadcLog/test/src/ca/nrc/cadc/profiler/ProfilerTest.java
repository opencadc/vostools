/*
************************************************************************
*******************  CANADIAN ASTRONOMY DATA CENTRE  *******************
**************  CENTRE CANADIEN DE DONNÉES ASTRONOMIQUES  **************
*
*  (c) 2011.                            (c) 2011.
*  Government of Canada                 Gouvernement du Canada
*  National Research Council            Conseil national de recherches
*  Ottawa, Canada, K1A 0R6              Ottawa, Canada, K1A 0R6
*  All rights reserved                  Tous droits réservés
*
*  NRC disclaims any warranties,        Le CNRC dénie toute garantie
*  expressed, implied, or               énoncée, implicite ou légale,
*  statutory, of any kind with          de quelque nature que ce
*  respect to the software,             soit, concernant le logiciel,
*  including without limitation         y compris sans restriction
*  any warranty of merchantability      toute garantie de valeur
*  or fitness for a particular          marchande ou de pertinence
*  purpose. NRC shall not be            pour un usage particulier.
*  liable in any event for any          Le CNRC ne pourra en aucun cas
*  damages, whether direct or           être tenu responsable de tout
*  indirect, special or general,        dommage, direct ou indirect,
*  consequential or incidental,         particulier ou général,
*  arising from the use of the          accessoire ou fortuit, résultant
*  software.  Neither the name          de l'utilisation du logiciel. Ni
*  of the National Research             le nom du Conseil National de
*  Council of Canada nor the            Recherches du Canada ni les noms
*  names of its contributors may        de ses  participants ne peuvent
*  be used to endorse or promote        être utilisés pour approuver ou
*  products derived from this           promouvoir les produits dérivés
*  software without specific prior      de ce logiciel sans autorisation
*  written permission.                  préalable et particulière
*                                       par écrit.
*
*  This file is part of the             Ce fichier fait partie du projet
*  OpenCADC project.                    OpenCADC.
*
*  OpenCADC is free software:           OpenCADC est un logiciel libre ;
*  you can redistribute it and/or       vous pouvez le redistribuer ou le
*  modify it under the terms of         modifier suivant les termes de
*  the GNU Affero General Public        la “GNU Affero General Public
*  License as published by the          License” telle que publiée
*  Free Software Foundation,            par la Free Software Foundation
*  either version 3 of the              : soit la version 3 de cette
*  License, or (at your option)         licence, soit (à votre gré)
*  any later version.                   toute version ultérieure.
*
*  OpenCADC is distributed in the       OpenCADC est distribué
*  hope that it will be useful,         dans l’espoir qu’il vous
*  but WITHOUT ANY WARRANTY;            sera utile, mais SANS AUCUNE
*  without even the implied             GARANTIE : sans même la garantie
*  warranty of MERCHANTABILITY          implicite de COMMERCIALISABILITÉ
*  or FITNESS FOR A PARTICULAR          ni d’ADÉQUATION À UN OBJECTIF
*  PURPOSE.  See the GNU Affero         PARTICULIER. Consultez la Licence
*  General Public License for           Générale Publique GNU Affero
*  more details.                        pour plus de détails.
*
*  You should have received             Vous devriez avoir reçu une
*  a copy of the GNU Affero             copie de la Licence Générale
*  General Public License along         Publique GNU Affero avec
*  with OpenCADC.  If not, see          OpenCADC ; si ce n’est
*  <http://www.gnu.org/licenses/>.      pas le cas, consultez :
*                                       <http://www.gnu.org/licenses/>.
*
*  $Revision: 5 $
*
************************************************************************
*/

package ca.nrc.cadc.profiler;

import ca.nrc.cadc.util.Log4jInit;
import java.io.IOException;
import java.io.LineNumberReader;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.junit.Assert;
import org.junit.Test;

/**
 *
 * @author pdowler
 */
public class ProfilerTest 
{
    private static Logger log = Logger.getLogger(ProfilerTest.class);
    
    
    
    public ProfilerTest()
    {
        
    }
    
    private List<String[]> extractProfilerLogs(String logContent)
        throws IOException
    {
        List<String[]> ret = new ArrayList<String[]>();
        LineNumberReader r = new LineNumberReader(new StringReader(logContent));
        String line = r.readLine();
        while ( line != null )
        {
            //System.out.println("extractProfilerLogs: " + line);
            String[] tokens = line.split("[\\s]+");
            String cname = tokens[4];
            if (Profiler.class.getSimpleName().equals(cname))
            {
                String level = tokens[3];
                String msg = tokens[6];
                //System.out.println("extractProfilerLogs: adding " + level + " " + msg);
                ret.add(new String[] { level, msg });
            }
            line = r.readLine();
        }
        return ret;
    }
    
    @Test
    public void testSilent()
    {
        try
        {
            StringWriter logBuffer = new StringWriter();
            Log4jInit.setLevel("ca.nrc.cadc.log", Level.INFO, logBuffer);
        
            Log4jInit.setLevel("ca.nrc.cadc.profiler", Level.ERROR, null);
            Profiler p = new Profiler(ProfilerTest.class);
            p.checkpoint("testSilent");
            Thread.sleep(10L);
            p.checkpoint("testSilent-abc");
            Thread.sleep(10L);
            p.checkpoint("testSilent-def");
            
            Assert.assertEquals(3, p.numOps);
            
            logBuffer.flush();
            List<String[]> out = extractProfilerLogs(logBuffer.toString());
            Assert.assertEquals(0, out.size());
        }
        catch(Exception unexpected)
        {
            log.error("unexpected exception", unexpected);
            Assert.fail("unexpected exception: " + unexpected);
        }
    }
    
    @Test
    public void testEnabledAfterCreated()
    {
        try
        {
            // expect: 6 ops, 3 get logged
            StringWriter logBuffer = new StringWriter();
            Log4jInit.setLevel("ca.nrc.cadc.log", Level.INFO, logBuffer);
            
            Log4jInit.setLevel("ca.nrc.cadc.profiler", Level.ERROR, null);
            Profiler p = new Profiler(ProfilerTest.class);
            
            p.checkpoint("testEnabledAfterCreated");
            Thread.sleep(10L);
            p.checkpoint("testEnabledAfterCreated-abc1");
            Thread.sleep(10L);
            p.checkpoint("testEnabledAfterCreated-def1");
            Thread.sleep(10L);
            
            Log4jInit.setLevel("ca.nrc.cadc.profiler", Level.INFO, null);
            
            p.checkpoint("testEnabledAfterCreated-start2");
            Thread.sleep(10L);
            p.checkpoint("testEnabledAfterCreated-abc2");
            Thread.sleep(10L);
            p.checkpoint("testEnabledAfterCreated-def2");
            
            Assert.assertEquals(6, p.numOps);
            
            logBuffer.flush();
            List<String[]> out = extractProfilerLogs(logBuffer.toString());
            Assert.assertEquals(3, out.size());
            
        }
        catch(Exception unexpected)
        {
            log.error("unexpected exception", unexpected);
            Assert.fail("unexpected exception: " + unexpected);
        }
        finally
        {
            Log4jInit.setLevel("ca.nrc.cadc.profiler", Level.ERROR, null);
        }
    }
    
    @Test
    public void testEnabledBeforeCreated()
    {
        try
        {
            StringWriter logBuffer = new StringWriter();
            Log4jInit.setLevel("ca.nrc.cadc.log", Level.INFO, logBuffer);
            
            Log4jInit.setLevel("ca.nrc.cadc.profiler", Level.INFO, null);
            Profiler p = new Profiler(ProfilerTest.class);
            
            p.checkpoint("testEnabled-start2");
            Thread.sleep(10L);
            p.checkpoint("testEnabled-abc2");
            Thread.sleep(10L);
            p.checkpoint("testEnabled-def2");
            
            Assert.assertEquals(3, p.numOps);
            
            logBuffer.flush();
            List<String[]> out = extractProfilerLogs(logBuffer.toString());
            Assert.assertEquals(3, out.size());
            
        }
        catch(Exception unexpected)
        {
            log.error("unexpected exception", unexpected);
            Assert.fail("unexpected exception: " + unexpected);
        }
        finally
        {
            Log4jInit.setLevel("ca.nrc.cadc.profiler", Level.ERROR, null);
        }
    }
}
