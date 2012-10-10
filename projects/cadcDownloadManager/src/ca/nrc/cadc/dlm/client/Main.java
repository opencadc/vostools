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

import java.awt.Component;
import java.security.PrivilegedAction;

import javax.security.auth.Subject;

import org.apache.log4j.Level;

import ca.nrc.cadc.auth.SSOCookieCredential;
import ca.nrc.cadc.auth.SSOCookieManager;
import ca.nrc.cadc.dlm.DownloadUtil;
import ca.nrc.cadc.thread.ConditionVar;
import ca.nrc.cadc.util.ArgumentMap;
import ca.onfire.ak.Application;
import ca.onfire.ak.ApplicationFrame;
import java.util.List;
import java.util.Map;

/**
 * TODO
 *
 * @version $Version$
 * @author pdowler
 */
public class Main
{
    //private static Logger log = Logger.getLogger(Main.class);
    private static UserInterface ui;

    public static void main(String[] args)
    {
        try
        {
            ArgumentMap am = new ArgumentMap(args);
            if ( am.isSet("h") || am.isSet("help") )
            {
                usage();
                System.exit(0);
            }
            Level level = Level.WARN;
            if ( am.isSet("d") || am.isSet("debug") )
                level = Level.DEBUG;
            else if ( am.isSet("v") || am.isSet("verbose") )
                level = Level.INFO;
            else if ( am.isSet("q") || am.isSet("quiet") )
                level = Level.OFF;

            String uriStr = fixNull(am.getValue("uris"));
            String paramStr = fixNull(am.getValue("params"));

            boolean headless = am.isSet("headless");
            
            
            ConditionVar downloadCompleteCond = new ConditionVar();
            
            if (headless)
            {
                boolean decompress = am.isSet("decompress");
                boolean overwrite = am.isSet("overwrite");
                String dest = am.getValue("dest");
                String thStr = am.getValue("threads");
                boolean retry = am.isSet("retry");
                Integer threads = null;
                if (thStr != null)
                    try
                    {
                        threads = new Integer(thStr);
                    }
                    catch(NumberFormatException ex)
                    {
                        throw new IllegalArgumentException("failed to parse '" + thStr + "' as an integer");
                    }
                downloadCompleteCond.set(false);
                ui = new ConsoleUI(level, threads, retry, dest, decompress, overwrite, downloadCompleteCond);
            }
            else
            {
                ui = new GraphicUI(level);
                ApplicationFrame frame  = new ApplicationFrame(Constants.name, (Application)ui);
                frame.getContentPane().add((Component)ui);
                frame.setVisible(true);
            }
            
            

            Subject subject = new Subject();
            // Cookie based authentication?
            String ssoCookieStr = fixNull(am.getValue("ssocookie"));
            if (ssoCookieStr != null)
            {
                  String ssoCookieDomain = 
                      fixNull(am.getValue("ssocookiedomain"));
                  if (ssoCookieDomain == null)
                  {
                      System.out.
                      println("Missing ssocookiedomain argument...");
                      Main.usage();
                      System.exit(-1);
                  }
                  SSOCookieCredential cred = new SSOCookieCredential(
                          SSOCookieManager.DEFAULT_SSO_COOKIE_NAME + "=" + 
                                  ssoCookieStr, ssoCookieDomain);
                  subject.getPublicCredentials().add(cred);
            }

            final List<String> uris = DownloadUtil.decodeListURI(uriStr);
            final Map<String,List<String>> params = DownloadUtil.decodeParamMap(paramStr);

            boolean result = Subject.doAs(subject, new PrivilegedAction<Boolean>()
            {
                public Boolean run()
                {
                    ui.add(uris, params);
                    ui.start();
                    return true;
                }
            });
            
            if (!result)
            {
                System.err.println("Error occurred during execution..");
            }
            
            // if running headless, don't exit
            // until the downloads have been completed.
            if (headless)
            {
                downloadCompleteCond.waitForTrue();
            }
        }
        catch(IllegalArgumentException ex)
        {
            System.err.println("fatal error during startup");
            ex.printStackTrace();
            usage();
            System.exit(1);
        }
        catch(Throwable oops) 
        {
            System.err.println("fatal error during startup");
            oops.printStackTrace();
            System.exit(2);
        }
    }
    
    // convert string 'null' and empty string to a null, trim() and return
    private static String fixNull(String s)
    {
        if (s == null || "null".equals(s) )
            return null;
        s = s.trim();
        if (s.length() == 0)
            return null;
        return s;
    }

    private static void usage()
    {
        System.out.println("java -jar cadcDownloadManagerClient.jar -h || --help");
        System.out.println("java -jar cadcDownloadManagerClient.jar [-v|--verbose | -d|--debug | -q|--quiet ]");
        System.out.println("          --uris=<comma-separated list of URIs>");
        System.out.println("         [ --fragment=<common fragment to append to all URIs> ]");
        System.out.println("         [ --ssocookie=<cookie value to use in sso authentication> ]");
        System.out.println("         [ --ssocookiedomain=<domain cookie is valid in (required with ssocookie arg)> ]");
        System.out.println("         [--headless] : run in non-interactive (no GUI) mode");
        System.out.println();
        System.out.println("optional arguments to use with --headless:");
        System.out.println("        --dest=<directory> : directory must exist and be writable by the user");
        System.out.println("        --decompress : decompress files after download (gzip,zip supported)");
        System.out.println("        --overwrite : overwrite existing files with the same name");
        System.out.println("        --threads=<number of threads> : allowed range is [1,11]");
        System.out.println("        --retry : retry (loop) when server reports it is too busy");
    }
}
