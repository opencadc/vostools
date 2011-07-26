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
*  with OpenCADC.  If not, see          OpenCADC ; si ce n’esttestDelete
*  <http://www.gnu.org/licenses/>.      pas le cas, consultez :
*                                       <http://www.gnu.org/licenses/>.
*
*  $Revision: 4 $
*
************************************************************************
*/

package ca.nrc.cadc.conformance.uws;

import org.junit.Assert;

import java.util.Date;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.junit.Test;

import ca.nrc.cadc.date.DateUtil;
import ca.nrc.cadc.util.Log4jInit;

import com.meterware.httpunit.PostMethodWebRequest;
import com.meterware.httpunit.WebConversation;
import com.meterware.httpunit.WebRequest;
import com.meterware.httpunit.WebResponse;
import java.text.DateFormat;

public class DestructionTest extends AbstractUWSTest
{
    private static Logger log = Logger.getLogger(DestructionTest.class);

    static
    {
        Log4jInit.setLevel("ca.nrc.cadc", Level.INFO);
    }

    // Destruction date passed to UWS service.
    DateFormat dateFormat = DateUtil.getDateFormat( DateUtil.IVOA_DATE_FORMAT, DateUtil.UTC);

    public DestructionTest()
    {
        super();
    }

    /**
     * Create a new Job, then update and verify the Destruction date.
     */
    @Test
    public void testDestruction()
    {
        try
        {
            // Create a new Job.
            WebConversation conversation = new WebConversation();
            WebResponse response;
            String jobId = createJob(conversation);
            String resourceUrl = serviceUrl + "/" + jobId + "/destruction";

            // Get the destruction resource for this jobId.
            response = get(conversation, resourceUrl, "text/plain");
            log.debug(Util.getResponseHeaders(response));
            log.debug("Response.getText():\r\n" + response.getText());
            Assert.assertEquals("GET response Content-Type header to " + resourceUrl + " is incorrect",
                    "text/plain", response.getContentType());
            String str = response.getText();
            if (str != null)
                str = str.trim();
            Date origDestruction = dateFormat.parse(str);


            Date now = new Date();
            long t1 = now.getTime();
            long t2 = origDestruction.getTime();
            Assert.assertTrue("default destruction in future", (t1 < t2));
            Date destroy = new Date((t1 + t2)/2); // request mid way between
            String destruction = dateFormat.format(destroy);

            // POST request to the destruction resource.
            WebRequest postRequest = new PostMethodWebRequest(resourceUrl);
            postRequest.setParameter("DESTRUCTION", destruction);
            postRequest.setHeaderField("Content-Type", "application/x-www-form-urlencoded");
            response = post(conversation, postRequest);

            // Get the redirect.
            String location = response.getHeaderField("Location");
            log.debug("Location: " + location);
            Assert.assertNotNull("POST response to " + resourceUrl + " location header not set", location);
            response = get(conversation, location);

            // Validate the XML against the schema.
            log.debug("XML:\r\n" + response.getText());
            buildDocument(response.getText(), true);

            // Get the destruction resource for this jobId.
            response = get(conversation, resourceUrl, "text/plain");
            log.debug(Util.getResponseHeaders(response));
            log.debug("Response.getText():\r\n" + response.getText());
            Assert.assertEquals("GET response Content-Type header to " + resourceUrl + " is incorrect",
                    "text/plain", response.getContentType());
            str = response.getText();
            if (str != null)
                str = str.trim();
            
            Date rtnDestr = dateFormat.parse(str);
            long dt = rtnDestr.getTime() - destroy.getTime();
            if (dt < 0)
                dt *= -1L;
            Assert.assertTrue("result destruction is approx requested value", (dt < 2L));
            
            // Delete the job.
            deleteJob(conversation, jobId);

            log.info("DestructionTest.testDestruction completed.");
        }
        catch (Exception ex)
        {
            log.error("unexpected exception", ex);
            Assert.fail("unexpected exception: " + ex);
        }
    }

}
