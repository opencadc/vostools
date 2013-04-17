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
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.text.DateFormat;
import java.util.Date;

import org.apache.log4j.Logger;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.output.Format;
import org.jdom2.output.XMLOutputter;

import ca.nrc.cadc.date.DateUtil;
import ca.nrc.cadc.uws.util.XmlUtil;

/**
 * Writes a Job as XML to an output.
 * 
 * @author Sailor Zhang
 */
public class JobWriter
{
    private static Logger log = Logger.getLogger(JobWriter.class);

    private DateFormat dateFormat;

    public JobWriter() 
    {
        this.dateFormat = DateUtil.getDateFormat(DateUtil.IVOA_DATE_FORMAT, DateUtil.UTC);
    }
    
    /**
     * Write to root Element to a writer.
     *
     * @param root Root Element to write.
     * @param writer Writer to write to.
     * @throws IOException if the writer fails to write.
     */
    protected void writeDocument(Element root, Writer writer)
        throws IOException
    {
        XMLOutputter outputter = new XMLOutputter();
        outputter.setFormat(Format.getPrettyFormat());
        Document document = new Document(root);
        outputter.output(document, writer);
    }

    /**
     * Write the job to an OutputStream.
     *
     * @param job
     * @param out OutputStream to write to.
     * @throws IOException if the writer fails to write.
     */
    public void write(Job job, OutputStream out)
        throws IOException
    {
        write(job, new OutputStreamWriter(out));
    }

