/**
 ************************************************************************
 *******************  CANADIAN ASTRONOMY DATA CENTRE  *******************
 **************  CENTRE CANADIEN DE DONNÉES ASTRONOMIQUES  **************
 *
 *  (c) 2010.                            (c) 2010.
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
 ************************************************************************
 */
package ca.nrc.cadc.gms;

import java.io.InputStreamReader;
import java.io.InputStream;
import java.io.IOException;
import java.io.StringReader;
import java.util.Arrays;

import org.jdom.Document;
import org.jdom.JDOMException;
import org.jdom.input.SAXBuilder;



public abstract class AbstractInputStreamReaderImpl extends InputStreamReader
{
    /**
     * Creates an InputStreamReader that uses the default charset.
     *
     * @param in An InputStream
     */
    public AbstractInputStreamReaderImpl(final InputStream in)
    {
        super(in);
    }


    /**
     * Read in the Document from the InputStream and parse it into an object.
     *
     * @throws  ReaderException     If anything went wrong during the read.
     */
    public void readAndParse() throws ReaderException
    {
        final StringBuilder xml = new StringBuilder(256);
        final char[] buffer = new char[256];

        int charCount;

        try
        {
            while ((charCount = read(buffer)) > 0)
            {
                final char[] trimmedChars = Arrays.copyOf(buffer, charCount);
                xml.append(new String(trimmedChars));
            }

            buildObject(parse(xml));
        }
        catch (IOException e)
        {
            final String message = "Unable to read the XML that was submitted.";
            throw new ReaderException(message, e);
        }
        catch (JDOMException e)
        {
            final String message =
                    "Unable to parse the XML that was submitted.";
            throw new ReaderException(message, e);
        }
    }

    /**
     * Parse out the given XML into a Document.
     *
     * @param xml           The XML String that was read in.
     * @return              A Document object.
     * @throws JDOMException when errors occur in parsing
     * @throws IOException when an I/O error prevents a document
     *         from being fully parsed
     */
    protected Document parse(final StringBuilder xml)
            throws IOException, JDOMException
    {
        // TODO - Turn on validation once the XML is sound!
        final SAXBuilder parser = new SAXBuilder(false);
        
        return parser.build(new StringReader(xml.toString()));
    }

    /**
     * Parse out the read in character data.
     *
     * @param document      The Document object parsed from the read in data.
     * @throws IOException  If anything went wrong during the read.
     */
    protected abstract void buildObject(final Document document)
            throws IOException;
}
