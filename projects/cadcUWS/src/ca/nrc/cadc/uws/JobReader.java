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
import java.io.StringReader;
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
import org.jdom.input.SAXBuilder;

import ca.nrc.cadc.date.DateUtil;

/**
 * @author zhangsa
 *
 */
public class JobReader
{
    @SuppressWarnings("unused")
    private static Logger log = Logger.getLogger(JobReader.class);

    private Document document;
    private SAXBuilder saxBuilder;

    public JobReader()
    {
        this.saxBuilder = new SAXBuilder("org.apache.xerces.parsers.SAXParser", false);
        this.saxBuilder.setFeature("http://xml.org/sax/features/validation", true);
        this.saxBuilder.setFeature("http://apache.org/xml/features/validation/schema", true);
        this.saxBuilder.setFeature("http://apache.org/xml/features/validation/schema-full-checking", true);
        this.saxBuilder.setProperty("http://apache.org/xml/properties/schema/external-schemaLocation", UWS.EXT_SCHEMA_LOCATION);
    }
    
    public Job readFrom(Reader reader) throws JDOMException, IOException
    {
        this.document = this.saxBuilder.build(reader);
        return parseJob();
    }

    public Job readFrom(File file) throws JDOMException, IOException
    {
        this.document = this.saxBuilder.build(file);
        return parseJob();
    }

    public Job readFrom(InputStream in) throws JDOMException, IOException
    {
        this.document = this.saxBuilder.build(in);
        return parseJob();
    }

    public Job readFrom(URL url) throws JDOMException, IOException
    {
        this.document = this.saxBuilder.build(url);
        return parseJob();
    }

    public Job readFrom(String string) throws JDOMException, IOException
    {
        StringReader reader = new StringReader(string);
        this.document = this.saxBuilder.build(reader);
        return parseJob();
    }

    private Job parseJob()
    {
        Element root = this.document.getRootElement();

        String jobID = root.getChildText("jobId", UWS.NS);
        String runID = root.getChildText("runId", UWS.NS);
        Subject owner = null; // It's not able to create a Subject based on XML text.
        ExecutionPhase executionPhase = parseExecutionPhase();
        Date quote = parseDate(root.getChildText("quote", UWS.NS));
        Date startTime = parseDate(root.getChildText("startTime", UWS.NS));
        Date endTime = parseDate(root.getChildText("endTime", UWS.NS));
        Date destructionTime = parseDate(root.getChildText("destruction", UWS.NS));
        long executionDuration = Long.parseLong(root.getChildText("executionDuration", UWS.NS));
        ErrorSummary errorSummary = parseErrorSummary();
        List<Result> resultsList = parseResultsList();
        List<Parameter> parameterList = parseParametersList();
        String requestPath = null; // not presented in XML text

        return new Job(jobID, executionPhase, executionDuration, destructionTime, quote, startTime, endTime, errorSummary, owner,
                runID, resultsList, parameterList, requestPath);
    }

    private static Date parseDate(String strDate)
    {
        Date rtn = null;
        try
        {
            rtn = DateUtil.toDate(strDate, DateUtil.IVOA_DATE_FORMAT, DateUtil.UTC);
        } catch (ParseException e)
        { 
            // do nothing, use null as return value
            log.debug(e.getMessage());
        }
        return rtn;
    }

    private ExecutionPhase parseExecutionPhase()
    {
        ExecutionPhase rtn = null;
        Element root = this.document.getRootElement();
        String strPhase = root.getChildText("phase", UWS.NS);
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
        else if (strPhase.equalsIgnoreCase(ExecutionPhase.ABORTED.toString()))
            rtn = ExecutionPhase.ABORTED;

        return rtn;
    }

    private List<Parameter> parseParametersList()
    {
        List<Parameter> rtn = null;
        Element root = this.document.getRootElement();
        Element elementParameters = root.getChild("parameters", UWS.NS);
        if (elementParameters != null)
        {
            rtn = new ArrayList<Parameter>();

            Parameter par = null;
            List<?> listElement = elementParameters.getChildren("parameter", UWS.NS);
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

    private List<Result> parseResultsList()
    {
        List<Result> rtn = null;
        Element root = this.document.getRootElement();
        Element e = root.getChild("results", UWS.NS);
        if (e != null)
        {
            rtn = new ArrayList<Result>();

            Result rs = null;
            List<?> listE = e.getChildren("result", UWS.NS);
            for (Object obj : listE)
            {
                Element eRs = (Element) obj;
                String id = eRs.getAttributeValue("id");
                String href = eRs.getAttributeValue("href", UWS.XLINK_NS);
                try
                {
                    rs = new Result(id, new URL(href));
                    rtn.add(rs);
                } catch (MalformedURLException ex)
                {
                    // do nothing; just do not add rs to list
                    log.debug(ex.getMessage());
                }
            }
        }
        return rtn;
    }

    private ErrorSummary parseErrorSummary()
    {
        ErrorSummary rtn = null;
        Element root = this.document.getRootElement();
        Element e = root.getChild("errorSummary", UWS.NS);
        if (e != null)
        {
            ErrorType errorType = null;
            String strType = e.getAttributeValue("type");
            if (strType.equalsIgnoreCase(ErrorType.FATAL.toString()))
                errorType = ErrorType.FATAL;
            else if (strType.equalsIgnoreCase(ErrorType.TRANSIENT.toString()))
                errorType = ErrorType.TRANSIENT;

            Element eDetail = e.getChild("detail", UWS.NS);
            String strDocUrl = eDetail.getAttributeValue("href", UWS.XLINK_NS);
            URL url = null;
            try
            {
                url = new URL(strDocUrl);
            } catch (MalformedURLException ex)
            {
                // do nothing; use NULL value
                log.debug(ex.getMessage());
            }

            String summaryMessage = e.getChildText("message", UWS.NS);
            rtn = new ErrorSummary(summaryMessage, url, errorType);
        }
        return rtn;
    }

}
