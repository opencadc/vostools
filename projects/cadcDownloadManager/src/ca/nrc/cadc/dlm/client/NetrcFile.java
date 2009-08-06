/*****************************************************************************
 *  
 *  Copyright (C) 2009				Copyright (C) 2009
 *  National Research Council		Conseil national de recherches
 *  Ottawa, Canada, K1A 0R6			Ottawa, Canada, K1A 0R6
 *  All rights reserved				Tous droits reserves
 *  					
 *  NRC disclaims any warranties,	Le CNRC denie toute garantie
 *  expressed, implied, or statu-	enoncee, implicite ou legale,
 *  tory, of any kind with respect	de quelque nature que se soit,
 *  to the software, including		concernant le logiciel, y com-
 *  without limitation any war-		pris sans restriction toute
 *  ranty of merchantability or		garantie de valeur marchande
 *  fitness for a particular pur-	ou de pertinence pour un usage
 *  pose.  NRC shall not be liable	particulier.  Le CNRC ne
 *  in any event for any damages,	pourra en aucun cas etre tenu
 *  whether direct or indirect,		responsable de tout dommage,
 *  special or general, consequen-	direct ou indirect, particul-
 *  tial or incidental, arising		ier ou general, accessoire ou
 *  from the use of the software.	fortuit, resultant de l'utili-
 *  								sation du logiciel.
 *  
 *  
 *  This file is part of cadcDownloadManager.
 *  
 *  CadcDownloadManager is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *  
 *  CadcDownloadManager is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *  
 *  You should have received a copy of the GNU Affero General Public License
 *  along with cadcDownloadManager.  If not, see <http://www.gnu.org/licenses/>.			
 *  
 *****************************************************************************/

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
