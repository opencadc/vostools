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

package ca.nrc.cadc.uws;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;
import java.security.Principal;
import java.util.Set;

import javax.security.auth.Subject;

import org.apache.log4j.Logger;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.output.Format;
import org.jdom.output.XMLOutputter;

import ca.nrc.cadc.xml.XmlUtil;
import java.io.Reader;
import java.io.StringReader;
import java.util.List;
import org.jdom.input.SAXBuilder;

/**
 * Writes a Job as XML to an output.
 * 
 * @author Sailor Zhang
 */
public class JobWriter
{
    private static Logger log = Logger.getLogger(JobWriter.class);

    private Job job;
    private Document document;
    private XMLOutputter outputter;

    public JobWriter(Job job)
    {
        this.job = job;
        buildDocument();
        this.outputter = new XMLOutputter();
        this.outputter.setFormat(Format.getPrettyFormat());
    }
    
    /**
     * Write the job to a String.
     */
    @Override
    public String toString()
    {
        return outputter.outputString(document);
    }

    /**
     * Write the job to an OutputStream.
     *
     * @param out OutputStream to write to.
     * @throws IOException if the writer fails to write.
     */
    public void writeTo(OutputStream out)
        throws IOException
    {
        outputter.output(document, out);
    }

    /**
     * Write the job to a writer.
     *
     * @param writer Writer to write to.
     * @throws IOException if the writer fails to write.
     */
    public void writeTo(Writer writer)
        throws IOException
    {
        outputter.output(document, writer);
    }

    /**
     * Get the JDOM Document representing this Job.
     *
     * @return The JDOM Document.
     */
    public Document getDocument()
    {
        return document;
    }

    /**
     * Get an Element representing a job Element.
     *
     * @return A job Element.
     */
    public static Element getJob()
    {
        Element element = new Element(JobAttribute.JOB.getAttributeName(), UWS.NS);
        element.addNamespaceDeclaration(UWS.NS);
        element.addNamespaceDeclaration(UWS.XLINK_NS);
        //element.setAttribute("schemaLocation", "http://www.ivoa.net/xml/UWS/v1.0 UWS.xsd", UWS.XSI_NS);
        return element;
    }

    /**
     * Get an Element representing a jobs Element.
     *
     * @return A jobs element.
     */
    //public static Element getJobs()
    //{
    //    Element element = new Element(JobAttribute.JOBS.getAttributeName(), UWS.NS);
    //    element.addNamespaceDeclaration(UWS.NS);
    //    element.addNamespaceDeclaration(UWS.XLINK_NS);
    //    //element.setAttribute("schemaLocation", "http://www.ivoa.net/xml/UWS/v1.0 UWS.xsd", UWS.XSI_NS);
    //    return element;
    //}
    
    /**
     * Get an Element representing the Job jobId.
     *
     * @return The Job jobId Element.
     */
    public Element getJobId()
    {
        Element element = new Element(JobAttribute.JOB_ID.getAttributeName(), UWS.NS);
        element.addContent(job.getID());
        return element;
    }

    /**
     * Get an Element representing the Job jobref.
     *
     * @param host The host part of the Job request URL.
     * @return The Job jobref Element.
     */
    public Element getJobRef(String host)
    {
        Element element = new Element(JobAttribute.JOB_REF.getAttributeName(), UWS.NS);
        element.setAttribute("id", job.getID());
        element.setAttribute("xlink:href", host + job.getRequestPath() + "/" + job.getID());
        return element;
    }

    /**
     * Get an Element representing the Job runId.
     *
     * @return The Job runId Element.
     */
    public Element getRunId()
    {
        Element element = new Element(JobAttribute.RUN_ID.getAttributeName(), UWS.NS);
        element.addContent(job.getRunID());
        return element;
    }

    /**
     * Get an Element representing the Job ownerId.
     *
     * @return The Job ownerId Element.
     */
    public Element getOwnerId()
    {
        Element element = new Element(JobAttribute.OWNER_ID.getAttributeName(), UWS.NS);
        Subject subjectOwner = job.getOwner();
        boolean nil = true;
        if (subjectOwner != null)
        {
            Set<Principal> setPrincipal = subjectOwner.getPrincipals();
            for (Principal prc : setPrincipal)
            {
                element.addContent(prc.getName());
                nil = false;
                break; // a convenient way to get the first principal in the set ONLY.
            }
        }
        if (nil)
            element.setAttribute("nil", "true", UWS.XSI_NS);
        return element;
    }

    /**
     * Get an Element representing the Job phase.
     *
     * @return The Job phase Element.
     */
    public Element getPhase()
    {
        Element element = new Element(JobAttribute.EXECUTION_PHASE.getAttributeName(), UWS.NS);
        element.addContent(job.getExecutionPhase().toString());
        return element;
    }

    /**
     * Get an Element representing the Job quote.
     *
     * @return The Job quote Element.
     */
    public Element getQuote()
    {
        Element element = new Element(JobAttribute.QUOTE.getAttributeName(), UWS.NS);
        XmlUtil.addElementContent(element, job.getQuote(), true);
        return element;
    }

    /**
     * Get an Element representing the Job startTime.
     *
     * @return The Job startTime Element.
     */
    public Element getStartTime()
    {
        Element element = new Element(JobAttribute.START_TIME.getAttributeName(), UWS.NS);
        XmlUtil.addElementContent(element, job.getStartTime(), true);
        return element;
    }

