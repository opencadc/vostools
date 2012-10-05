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
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.text.DateFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;
import org.jdom.Attribute;
import org.jdom.DataConversionException;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.input.SAXBuilder;
import org.jdom.output.XMLOutputter;

import ca.nrc.cadc.date.DateUtil;
import ca.nrc.cadc.xml.XmlUtil;
import java.util.Map.Entry;
import java.util.Set;

/**
 * Constructs a Job from an XML source. This class is not thread safe but it is
 * re-usable  so it can safely be used to sequentially parse multiple XML transfer
 * documents.
 */
public class JobReader
{
    @SuppressWarnings("unused")
    private static Logger log = Logger.getLogger(JobReader.class);
    
    private static final String UWS_SCHEMA_URL = "http://www.ivoa.net/xml/UWS/v1.0";
    private static final String UWS_SCHEMA_RESOURCE = "UWS-v1.0.xsd";
    private static final String XLINK_SCHEMA_URL = "http://www.w3.org/1999/xlink";
    private static final String XLINK_SCHEMA_RESOURCE = "XLINK.xsd";
    
    private static final String uwsSchemaUrl;
    private static final String xlinkSchemaUrl;
    static
    {        
        uwsSchemaUrl = XmlUtil.getResourceUrlString(UWS_SCHEMA_RESOURCE, JobReader.class);
        log.debug("uwsSchemaUrl: " + uwsSchemaUrl);
        
        xlinkSchemaUrl = XmlUtil.getResourceUrlString(XLINK_SCHEMA_RESOURCE, JobReader.class);
        log.debug("xlinkSchemaUrl: " + xlinkSchemaUrl);
    }

    private Map<String, String> schemaMap;
    private DateFormat dateFormat;
    private SAXBuilder docBuilder;

    /**
     * Constructor. XML Schema validation is enabled by default.
     */
    public JobReader() { this(true); }

    /**
     * Constructor. XML schema validation may be disabled, in which case the client
     * is likely to fail in horrible ways (e.g. NullPointerException) if it receives
     * invalid documents. However, performance may be improved.
     *
     * @param enableSchemaValidation
     */
    public JobReader(boolean enableSchemaValidation)
    {
        if (enableSchemaValidation)
        {
            schemaMap = new HashMap<String, String>();
            schemaMap.put(UWS_SCHEMA_URL, uwsSchemaUrl);
            schemaMap.put(XLINK_SCHEMA_URL, xlinkSchemaUrl);
            log.debug("schema validation enabled");
        }
        else
        {
            log.debug("schema validation disabled");
        }

        this.docBuilder = XmlUtil.createBuilder(schemaMap);
        this.dateFormat = DateUtil.getDateFormat(DateUtil.IVOA_DATE_FORMAT, DateUtil.UTC);
    }

    /**
     * Alternative constructor to pass in additional schemas used to valid the
     * documents being read.
     *
     * Passing in an empty Map enables schema validation with no additional schemas
     * other than the default UWS and XLink schemas.
     *
     * @param schemas Map of schema namespace to resource.
     */
    public JobReader(Map<String, String> schemas)
    {
        if (schemas == null)
        {
            throw new IllegalArgumentException("Map of schema namespace to resource cannot be null");
        }
        schemaMap = new HashMap<String, String>();
        schemaMap.put(UWS_SCHEMA_URL, uwsSchemaUrl);
        schemaMap.put(XLINK_SCHEMA_URL, xlinkSchemaUrl);
        if (!schemas.isEmpty())
        {
            Set<Entry<String, String>> entries = schemas.entrySet();
            for (Entry<String, String> entry : entries)
            {
                schemaMap.put(entry.getKey(), entry.getValue());
            }
        }
        log.debug("schema validation enabled");

        this.docBuilder = XmlUtil.createBuilder(schemaMap);
        this.dateFormat = DateUtil.getDateFormat(DateUtil.IVOA_DATE_FORMAT, DateUtil.UTC);
    }

    public Job read(InputStream in) 
        throws JDOMException, IOException, ParseException
    {
        try
        {
            return read(new InputStreamReader(in, "UTF-8"));
        }
        catch (UnsupportedEncodingException e)
        {
            throw new RuntimeException("UTF-8 encoding not supported");
        }
    }

