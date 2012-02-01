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
package ca.nrc.cadc.tap.writer;

import ca.nrc.cadc.date.DateUtil;
import ca.nrc.cadc.net.NetUtil;
import ca.nrc.cadc.tap.TableWriter;
import ca.nrc.cadc.tap.schema.ParamDesc;
import ca.nrc.cadc.tap.schema.TapSchema;
import ca.nrc.cadc.tap.writer.formatter.DefaultFormatterFactory;
import ca.nrc.cadc.tap.writer.formatter.Formatter;
import ca.nrc.cadc.tap.writer.formatter.FormatterFactory;
import ca.nrc.cadc.tap.writer.formatter.ResultSetFormatter;
import ca.nrc.cadc.uws.Job;
import ca.nrc.cadc.uws.Parameter;
import java.io.IOException;
import java.io.OutputStream;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.DateFormat;
import java.util.List;
import org.apache.log4j.Logger;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.output.Format;
import org.jdom.output.XMLOutputter;

/**
 *
 * @author jburke
 */
public class RssTableWriter implements TableWriter
{
    private static Logger log = Logger.getLogger(RssTableWriter.class);
    
    public static final String RFC_822__DATE_FORMAT = "EEE', 'dd' 'MMM' 'yyyy' 'HH:mm:ss' 'z";
    private DateFormat dateFormat = DateUtil.getDateFormat(RFC_822__DATE_FORMAT, DateUtil.LOCAL);

    // List of column names used in the select statement.
    protected List<ParamDesc> selectList;

    protected Job job;

    protected String info;
    
    // Maximum number of rows to write.
    protected int maxRows;
    
    public RssTableWriter()
    {
        maxRows = Integer.MAX_VALUE;
    }

    /**
     * @deprecated
     */
    public void setJobID(String jobID) { }

    /**
     * @deprecated
     */
    public void setParameterList(List<Parameter> params) { }

    /**
     * @deprecated
     */
    public void setTapSchema(TapSchema schema) { }

    public void setJob(Job job)
    {
        this.job = job;
    }

    /**
     * The info is used as the channel title and should be short.
     *
     * @param info
     */
    public void setQueryInfo(String info)
    {
        this.info = info;
    }

    public String getExtension()
    {
        return "xml";
    }

    public String getContentType()
    {
        return "application/rss+xml";
    }

    public void setSelectList(List<ParamDesc> selectList)
    {
        this.selectList = selectList;
    }

    public void setMaxRowCount(int count)
    {
        this.maxRows = count;
        log.debug("maxRows: " + maxRows);
    }

    public void write(ResultSet resultSet, OutputStream output)
        throws IOException
    {
        if (job == null)
            throw new IllegalStateException("Job cannot be null, set using setJob()");
        if (selectList == null)
            throw new IllegalStateException("SelectList cannot be null, set using setSelectList()");

        FormatterFactory factory = DefaultFormatterFactory.getFormatterFactory();
        factory.setJobID(job.getID());
        factory.setParamList(job.getParameterList());
        List<Formatter> formatters = factory.getFormatters(selectList);

        if (resultSet != null)
            try { log.debug("resultSet column count: " + resultSet.getMetaData().getColumnCount()); }
            catch(Exception oops) { log.error("failed to check resultset column count", oops); }
        
        // JDOM document.
        Document document = new Document();
        
        // Root element.
        Element rss = new Element("rss");
        rss.setAttribute("version", "2.0");
        document.setRootElement(rss); 

        // channel element.
        Element channel = new Element("channel");
        rss.addContent(channel);

        // channel title.
        Element channelTitle = new Element("title");
        channelTitle.setText(info);
        channel.addContent(channelTitle);

        StringBuilder qp = new StringBuilder();
        qp.append("http://");
        qp.append(NetUtil.getServerName(null));
        qp.append(job.getRequestPath());
        qp.append("?");
        for (Parameter parameter : job.getParameterList())
        {
            qp.append(parameter.getName());
            qp.append("=");
            qp.append(parameter.getValue());
            qp.append("&");
        }
        String queryString = qp.substring(0, qp.length() - 1); // strip trailing &
        Element link = new Element("link");
        link.setText(queryString);
        channel.addContent(link);
 
        // items.
        int itemCount = 0;
        try
        {
            while (resultSet.next())
            {
                // item element.
                Element item = new Element("item");
       
                // item description.
                Element itemDescription = new Element("description");
                StringBuilder sb = new StringBuilder();
                sb.append("<table>");
                
                // Loop through the ResultSet adding the table data elements.
                for (int columnIndex = 1; columnIndex <= resultSet.getMetaData().getColumnCount(); columnIndex++)
                {
                    String columnLabel = resultSet.getMetaData().getColumnLabel(columnIndex);

                    if (columnLabel.equalsIgnoreCase("rss_title"))
                    {
                        String titleStr = resultSet.getString("rss_title");
                        log.debug("item title: " + titleStr);
                        Element itemTitle = new Element("title");
                        itemTitle.setText(titleStr);
                        item.addContent(itemTitle);
                    }
                    else if (columnLabel.equalsIgnoreCase("rss_link"))
                    {
                        String linkStr = resultSet.getString("rss_link");
                        log.debug("item link: " + linkStr);
                        Element itemLink = new Element("link");
                        itemLink.setText(linkStr);
                        item.addContent(itemLink);
                    }
                    else if (columnLabel.equalsIgnoreCase("rss_pubDate"))
                    {
                        Timestamp ts = resultSet.getTimestamp("rss_pubDate");
                        String pubDateStr = dateFormat.format(ts);
                        log.debug("item pubDate: " + pubDateStr);
                        Element itemPubDate = new Element("pubDate");
                        itemPubDate.setText(pubDateStr);
                        item.addContent(itemPubDate);
                    }
                    else
                    {
                        ParamDesc paramDesc = selectList.get(columnIndex - 1);
                        sb.append("<tr><td align=\"right\">");
                        sb.append(paramDesc.name);
                        sb.append("</td><td align=\"left\">");
                        Formatter formatter = formatters.get(columnIndex - 1);
                        if (formatter instanceof ResultSetFormatter)
                            sb.append(((ResultSetFormatter) formatter).format(resultSet, columnIndex));
                        else
                            sb.append(formatter.format(resultSet.getObject(columnIndex)));
                        sb.append("</td></tr>");
                    }
                }
                sb.append("</table>");
                itemDescription.setText(sb.toString());
                item.addContent(itemDescription);
                channel.addContent(item);
                itemCount++;

                // Write MaxRows
                if (itemCount == maxRows)
                    break;
            }
        }
        catch (SQLException e)
        {
            throw new RuntimeException(e.getMessage());
        }

        // channel description.
        Element channelDescription = new Element("description");
        channelDescription.setText("The " + itemCount + " most recent from " + info);
        channel.addContent(channelDescription);

        // Write out the VOTABLE.
        XMLOutputter outputter = new XMLOutputter(Format.getPrettyFormat());
        outputter.output(document, output);
    }

}
