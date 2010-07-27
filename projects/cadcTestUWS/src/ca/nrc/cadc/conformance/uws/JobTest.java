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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import com.meterware.httpunit.WebConversation;
import com.meterware.httpunit.WebResponse;
import java.util.List;
import org.apache.log4j.Logger;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.Namespace;
import org.junit.Test;
import static org.junit.Assert.*;

public class JobTest extends AbstractUWSTest
{
    private static Logger log = Logger.getLogger(JobTest.class);

    public JobTest()
    {
        super();
        setLoggingLevel(log);
    }

    @Test
    public void testCreateDefaultJob()
    {
        try
        {
            // Create a new Job.
            WebConversation conversation = new WebConversation();
            String jobId = createJob(conversation);

            // GET request to the jobId resource.
            String resourceUrl = serviceUrl + "/" + jobId;
            WebResponse response = get(conversation, resourceUrl);

            // Validate the XML against the schema.
            log.debug("XML:\r\n" + response.getText());
            Document document = buildDocument(response.getText(), true);

            // Get the document root.
            Element root = document.getRootElement();
            assertNotNull("XML returned from GET of " + resourceUrl + " missing root element", root);
            Namespace namespace = root.getNamespace();
            log.debug("namespace: " + namespace);

            // List of jobId elements.
            List list = root.getChildren("jobId", namespace);
            assertNotNull("XML returned from GET of " + resourceUrl + " missing uws:jobId element", list);

            // Validate the jobId.
            Element element = (Element) list.get(0);
            log.debug("uws:jobId: " + element.getText());
            assertEquals("Incorrect uws:jobId element in XML returned from GET of " + resourceUrl, jobId, element.getText());

            // Delete the job.
            deleteJob(conversation, jobId);

            log.info("JobTest.testCreateDefaultJob completed.");
        }
        catch (Throwable t)
        {
            log.error(t);
            fail(t.getMessage());
        }
    }

    @Test
    public void testCreateJobViaParameters()
    {
        try
        {
            // Create Map of Job parameters.
            Map<String, List<String>> parameters = new HashMap<String, List<String>>();
            List values = new ArrayList<String>();
            values.add("runId value");
            parameters.put("runId", values);

            // Create a new Job.
            WebConversation conversation = new WebConversation();
            String jobId = createJob(conversation, parameters);

            // GET request to the jobId resource.
            String resourceUrl = serviceUrl + "/" + jobId;
            WebResponse response = get(conversation, resourceUrl);

            // Validate the XML against the schema.
            log.debug("XML:\r\n" + response.getText());
            Document document = buildDocument(response.getText(), true);

            // Get the document root.
            Element root = document.getRootElement();
            assertNotNull("XML returned from GET of " + resourceUrl + " missing root element", root);
            Namespace namespace = root.getNamespace();
            log.debug("namespace: " + namespace);

            // List of jobId elements.
            List list = root.getChildren("jobId", namespace);
            assertNotNull("XML returned from GET of " + resourceUrl + " missing uws:jobId element", list);

            // Validate the jobId.
            Element element = (Element) list.get(0);
            log.debug("uws:jobId: " + element.getText());
            assertEquals("Incorrect uws:jobId element in XML returned from GET of " + resourceUrl, jobId, element.getText());

            // Delete the job.
            deleteJob(conversation, jobId);

            log.info("JobTest.testCreateJobViaParameters completed.");
        }
        catch (Throwable t)
        {
            log.error(t);
            fail(t.getMessage());
        }
    }

    @Test
    public void testCreateJobViaXML()
    {
        try
        {
            StringBuilder sb = new StringBuilder();
            sb.append("<uws:job ");
            sb.append("xmlns:uws=\"http://www.ivoa.net/xml/UWS/v1.0\" ");
            sb.append("xmlns:xlink=\"http://www.w3.org/1999/xlink\" ");
            sb.append("xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" ");
            sb.append("xsi:schemaLocation=\"http://www.ivoa.net/xml/UWS/v1.0 UWS.xsd \" ");
            sb.append(">\n");
            sb.append("  <uws:ownerId xsi:nil=\"true\">ownerId value</uws:ownerId>\n");
            sb.append("  <uws:phase>PENDING</uws:phase>");
            sb.append("  <uws:startTime xsi:nil=\"true\"/>");
            sb.append("  <uws:endTime xsi:nil=\"true\"/>");
            sb.append("  <uws:executionDuration>0</uws:executionDuration>");
            sb.append("  <uws:destruction xsi:nil=\"true\"/>");
            sb.append("  <uws:quote xsi:nil=\"true\"/>");
            sb.append("  <uws:parameters>\n");
            sb.append("    <uws:parameter id=\"parameter 1\">parameter 1 value</uws:parameter>\n");
            sb.append("    <uws:parameter id=\"parameter 2\">parameter 2 value</uws:parameter>\n");
            sb.append("  </uws:parameters>\n");
            sb.append("</uws:job>");

            // Create a new Job.
            WebConversation conversation = new WebConversation();
            String jobId = createJob(conversation, sb.toString());

            // GET request to the jobId resource.
            String resourceUrl = serviceUrl + "/" + jobId;
            WebResponse response = get(conversation, resourceUrl);

            // Validate the XML against the schema.
            log.debug("XML:\r\n" + response.getText());
            Document document = buildDocument(response.getText(), true);

            // Get the document root.
            Element root = document.getRootElement();
            assertNotNull("XML returned from GET of " + resourceUrl + " missing root element", root);
            Namespace namespace = root.getNamespace();
            log.debug("namespace: " + namespace);

            // List of jobId elements.
            List list = root.getChildren("jobId", namespace);
            assertNotNull("XML returned from GET of " + resourceUrl + " missing uws:jobId element", list);

            // Validate the jobId.
            Element element = (Element) list.get(0);
            log.debug("uws:jobId: " + element.getText());
            assertEquals("Incorrect uws:jobId element in XML returned from GET of " + resourceUrl, jobId, element.getText());

            // Delete the job.
            deleteJob(conversation, jobId);

            log.info("JobTest.testCreateJobViaXML completed.");
        }
        catch (Throwable t)
        {
            log.error(t);
            fail(t.getMessage());
        }
    }

}
