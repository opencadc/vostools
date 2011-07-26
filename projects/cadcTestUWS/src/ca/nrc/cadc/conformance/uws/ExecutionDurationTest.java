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

package ca.nrc.cadc.conformance.uws;

import org.junit.Assert;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.junit.Test;

import ca.nrc.cadc.util.Log4jInit;

import com.meterware.httpunit.PostMethodWebRequest;
import com.meterware.httpunit.WebConversation;
import com.meterware.httpunit.WebRequest;
import com.meterware.httpunit.WebResponse;

public class ExecutionDurationTest extends AbstractUWSTest
{
    private static Logger log = Logger.getLogger(ExecutionDurationTest.class);

    static
    {
        Log4jInit.setLevel("ca.nrc.cadc", Level.INFO);
    }

    private static final String EXECUTIONDURATION = "99999";

    public ExecutionDurationTest()
    {
        super();
    }

    /*
     * Create a new Job, then update and verify the executionduration.
     */
    @Test
    public void testExecutionDuration()
    {
        try
        {
            // Create a new Job.
            WebConversation conversation = new WebConversation();
            String jobId = createJob(conversation);

            // POST request to the destruction resource.
            String resourceUrl = serviceUrl + "/" + jobId + "/executionduration";
            WebRequest postRequest = new PostMethodWebRequest(resourceUrl);
            postRequest.setParameter("EXECUTIONDURATION", EXECUTIONDURATION);
            postRequest.setHeaderField("Content-Type", "application/x-www-form-urlencoded");
            WebResponse response = post(conversation, postRequest);

            // Get the redirect.
            String location = response.getHeaderField("Location");
            log.debug("Location: " + location);
            Assert.assertNotNull("POST response to " + resourceUrl + " location header not set", location);

            // Follow the redirect.
            response = get(conversation, location);

            // Validate the XML against the schema.
            log.debug("XML:\r\n" + response.getText());
            buildDocument(response.getText(), true);

            // Get the executionduration resouce for this jobId.
            response = get(conversation, resourceUrl, "text/plain");

            log.debug(Util.getResponseHeaders(response));
            log.debug("Response.getText():\r\n" + response.getText());

            Assert.assertEquals("GET response Content-Type header to " + resourceUrl + " is incorrect",
                    "text/plain", response.getContentType());

            String str = response.getText();
            if (str != null)
                str = str.trim();
            try
            {
                Long dur = new Long(str);

            }
            catch(NumberFormatException ex)
            {
                Assert.fail("expected execution duration to be an integer value, got: " + str);
            }

            // Delete the job.
            deleteJob(conversation, jobId);
        }
        catch (Exception ex)
        {
            log.error("unexpected exception", ex);
            Assert.fail("unexpected exception: " + ex);
        }
    }

}