    public Job read(Reader reader) 
        throws JDOMException, IOException, ParseException
    {
        Document doc = docBuilder.build(reader);
        return parseJob(doc);
    }

    private Job parseJob(Document doc)
        throws ParseException, DataConversionException
    {
        Element root = doc.getRootElement();

        String jobID = root.getChildText(JobAttribute.JOB_ID.getAttributeName(), UWS.NS);
        if (jobID != null && jobID.trim().length() == 0)
            jobID = null;
        String runID = parseStringContent(root.getChild(JobAttribute.RUN_ID.getAttributeName(), UWS.NS));
        String ownerID = parseStringContent(root.getChild(JobAttribute.OWNER_ID.getAttributeName(), UWS.NS));
        Date quote = parseDate(parseStringContent(root.getChild(JobAttribute.QUOTE.getAttributeName(), UWS.NS)));
        Date startTime = parseDate(parseStringContent(root.getChild(JobAttribute.START_TIME.getAttributeName(), UWS.NS)));
        Date endTime = parseDate(parseStringContent(root.getChild(JobAttribute.END_TIME.getAttributeName(), UWS.NS)));
        Date destructionTime = parseDate(parseStringContent(root.getChild(JobAttribute.DESTRUCTION_TIME.getAttributeName(), UWS.NS)));
        Long executionDuration = new Long(parseStringContent(root.getChild(JobAttribute.EXECUTION_DURATION.getAttributeName(), UWS.NS)));

        ExecutionPhase executionPhase = parseExecutionPhase(doc);

        ErrorSummary errorSummary = null;
        if (executionPhase.equals(ExecutionPhase.ERROR)) errorSummary = parseErrorSummary(doc);

        List<Result> resultsList = parseResultsList(doc);

        List<Parameter> parameterList = parseParametersList(doc);

        JobInfo jobInfo = parseJobInfo(doc);

        Job job = new Job(jobID, executionPhase, executionDuration, destructionTime, quote,
                startTime, endTime, errorSummary, ownerID, runID,
                null, null, jobInfo, parameterList, resultsList);
        
        return job;
    }

    private Date parseDate(String strDate)
        throws ParseException
    {
        if (strDate == null)
            return null;
        strDate = strDate.trim();
        if (strDate.length() == 0)
            return null;
        return dateFormat.parse(strDate);
    }

    private String parseStringContent(Element e)
        throws DataConversionException
    {
        if (e == null)
            return null;
        Attribute nil = e.getAttribute("nil", UWS.XSI_NS);
        if (nil != null && nil.getBooleanValue())
            return null;
        return e.getTextTrim();
    }

    private ExecutionPhase parseExecutionPhase(Document doc)
    {
        ExecutionPhase rtn = null;
        Element root = doc.getRootElement();
        String strPhase = root.getChildText(JobAttribute.EXECUTION_PHASE.getAttributeName(), UWS.NS);
        if (strPhase.equalsIgnoreCase(ExecutionPhase.PENDING.toString()))
            rtn = ExecutionPhase.PENDING;
        else if (strPhase.equalsIgnoreCase(ExecutionPhase.QUEUED.toString()))
            rtn = ExecutionPhase.QUEUED;
        else if (strPhase.equalsIgnoreCase(ExecutionPhase.EXECUTING.toString()))
            rtn = ExecutionPhase.EXECUTING;
        else if (strPhase.equalsIgnoreCase(ExecutionPhase.COMPLETED.toString()))
            rtn = ExecutionPhase.COMPLETED;
        else if (strPhase.equalsIgnoreCase(ExecutionPhase.ERROR.toString()))
            rtn = ExecutionPhase.ERROR;
        else if (strPhase.equalsIgnoreCase(ExecutionPhase.UNKNOWN.toString()))
            rtn = ExecutionPhase.UNKNOWN;
        else if (strPhase.equalsIgnoreCase(ExecutionPhase.HELD.toString()))
            rtn = ExecutionPhase.HELD;
        else if (strPhase.equalsIgnoreCase(ExecutionPhase.SUSPENDED.toString()))
            rtn = ExecutionPhase.SUSPENDED;
        else if (strPhase.equalsIgnoreCase(ExecutionPhase.ABORTED.toString())) rtn = ExecutionPhase.ABORTED;

        return rtn;
    }

