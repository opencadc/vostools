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
*  $Revision: 0 $
*
************************************************************************
*/

package ca.nrc.cadc.tap;

import ca.nrc.cadc.tap.writer.VOTableWriter;
import ca.nrc.cadc.tap.writer.AsciiTableWriter;
import ca.nrc.cadc.tap.writer.RssTableWriter;
import java.util.List;

import ca.nrc.cadc.uws.Parameter;
import ca.nrc.cadc.uws.ParameterUtil;
import java.util.Map;
import java.util.TreeMap;
import org.apache.log4j.Logger;

/**
 * Factory that handles the FORMAT parameter and creates a suitable TableWriter.
 * TODO: change to non-static methods so sub-classes can define and enable alternate
 * formats.
 * 
 * @author pdowler
 */
public class TableWriterFactory
{
    private static Logger log = Logger.getLogger(TableWriterFactory.class);

    private static final String FORMAT = "FORMAT";

    // shortcuts
    public static final String CSV = "csv";
    public static final String FITS = "fits";
    public static final String HTML = "html";
    public static final String TEXT = "text";
    public static final String TSV = "tsv";
    public static final String VOTABLE = "votable";
    public static final String RSS = "rss";

    // content-type
    private static final String APPLICATION_FITS = "application/fits";
    private static final String APPLICATION_VOTABLE_XML = "application/x-votable+xml";
    private static final String APPLICATION_RSS = "application/rss+xml";
    private static final String TEXT_XML_VOTABLE = "text/xml;content=x-votable"; // the SIAv1 mimetype
    private static final String TEXT_CSV = "text/csv";
    private static final String TEXT_HTML = "text/html";
    private static final String TEXT_PLAIN = "text/plain";
    private static final String TEXT_TAB_SEPARATED_VALUES = "text/tab-separated-values";
    private static final String TEXT_XML = "text/xml";

    

    private static Map<String,String> knownFormats;

    static
    {
        knownFormats = new TreeMap<String,String>();
        knownFormats.put(APPLICATION_VOTABLE_XML, VOTABLE);
        knownFormats.put(TEXT_XML, VOTABLE);
        knownFormats.put(TEXT_XML_VOTABLE, VOTABLE);
        knownFormats.put(TEXT_CSV, CSV);
        knownFormats.put(TEXT_TAB_SEPARATED_VALUES, TSV);
//        knownFormats.put(APPLICATION_FITS, FITS);
//        knownFormats.put(TEXT_PLAIN, TEXT);
//        knownFormats.put(TEXT_HTML, HTML);
        knownFormats.put(VOTABLE, VOTABLE);
        knownFormats.put(CSV, CSV);
        knownFormats.put(TSV, TSV);
//        knownFormats.put(FITS, FITS);
//        knownFormats.put(TEXT, TEXT);
//        knownFormats.put(HTML, HTML);
        knownFormats.put(RSS, RSS);
        knownFormats.put(APPLICATION_RSS, RSS);
    }

    /**
     * Find the FORMAT parameter and return one of the public constants ion this class.
     * 
     * @param params
     * @return one of the format constants
     */
    public static String getFormat(List<Parameter> params)
    {
        String format = ParameterUtil.findParameterValue(FORMAT, params);
        if (format == null)
            format = VOTABLE;
        String type = knownFormats.get(format.toLowerCase());
        if (type == null)
            throw new UnsupportedOperationException("unknown format: " + format);
        return type;
    }
    
    public static TableWriter getWriter(List<Parameter> params)
    {
        String type = getFormat(params);

        if (type.equals(VOTABLE))
            return new VOTableWriter();
        if (type.equals(CSV))
            return new AsciiTableWriter(CSV);
        if (type.equals(TSV))
            return new AsciiTableWriter(TSV);
        if (type.equals(RSS))
            return new RssTableWriter();

        // legal format but we don't have a table writer for it
        throw new UnsupportedOperationException("unsupported format: " + type);
    }

}
