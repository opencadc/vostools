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

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.jdom2.Attribute;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;
import org.junit.Before;
import org.junit.Test;

import ca.nrc.cadc.util.Log4jInit;

import com.meterware.httpunit.PostMethodWebRequest;
import com.meterware.httpunit.WebConversation;
import com.meterware.httpunit.WebRequest;
import com.meterware.httpunit.WebResponse;

public class ResultsTest extends AbstractUWSTest
{
    protected static TestPropertiesList testPropertiesList;

    private static Logger log = Logger.getLogger(ResultsTest.class);

    static
    {
        Log4jInit.setLevel("ca.nrc.cadc", Level.INFO);
    }

    private static final String CLASS_NAME = "ResultsTest";

    public ResultsTest()
    {
        super();
    }

    @Before
    public void before()
    {
        String propertiesDirectory = System.getProperty("properties.directory");
        if (propertiesDirectory == null)
            fail("properties.directory System property not set");
        try
        {
            testPropertiesList = new TestPropertiesList(propertiesDirectory, CLASS_NAME);
        }
        catch (IOException e)
        {
            log.error(e);
            fail(e.getMessage());
        }
    }

    @Test
    public void testResults()
    {
        try
        {
            if (testPropertiesList.propertiesList.size() == 0)
                fail("missing properties file for " + CLASS_NAME);

            // For each properties file.
            for (TestProperties properties : testPropertiesList.propertiesList)
            {
                log.debug("**************************************************");
                log.debug("processing properties file: " + properties.filename);
                log.debug(properties);
                log.debug("**************************************************");

                // see if there are realm/userid/password preconditions
                String realm = null;
                String userid = null;
                String password = null;
                if (properties.preconditions != null)
                {
                    if (properties.preconditions.containsKey("Realm"))
                        realm = properties.preconditions.get("Realm").get(0);
                    if (properties.preconditions.containsKey("Userid"))
                        userid = properties.preconditions.get("Userid").get(0);
                    if (properties.preconditions.containsKey("Password"))
                        password = properties.preconditions.get("Password").get(0);
                }
                
                // Create a new Job.
                WebConversation conversation = new WebConversation();
                if (userid != null && password != null && realm != null)
                    conversation.setAuthentication(realm, userid, password);
                String jobId = createJob(conversation, properties.parameters);

                // POST request to the phase resource.
                String resourceUrl = serviceUrl + "/" + jobId + "/phase";
                WebRequest postRequest = new PostMethodWebRequest(resourceUrl);
                postRequest.setParameter("PHASE", "RUN");
                WebResponse response = post(conversation, postRequest);

                // Get the redirect.
                String location = response.getHeaderField("Location");
                log.debug("Location: " + location);
                assertNotNull(properties.filename + " POST response to " + resourceUrl + " location header not set", location);
    //            assertEquals(propertiesFilename + " POST response to " + resourceUrl + " location header incorrect", baseUrl + "/" + jobId, location);

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
                long dt = 2000L;
                while (!done)
                {
                    // Wait
                    Thread.sleep(dt);

                    // GET the resource.
                    response = get(conversation, resourceUrl);

                    // Create DOM document from XML.
                    log.debug("XML:\r\n" + response.getText());
                    Document document = buildDocument(response.getText(), false);

                    // Root element of the document.
                    root = document.getRootElement();
                    assertNotNull(properties.filename + " no XML returned from GET of " + resourceUrl, root);

                    // Get the phase element.
                    list = root.getChildren("phase", namespace);
                    assertEquals(properties.filename + " phase element should only have a single element in XML returned from GET of " + resourceUrl, 1, list.size());
                    Element phase = (Element) list.get(0);
                    String phaseText = phase.getText();

                    // Check if request timeout exceeded.
                    if ((System.currentTimeMillis() - start) > (REQUEST_TIMEOUT * 1000))
                        fail(properties.filename + " request timeout exceeded in GET of " + resourceUrl);

                    // COMPLETED phase, continue with test.
                    if (phaseText.equals("COMPLETED"))
                        break;

                    // Fail if phase is ERROR or ABORTED.
                    else if (phaseText.equals("ERROR") || phaseText.equals("ABORTED"))
                        fail(properties.filename + " phase should not be " + phaseText + ", in XML returned from GET of " + resourceUrl);

                    // Check phase, if still PENDING or QUEUED after x seconds, fail.
                    else if (phaseText.equals("PENDING") || phaseText.equals("QUEUED") || phaseText.equals("EXECUTING"))
                        continue;
                }

                // Get the results element.
                list = root.getChildren("results", namespace);
                assertEquals(properties.filename + " uws:results element should only have a single element in XML returned from GET of " + resourceUrl, 1, list.size());

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

                // Validate the result urls content.
                validateResults(resultUrls);

                deleteJob(conversation, jobId);

                log.debug("ResultsTest.testResults completed.");
            }
        }
        catch (Exception unexpected)
        {
            log.error("unexpected exception", unexpected);
            fail("unexpected exception: " + unexpected);
        }
    }

    protected void validateResults(List<URL> resultUrls) { }

}