    private List<Parameter> parseParametersList(Document doc)
    {
        List<Parameter> rtn = null;
        Element root = doc.getRootElement();
        Element elementParameters = root.getChild(JobAttribute.PARAMETERS.getAttributeName(), UWS.NS);
        if (elementParameters != null)
        {
            rtn = new ArrayList<Parameter>();

            Parameter par = null;
            List<?> listElement = elementParameters.getChildren(JobAttribute.PARAMETER.getAttributeName(), UWS.NS);
            for (Object obj : listElement)
            {
                Element e = (Element) obj;
                String id = e.getAttributeValue("id");
                String value = e.getText();
                par = new Parameter(id, value);
                rtn.add(par);
            }
        }
        return rtn;
    }

    private List<Result> parseResultsList(Document doc)
    {
        List<Result> rtn = null;
        Element root = doc.getRootElement();
        Element e = root.getChild(JobAttribute.RESULTS.getAttributeName(), UWS.NS);
        if (e != null)
        {
            rtn = new ArrayList<Result>();

            Result rs = null;
            List<?> listE = e.getChildren(JobAttribute.RESULT.getAttributeName(), UWS.NS);
            for (Object obj : listE)
            {
                Element eRs = (Element) obj;
                String id = eRs.getAttributeValue("id");
                String href = eRs.getAttributeValue("href", UWS.XLINK_NS);
                try
                {
                    rs = new Result(id, new URI(href));
                    rtn.add(rs);
                }
                catch (URISyntaxException ex)
                {
                    // do nothing; just do not add rs to list
                    log.debug(ex.getMessage());
                }
            }
        }
        return rtn;
    }

    private ErrorSummary parseErrorSummary(Document doc)
    {
        ErrorSummary rtn = null;
        Element root = doc.getRootElement();
        Element e = root.getChild(JobAttribute.ERROR_SUMMARY.getAttributeName(), UWS.NS);
        if (e != null)
        {
            ErrorType errorType = null;
            String strType = e.getAttributeValue("type");
            if (strType.equalsIgnoreCase(ErrorType.FATAL.toString()))
                errorType = ErrorType.FATAL;
            else if (strType.equalsIgnoreCase(ErrorType.TRANSIENT.toString()))
                errorType = ErrorType.TRANSIENT;

            boolean hasDetail = false;
            String strDetail = e.getAttributeValue("hasDetail");
            if (strDetail.equalsIgnoreCase("true"))
                hasDetail = true;
            
            String summaryMessage = e.getChildText(JobAttribute.ERROR_SUMMARY_MESSAGE.getAttributeName(), UWS.NS);
            rtn = new ErrorSummary(summaryMessage, errorType, hasDetail);
        }
        return rtn;
    }
    
    private JobInfo parseJobInfo(Document doc)
    {
        JobInfo rtn = null;
        Element root = doc.getRootElement();
        Element e = root.getChild(JobAttribute.JOB_INFO.getAttributeName(), UWS.NS);
        if (e != null)
        {
            log.debug("found jobInfo element");
            String content = e.getText();
            List children = e.getChildren();
            if (content != null)
                content = content.trim();
            if (content.length() > 0) // it was text content
            {
                rtn = new JobInfo(content, null, null);
            }
            else if (children != null)
            {
                if (children.size() == 1)
                {
                    try
                    {
                        Element ce = (Element) children.get(0);
                        Document jiDoc = new Document((Element) ce.detach());
                        XMLOutputter outputter = new XMLOutputter();
                        StringWriter sw = new StringWriter();
                        outputter.output(jiDoc, sw);
                        sw.close();
                        rtn = new JobInfo(sw.toString(), null, null);
                        
                    }
                    catch(IOException ex)
                    {
                        throw new RuntimeException("BUG while writing element to string", ex);
                    }
                }
            }
        }
        log.debug("parseJobInfo: " + rtn);
        return rtn;
    }

}
