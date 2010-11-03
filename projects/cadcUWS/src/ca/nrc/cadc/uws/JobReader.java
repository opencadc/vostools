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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.security.auth.Subject;

import org.apache.log4j.Logger;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;

import ca.nrc.cadc.date.DateUtil;
import ca.nrc.cadc.xml.XmlUtil;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.text.DateFormat;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.security.auth.x500.X500Principal;
import org.jdom.input.SAXBuilder;

/**
 * @author zhangsa
 *
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

    private DateFormat dateFormat;
    private SAXBuilder docBuilder;

    public JobReader()
    {
        Map<String, String> schemaMap = new HashMap<String, String>();
        schemaMap.put(UWS_SCHEMA_URL, uwsSchemaUrl);
        schemaMap.put(XLINK_SCHEMA_URL, xlinkSchemaUrl);
        this.docBuilder = XmlUtil.createBuilder(schemaMap);
        this.dateFormat = DateUtil.getDateFormat(DateUtil.IVOA_DATE_FORMAT, DateUtil.UTC);
    }

    public Job readFrom(File file) 
        throws JDOMException, IOException, ParseException
    {
        if (file == null)
            throw new IOException("Null file reference");
        FileReader reader;
        try
        {
            reader = new FileReader(file);
        }
        catch (FileNotFoundException e)
        {
            throw new RuntimeException("File not found " + file.getAbsoluteFile());
        }
        Document doc = docBuilder.build(reader);
        return parseJob(doc);
    }

    public Job readFrom(InputStream in) 
        throws JDOMException, IOException, ParseException
    {
        if (in == null)
            throw new IOException("InputStream is closed");
        InputStreamReader reader;
        try
        {
            reader = new InputStreamReader(in, "UTF-8");
        }
        catch (UnsupportedEncodingException e)
        {
            throw new RuntimeException("UTF-8 encoding not supported");
        }
        Document doc = docBuilder.build(reader);
        return parseJob(doc);
    }

    public Job readFrom(Reader reader) 
        throws JDOMException, IOException, ParseException
    {
        Document doc = docBuilder.build(reader);
        return parseJob(doc);
    }

    /**
     * @deprecated cannot be correctly initialized with SSL in all contexts
     * @param url
     * @return
     * @throws JDOMException
     * @throws IOException
     * @throws ParseException
     */
    public Job readFrom(URL url) 
        throws JDOMException, IOException, ParseException
    {
        InputStreamReader reader;
        try
        {
            reader = new InputStreamReader(url.openConnection().getInputStream(), "UTF-8");
        }
        catch (UnsupportedEncodingException e)
        {
            throw new RuntimeException("UTF-8 encoding not supported");
        }
        Document doc = docBuilder.build(reader);
        return parseJob(doc);
    }

    public Job readFrom(String string)
        throws JDOMException, IOException, ParseException
    {
        Document doc = docBuilder.build(string);
        return parseJob(doc);
    }

    private Job parseJob(Document doc)
        throws ParseException
    {
        Element root = doc.getRootElement();

        String jobID = root.getChildText(JobAttribute.JOB_ID.getAttributeName(), UWS.NS);
        if (jobID != null && jobID.trim().length() == 0)
            jobID = null;
        String runID = root.getChildText(JobAttribute.RUN_ID.getAttributeName(), UWS.NS);
        Subject owner = createSubject(root.getChildText(JobAttribute.OWNER_ID.getAttributeName(), UWS.NS));
        ExecutionPhase executionPhase = parseExecutionPhase(doc);
        Date quote = parseDate(root.getChildText(JobAttribute.QUOTE.getAttributeName(), UWS.NS));
        Date startTime = parseDate(root.getChildText(JobAttribute.START_TIME.getAttributeName(), UWS.NS));
        Date endTime = parseDate(root.getChildText(JobAttribute.END_TIME.getAttributeName(), UWS.NS));
        Date destructionTime = parseDate(root.getChildText(JobAttribute.DESTRUCTION_TIME.getAttributeName(), UWS.NS));
        long executionDuration = Long.parseLong(root.getChildText(JobAttribute.EXECUTION_DURATION.getAttributeName(), UWS.NS));
        ErrorSummary errorSummary = null;
        if (executionPhase.equals(ExecutionPhase.ERROR)) errorSummary = parseErrorSummary(doc);
        List<Result> resultsList = parseResultsList(doc);
        List<Parameter> parameterList = parseParametersList(doc);
        String requestPath = null; // not presented in XML text

        return new Job(jobID, executionPhase, executionDuration, destructionTime, quote, startTime, endTime, errorSummary, owner,
                runID, resultsList, parameterList, requestPath);
    }

    private Subject createSubject(String owner)
    {
        if (owner == null)
            return null;
        owner = owner.trim();
        if (owner.length() == 0)
            return null;
        Set<X500Principal> principals = new HashSet<X500Principal>();
        Set<Object> pub = new HashSet();
        Set<Object> priv = new HashSet();
        principals.add(new X500Principal(owner));
        return new Subject(true, principals, pub, priv);
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
                    rs = new Result(id, new URL(href));
                    rtn.add(rs);
                }
                catch (MalformedURLException ex)
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

}