    /**
     * Write the job to a writer.
     *
     * @param job
     * @param writer Writer to write to.
     * @throws IOException if the writer fails to write.
     */
    public void write(Job job, Writer writer)
        throws IOException
    {
        Element root = getRootElement(job);
        XMLOutputter outputter = new XMLOutputter();
        outputter.setFormat(Format.getPrettyFormat());
        Document document = new Document(root);
        outputter.output(document, writer);
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
     * Create the root element of a job document.
     * @param job
     * @return
     */
    public Element getRootElement(Job job)
    {
        Element root = new Element(JobAttribute.JOB.getAttributeName(), UWS.NS);
        root.addNamespaceDeclaration(UWS.NS);
        root.addNamespaceDeclaration(UWS.XLINK_NS);

        root.addContent(getJobId(job));
        root.addContent(getRunId(job));
        root.addContent(getOwnerId(job));
        root.addContent(getPhase(job));
        root.addContent(getQuote(job));
        root.addContent(getStartTime(job));
        root.addContent(getEndTime(job));
        root.addContent(getExecutionDuration(job));
        root.addContent(getDestruction(job));
        root.addContent(getParameters(job));
        root.addContent(getResults(job));
        Element errorSummary = getErrorSummary(job);
        if (errorSummary != null)
            root.addContent(errorSummary);

        Element jobInfo = getJobInfo(job);
        if (jobInfo != null)
            root.addContent(jobInfo);

        return root;
    }

    /**
     * Get an Element representing the Job jobId.
     *
     * @return The Job jobId Element.
     */
    public Element getJobId(Job job)
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
    public Element getJobRef(String host, Job job)
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
    public Element getRunId(Job job)
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
    public Element getOwnerId(Job job)
    {
        Element element = new Element(JobAttribute.OWNER_ID.getAttributeName(), UWS.NS);
        if (job.getOwnerID() != null)
            element.addContent(job.getOwnerID());
        else
            element.setAttribute("nil", "true", UWS.XSI_NS);
        return element;
    }

    /**
     * Get an Element representing the Job phase.
     *
     * @return The Job phase Element.
     */
    public Element getPhase(Job job)
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
    public Element getQuote(Job job)
    {
        Element element = new Element(JobAttribute.QUOTE.getAttributeName(), UWS.NS);
        Date date = job.getQuote();
        if (date == null)
            element.setAttribute("nil", "true", UWS.XSI_NS);
        else
            element.addContent(dateFormat.format(date));
        return element;
    }

    /**
     * Get an Element representing the Job startTime.
     *
     * @return The Job startTime Element.
     */
    public Element getStartTime(Job job)
    {
        Element element = new Element(JobAttribute.START_TIME.getAttributeName(), UWS.NS);
        Date date = job.getStartTime();
        if (date == null)
            element.setAttribute("nil", "true", UWS.XSI_NS);
        else
            element.addContent(dateFormat.format(date));
        return element;
    }

    /**
     * Get an Element representing the Job endTime.
     *
     * @return The Job endTime Element.
     */
    public Element getEndTime(Job job)
    {
        Element element = new Element(JobAttribute.END_TIME.getAttributeName(), UWS.NS);
        Date date = job.getEndTime();
        if (date == null)
            element.setAttribute("nil", "true", UWS.XSI_NS);
        else
            element.addContent(dateFormat.format(date));
        return element;
    }

    /**
     * Get an Element representing the Job executionDuration.
     *
     * @return The Job executionDuration Element.
     */
    public Element getExecutionDuration(Job job)
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
    public Element getDestruction(Job job)
    {
        Element element = new Element(JobAttribute.DESTRUCTION_TIME.getAttributeName(), UWS.NS);
        Date date = job.getDestructionTime();
        if (date == null)
            element.setAttribute("nil", "true", UWS.XSI_NS);
        else
            element.addContent(dateFormat.format(date));
        return element;
    }

    /**
     * Get an Element representing the Job parameters.
     *
     * @return The Job parameters Element.
     */
    public Element getParameters(Job job)
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
    public Element getResults(Job job)
    {
        Element element = new Element(JobAttribute.RESULTS.getAttributeName(), UWS.NS);
        for (Result result : job.getResultsList())
        {
            Element e = new Element(JobAttribute.RESULT.getAttributeName(), UWS.NS);
            e.setAttribute("id", result.getName());
            e.setAttribute("href", result.getURI().toASCIIString(), UWS.XLINK_NS);
            element.addContent(e);
        }
        return element;
    }

    /**
     * Get an Element representing the Job errorSummary.
     *
     * @return The Job errorSummary Element.
     */
    public Element getErrorSummary(Job job)
    {
        Element eleErrorSummary = null;
        ErrorSummary es = job.getErrorSummary();
        if (es != null)
        {
            eleErrorSummary = new Element(JobAttribute.ERROR_SUMMARY.getAttributeName(), UWS.NS);
            eleErrorSummary.setAttribute("type", es.getErrorType().toString().toLowerCase());
            eleErrorSummary.setAttribute("hasDetail", Boolean.toString(es.getHasDetail()));

            Element eleMessage = new Element(JobAttribute.ERROR_SUMMARY_MESSAGE.getAttributeName(), UWS.NS);
            eleMessage.addContent(job.getErrorSummary().getSummaryMessage());
            eleErrorSummary.addContent(eleMessage);
        }
        return eleErrorSummary;
    }
    
    /**
     * Get an Element representing the Job jobInfo.
     *
     * @return The Job jobInfo Element.
     */
    public Element getJobInfo(Job job)
    {
        Element element = null;
        JobInfo jobInfo = job.getJobInfo();
        if (jobInfo != null)
        {
            
            if (jobInfo.getContent() != null && jobInfo.getValid() != null && jobInfo.getValid())
            {
                element = new Element(JobAttribute.JOB_INFO.getAttributeName(), UWS.NS);
                try
                {
                    // The JobInfo content can't be validated since the schema(s) aren't known
                    // butw e still need to parse and extract the root/document element
                    Document doc = XmlUtil.validateXml(jobInfo.getContent(), null);
                    element.addContent(doc.getRootElement().detach()); 
                }
                catch (Exception e)
                {
                    element = null;
                }                               
            }                                   
        }
        return element;
    }
}
    