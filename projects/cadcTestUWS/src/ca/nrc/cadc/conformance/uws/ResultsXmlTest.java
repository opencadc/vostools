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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.log4j.Logger;
import org.jdom2.Attribute;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;
import org.junit.Test;

import com.meterware.httpunit.PostMethodWebRequest;
import com.meterware.httpunit.WebConversation;
import com.meterware.httpunit.WebRequest;
import com.meterware.httpunit.WebResponse;

/**
 * For each file in the specified testing directory with specified prefix,
 * create job and test whether the job goes properly.
 *
 * @author zhangsa
 */
public class ResultsXmlTest extends AbstractUWSXmlTest
{
    protected static Logger log = Logger.getLogger(ResultsXmlTest.class);
    protected static final String XML_TEST_FILE_PREFIX = "ResultsTest";

    public ResultsXmlTest()
    {
        super(XML_TEST_FILE_PREFIX);
    }
    
    @Test
    public void testResults()
    {
        super.testFileList();
    }

    /**
     * This is the actual test implentation.
     * 
     * @param xml:  XML string to be posted to the SampleUWS Server.
     * 
     */
    protected void testImpl(String xml) throws Exception
    {
        
        WebConversation conversation = new WebConversation();
        String jobId = createJob(conversation, xml);

        // POST request to the phase resource.
        String resourceUrl = serviceUrl + "/" + jobId + "/phase";
        WebRequest postRequest = new PostMethodWebRequest(resourceUrl);
        postRequest.setParameter("PHASE", "RUN");
        WebResponse response = post(conversation, postRequest);

        // Get the redirect.
        String location = response.getHeaderField("Location");
        log.debug("Location: " + location);
        assertNotNull(" POST response to " + resourceUrl + " location header not set", location);

        // Follow the redirect.
        response = get(conversation, location);

        // Validate the XML against the schema.
        log.debug("XML:\r\n" + response.getText());
        buildDocument(response.getText(), true);

        // Job resource for this jobId.
        resourceUrl = serviceUrl + "/" + jobId;

        // Loop until the phase is either COMPLETED, ERROR or ABORTED.
        Element root = null;
        List list = null;
        Namespace namespace = null;
        boolean done = false;
        Long start = System.currentTimeMillis();
        while (!done)
        {
            // Wait for 1 second.
            Thread.sleep(1000);

            // GET the resource.
            response = get(conversation, resourceUrl);

            // Create DOM document from XML.
            log.debug("XML:\r\n" + response.getText());
            Document document = buildDocument(response.getText(), false);

            // Root element of the document.
            root = document.getRootElement();
            assertNotNull(" no XML returned from GET of " + resourceUrl, root);

            // Get the phase element.
            list = root.getChildren("phase", namespace);
            assertEquals(
                    " phase element should only have a single element in XML returned from GET of "
                            + resourceUrl, 1, list.size());
            Element phase = (Element) list.get(0);
            String phaseText = phase.getText();

            // Check if request timeout exceeded.
            if ((System.currentTimeMillis() - start) > (REQUEST_TIMEOUT * 1000))
                fail(" request timeout exceeded in GET of " + resourceUrl);

            // COMPLETED phase, continue with test.
            if (phaseText.equals("COMPLETED"))
                break;

            // Fail if phase is ERROR or ABORTED.
            else if (phaseText.equals("ERROR") || phaseText.equals("ABORTED"))
                fail(" phase should not be " + phaseText + ", in XML returned from GET of "
                        + resourceUrl);

            // Check phase, if still PENDING or QUEUED after x seconds, fail.
            else if (phaseText.equals("PENDING") || phaseText.equals("QUEUED")
                    || phaseText.equals("EXECUTING")) continue;
        }

        // Get the results element.
        list = root.getChildren("results", namespace);
        assertEquals(
                " uws:results element should only have a single element in XML returned from GET of "
                        + resourceUrl, 1, list.size());

        // Get the list of result elements.
        Element results = (Element) list.get(0);
        list = results.getChildren("result", namespace);

        // Get a List of URL's for the result href attribute.
        List<URL> resultUrls = new ArrayList<URL>();
        for (Iterator it = list.iterator(); it.hasNext();)
        {
            Element element = (Element) it.next();
            List attributes = element.getAttributes();
            for (Iterator itt = attributes.iterator(); itt.hasNext();)
            {
                Attribute attribute = (Attribute) itt.next();
                if (attribute.getNamespacePrefix().equals("xlink")
                        && attribute.getName().equals("href"))
                {
                    try
                    {
                        // Try and create an URL from the href and add to list.
                        URL url = new URL(attribute.getValue());
                        resultUrls.add(url);
                    }
                    catch (MalformedURLException mue)
                    {
                        log.error(mue);
                        fail(mue.getMessage());
                    }
                }
            }
        }

        // Do a HEAD request on each result url.
        for (URL url : resultUrls)
        {
            head(conversation, url.toString());
        }
        deleteJob(conversation, jobId);
    }

}
