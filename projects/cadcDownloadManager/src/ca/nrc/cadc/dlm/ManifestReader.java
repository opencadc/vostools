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

package ca.nrc.cadc.dlm;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Iterator;
import java.util.NoSuchElementException;
import org.apache.log4j.Logger;

/**
 * Reads a download manifest. A download manifest is a tab-separated-value
 * ascii file (content-type: <code>application/x-download-manifest+txt</code>)
 * with 2 or 3 tokens per line:
 * </p>
 * <pre>
 * OK    &lt;url&gt;     [&lt;destination&gt;]
 * ERROR &lt;message&gt; [&lt;destination&gt;]
 * </pre>
 * If the first token is OK, the second must be a valud URL (practically, http or
 * https). If the first token is ERROR, the second token is an error message. The
 * third (optional) token is a relative path to store the downloaded file in; the
 * last path element is the filename and all preceeding elements are assumed to be
 * directory names. 
 *
 * @author pdowler
 */
public class ManifestReader
{
    private static Logger log = Logger.getLogger(ManifestReader.class);

    public static final String CONTENT_TYPE = "application/x-download-manifest+txt";

    public ManifestReader() { }

    public Iterator<DownloadDescriptor> read(String content)
    {
        return read(new StringReader(content));
    }
    
    public Iterator<DownloadDescriptor> read(InputStream istream)
    {
        return read(new InputStreamReader(istream));
    }

    public Iterator<DownloadDescriptor> read(Reader r)
    {
        return new ManifestIterator(r);
    }

    private class ManifestIterator implements Iterator<DownloadDescriptor>
    {
        private BufferedReader in;
        private DownloadDescriptor cur;

        private ManifestIterator(Reader r)
        {
            this.in = new BufferedReader(r);
            step(true);
        }

        public boolean hasNext()
        {
            return (cur != null);
        }

        public DownloadDescriptor next()
        {
            if (cur == null)
                throw new NoSuchElementException();
            DownloadDescriptor ret = cur;
            step(false);
            return ret;
        }

        public void remove()
        {
            throw new UnsupportedOperationException();
        }

        // assign the next descriptor to cur
        private void step(boolean init)
        {
            if (!init && cur == null)
                return;

            String arg = null;
            String dest = null;
            try
            {
                String line = in.readLine();
                while (line != null) // skip blank lines
                {
                    line = line.trim();
                    if (line.length() > 0)
                        break; // found non-blank line
                    else
                        line = in.readLine();
                }
                if (line == null)
                {
                    cur = null;
                    try { in.close(); }
                    catch(IOException ignore) { }
                    return;
                }
                log.debug("line: " + line);
                String[] tokens = line.split("[\t]"); // tab-separated-value
                String status = tokens[0];
                if (tokens.length > 1)
                    arg = tokens[1];
                if (tokens.length > 2)
                    dest = tokens[2];
                if (DownloadDescriptor.OK.equals(status))
                {
                    URL url = new URL(arg);
                    cur = new DownloadDescriptor(null, url, dest);
                }
                else if (DownloadDescriptor.ERROR.equals(status))
                {
                    cur = new DownloadDescriptor(null, arg, dest);
                }
                else
                {
                    cur = new DownloadDescriptor(null, "illegal start of line: " + status);
                }
            }
            catch(MalformedURLException ex)
            {
                cur = new DownloadDescriptor(null, "illegal URL: "+ arg, dest);
            }
            catch(IOException ex)
            {
                throw new NoSuchElementException("failed to read a DownloadDescriptor: " + ex);
            }
            log.debug("step: " + cur);
        }
    }
}
