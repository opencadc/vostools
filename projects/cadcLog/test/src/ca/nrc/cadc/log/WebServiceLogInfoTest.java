/*
************************************************************************
*******************  CANADIAN ASTRONOMY DATA CENTRE  *******************
**************  CENTRE CANADIEN DE DONNÉES ASTRONOMIQUES  **************
*
*  (c) 2009.                            (c) 2009.
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
*  $Revision: 4 $
*
************************************************************************
*/
package ca.nrc.cadc.log;

import ca.nrc.cadc.auth.HttpPrincipal;
import javax.security.auth.Subject;
import javax.servlet.http.HttpServletRequest;
import junit.framework.Assert;
import org.easymock.EasyMock;
import org.junit.Test;

public class WebServiceLogInfoTest
{
    
    @Test
    public void testMinimalContentServlet()
    {
        HttpServletRequest request = EasyMock.createMock(HttpServletRequest.class);
        EasyMock.expect(request.getMethod()).andReturn("Get").once();
        EasyMock.expect(request.getPathInfo()).andReturn("/path/of/request").once();
        EasyMock.expect(request.getRemoteAddr()).andReturn("192.168.0.0").once();
        
        EasyMock.replay(request);
        
        WebServiceLogInfo logInfo = new ServletLogInfo(request);
        String start = logInfo.start();
        String end = logInfo.end();
        Assert.assertEquals("Wrong start", "START: {\"method\":\"GET\",\"path\":\"/path/of/request\",\"from\":\"192.168.0.0\"}", start);
        Assert.assertEquals("Wrong end", "END: {\"method\":\"GET\",\"path\":\"/path/of/request\",\"success\":true,\"from\":\"192.168.0.0\"}", end);
        
        EasyMock.verify(request);
    }
    
    @Test
    public void testMaximalContentServlet()
    {
        HttpServletRequest request = EasyMock.createMock(HttpServletRequest.class);
        EasyMock.expect(request.getMethod()).andReturn("Get").once();
        EasyMock.expect(request.getPathInfo()).andReturn("/path/of/request").once();
        EasyMock.expect(request.getRemoteAddr()).andReturn("192.168.0.0").once();
        
        EasyMock.replay(request);
        
        WebServiceLogInfo logInfo = new ServletLogInfo(request);
        String start = logInfo.start();
        Assert.assertEquals("Wrong start", "START: {\"method\":\"GET\",\"path\":\"/path/of/request\",\"from\":\"192.168.0.0\"}", start);
        logInfo.setSuccess(false);
        logInfo.setSubject(createSubject("the user"));
        logInfo.setElapsedTime(1234L);
        logInfo.setBytes(10L);
        logInfo.setMessage("the message");
        String end = logInfo.end();
        Assert.assertEquals("Wrong end", "END: {\"method\":\"GET\",\"path\":\"/path/of/request\",\"success\":false,\"user\":\"the user\",\"from\":\"192.168.0.0\",\"time\":1234,\"bytes\":10,\"message\":\"the message\"}", end);
        EasyMock.verify(request);
    }
    
    private Subject createSubject(String userid)
    {
        Subject s = new Subject();
        HttpPrincipal p = new HttpPrincipal(userid);
        s.getPrincipals().add(p);
        return s;
    }

}