    /**
     * Get an Element representing the Job endTime.
     *
     * @return The Job endTime Element.
     */
    public Element getEndTime()
    {
        Element element = new Element(JobAttribute.END_TIME.getAttributeName(), UWS.NS);
        XmlUtil.addElementContent(element, job.getEndTime(), true);
        return element;
    }

    /**
     * Get an Element representing the Job executionDuration.
     *
     * @return The Job executionDuration Element.
     */
    public Element getExecutionDuration()
    {
        Element element = new Element(JobAttribute.EXECUTION_DURATION.getAttributeName(), UWS.NS);
        element.addContent(Long.toString(job.getExecutionDuration()));
        return element;
    }

    /**
     * Get an Element representing the Job destruction.
     *
     * @return The Job destruction Element.
     */
    public Element getDestruction()
    {
        Element element = new Element(JobAttribute.DESTRUCTION_TIME.getAttributeName(), UWS.NS);
        XmlUtil.addElementContent(element, job.getDestructionTime(), true);
        return element;
    }

    /**
     * Get an Element representing the Job parameters.
     *
     * @return The Job parameters Element.
     */
    public Element getParameters()
    {
        Element element = new Element(JobAttribute.PARAMETERS.getAttributeName(), UWS.NS);
        for (Parameter parameter : job.getParameterList())
        {
            Element e = new Element(JobAttribute.PARAMETER.getAttributeName(), UWS.NS);
            e.setAttribute("id", parameter.getName());
            e.addContent(parameter.getValue());
            element.addContent(e);
        }
        return element;
    }

    /**
     * Get an Element representing the Job results.
     *
     * @return The Job results Element.
     */
    public Element getResults()
    {
        Element element = new Element(JobAttribute.RESULTS.getAttributeName(), UWS.NS);
        for (Result result : job.getResultsList())
        {
            Element e = new Element(JobAttribute.RESULT.getAttributeName(), UWS.NS);
            e.setAttribute("id", result.getName());
            e.setAttribute("href", result.getURL().toString(), UWS.XLINK_NS);
            element.addContent(e);
        }
        return element;
    }

    /**
     * Get an Element representing the Job errorSummary.
     *
     * @return The Job errorSummary Element.
     */
    public Element getErrorSummary()
    {
        Element eleErrorSummary = null;
        ErrorSummary es = job.getErrorSummary();
        if (es != null)
        {
            eleErrorSummary = new Element(JobAttribute.ERROR_SUMMARY.getAttributeName(), UWS.NS);
            eleErrorSummary.setAttribute("type", es.getErrorType().toString().toLowerCase());
            String hasDetail = "false";
            if (job.getErrorSummary().getDocumentURL() != null)
                hasDetail = "true";
            eleErrorSummary.setAttribute("hasDetail", hasDetail);

            Element eleMessage = new Element(JobAttribute.ERROR_SUMMARY_MESSAGE.getAttributeName(), UWS.NS);
            eleMessage.addContent(job.getErrorSummary().getSummaryMessage());
            eleErrorSummary.addContent(eleMessage);
            
//            URL esDocUrl = job.getErrorSummary().getDocumentURL();
//            if (esDocUrl != null)
//            {
//                Element eleDetail = new Element(JobAttribute.ERROR_SUMMARY_DETAIL_LINK.getAttributeName(), UWS.NS);
//                eleDetail.setAttribute("href", esDocUrl.toString(), UWS.XLINK_NS);
//                eleErrorSummary.addContent(eleDetail);
//            }
        }
        return eleErrorSummary;
    }
    
    /**
     * Get an Element representing the Job jobInfo.
     *
     * @return The Job jobInfo Element.
     */
    public Element getJobInfo()
    {
        Element element = null;
        JobInfo jobInfo = job.getJobInfo();
        if (jobInfo != null)
        {
            element = new Element(JobAttribute.JOB_INFO.getAttributeName(), UWS.NS);
            if (jobInfo.getContent() != null)
            {
                try
                {
                    // The JobInfo content can't be validate since the schema(s) aren't known.
                    SAXBuilder builder = new SAXBuilder("org.apache.xerces.parsers.SAXParser", false);
                    Document doc = builder.build(new StringReader(jobInfo.getContent()));
                    element.addContent(doc.getRootElement().detach()); 
                }
                catch (Exception e)
                {
                    log.error(e);
                }                               
            }                                   
        }
        return element;
    }

    /**
     * Build XML Document for the job
     */
    private void buildDocument()
    {
        Element root = getJob();
        root.addContent(getJobId());
        root.addContent(getRunId());
        root.addContent(getOwnerId());
        root.addContent(getPhase());
        root.addContent(getQuote());
        root.addContent(getStartTime());
        root.addContent(getEndTime());
        root.addContent(getExecutionDuration());
        root.addContent(getDestruction());
        root.addContent(getParameters());
        root.addContent(getResults());
        Element errorSummary = getErrorSummary();
        if (errorSummary != null)
            root.addContent(errorSummary);
        Element jobInfo = getJobInfo();
        if (jobInfo != null)
            root.addContent(jobInfo);

        document = new Document(root);
    }
    
}
    