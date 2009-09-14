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


package ca.nrc.cadc.dlm.client;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.PasswordAuthentication;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * TODO.
 *
 * @author pdowler
 */
public class NetrcFile extends File 
{
    private boolean debug = false;
    private static String DEFAULT_MACHINE = "*";
    private List cache;
    private long timestamp;
    
    private boolean secureMode;
    
    /**
     * Create a new .netrc file. This always uses ${user.home}/.netrc as the path to the netrc file.
     *
     * @param secureMode if true, passwords are not kept in memory and read from filesystem on every call
     * to getCredentials(String)
     */
    public NetrcFile(boolean secureMode) 
    { 
        super(System.getProperty("user.home"), ".netrc");
        this.secureMode = secureMode;
    }
    
    /**
     * Crate a new .netrc file. This called NetrcFile(true).
     */
    public NetrcFile() { this(true); }
    
    private void msg(String s)
    {
        if (debug) System.out.println("[NetrcFile] " + s);
    }
    
    /**
     * Read the users .netrc file and find credentials for the specified server.
     *
     * @param host name of the remote machine the credentials are to be used with
     * @param strict require a strict hostname match or also allow default value
     * @return authentication credentials or null
     */
    public PasswordAuthentication getCredentials(String host, boolean strict)
    {
        return readFromCache(host, strict);
    }
    
    // read entire .netrc file into a char[] buffer.
    private char[] readEntireFile()
        throws IOException
    {
        FileReader fr = null;
        char[] c = new char[ (int) length() ];
        try
        {
            fr = new FileReader(this);
            int n = fr.read(c, 0, c.length);
            int n2 = 1;
            while (n2 > 0)
            {
                n2 = fr.read(c, n, c.length - n);
                if (n2 >= 0)
                    n += n2;
            }
        }
        catch(IOException ex)
        {
            if (c != null)
                Arrays.fill(c, '0');
            throw ex;
        }
        finally
        {
            if (fr != null)
                try { fr.close(); }
                catch(IOException ignore) { }
        }
        return c;
    }
    
    private PasswordAuthentication readFromCache(String host, boolean strict)
    {
        try
        {
            initCache();
            if (cache != null)
            {
                msg("looking for '" + host + "'");
                Cred defaultCred = null;
                for (int i=0; i<cache.size(); i++)
                {
                    Cred cred = (Cred) cache.get(i);
                    msg("checking '" + cred + "'");
                    if (cred.machine.equals(host))
                        return new PasswordAuthentication(cred.login, cred.pword);
                    if (cred.machine == DEFAULT_MACHINE)
                        defaultCred = cred;
                }
                if (!strict && defaultCred != null)
                    return new PasswordAuthentication(defaultCred.login, defaultCred.pword);
            }
        }
        finally
        {
            clearCache();
        }
        return null;
    }
    
    public static void main(String[] args)
    {
        NetrcFile netrc = new NetrcFile();
        netrc.msg("calling initCache for 1st time...");
        netrc.initCache();
        netrc.msg("calling clearCache for 1st time...");
        netrc.clearCache();
        netrc.msg("calling initCache for 2nd time...");
        netrc.initCache();
    }
    
    private void clearCache()
    {
        if (cache == null)
            return;
        if (secureMode)
        {
            // go through and wipe passwords
            for (int i=0; i<cache.size(); i++)
            {
                Cred cred = (Cred) cache.get(i);
                Arrays.fill(cred.pword, '0');
            }
            cache.clear();
            cache = null;
        }
    }
    private void initCache()
    {
        if (cache != null && timestamp == lastModified())
        {
            msg("cache is up to date");
            return; // up to date
        }
        
        if (cache == null)
            this.cache = new ArrayList();
        else
            cache.clear();
        this.timestamp = lastModified();

        char[] c = null;
        try
        {
            msg("reading entire file...");
            c = readEntireFile();
            
            msg("looking through char[" + c.length + "] for tokens");

            // split char buffer into lines
            int start = 0;
            int i = 0;
            boolean inToken = false;
            char lineSep = System.getProperty("line.separator").charAt(0);
            while (i < c.length)
            {
                if (inToken)
                {
                    while (i < c.length && c[i] != lineSep) // could leave whitespace on the end
                        i++;
                    if (i > start)
                    {
                        int e = i;
                        while (e > start && Character.isWhitespace(c[e-1]))
                            e--; // backtrack over trailing whitespace
                        Cred cred = new Cred(c, start, e-start);
                        msg("found: " + cred);
                        if (cred.valid)
                            cache.add(cred);
                        inToken = false;
                    }
                }
                else
                {
                    // skip intervening whitespace, blank lines, etc
                    while (i < c.length && Character.isWhitespace(c[i]))
                        i++;
                    if (i < c.length)
                    {
                        inToken = true;
                        start = i;
                    }
                }
            }
                
        }
        catch(IOException ex)
        {
            // alwasy clean up fully on failure
            clearCache();
        }
        finally
        {
            // always erase the buffer
            if (c != null)
                Arrays.fill(c, '0');
        }
    }
    
    private class Cred
    {
        boolean valid;
        String machine;
        String login;
        char[] pword;
        
        // create from part of char buffer (one line of netrc file)
        Cred(char[] cbuf, int offset, int count)
        {
            valid = false;
            int start = offset;
            int end = offset+count;
            int i = offset;
            boolean inToken = true; // assume 
            while (i < end)
            {
                if (inToken)
                {
                    // look for end of this token
                    while ( i < end && !Character.isWhitespace(cbuf[i]) )
                        i++;
                    // end of token or array
                    int len = i - start;
                    String s = new String(cbuf, start, len);
                    if ("machine".equals(s))
                    {
                        start = nextToken(cbuf, i, end);
                        i = nextWhitespace(cbuf, start, end);
                        if (i > start)
                            machine = new String(cbuf, start, i-start);
                    }
                    else  if ("login".equals(s))
                    {
                        start = nextToken(cbuf, i, end);
                        i = nextWhitespace(cbuf, start, end);
                        if (i > start)
                            login = new String(cbuf, start, i-start);
                    }
                    else if ("password".equals(s))
                    {
                        start = nextToken(cbuf, i, end);
                        i = nextWhitespace(cbuf, start, end);
                        if (i > start)
                        {
                            pword = new char[i-start];
                            System.arraycopy(cbuf, start, pword, 0, i-start);
                            
                        }
                    }
                    else if ("default".equals(s))
                    {
                        machine = "*";
                    }
                    inToken = false;
                }
                else
                {
                    i = nextToken(cbuf, i, end);
                    if (i < end)
                    {
                        inToken = true;
                        start = i;
                    }
                }
            }
            valid = (machine != null && login != null && pword != null);
        }
        
        // find next whitespace character
        private int nextWhitespace(char[] cbuf, int i, int end)
        {
            // skip whitespace
            while (i < end && !Character.isWhitespace(cbuf[i]))
                i++;
            return i;
        }
        // find start of next token (non-whitespace)
        private int nextToken(char[] cbuf, int i, int end)
        {
            // skip whitespace
            while (i < end && Character.isWhitespace(cbuf[i]))
                i++;
            return i;
        }
        
        public String toString()
        {
            return "Cred[" + machine + "," + login + "," + new String(pword) + "]";
        }
    }
}
