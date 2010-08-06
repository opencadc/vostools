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


package test;

import ca.nrc.cadc.dlm.client.event.ConsoleEventLogger;
import ca.nrc.cadc.dlm.client.JDownload;
import ca.nrc.cadc.net.HttpDownload;
import ca.nrc.cadc.util.Log4jInit;

import ca.onfire.ak.AbstractApplication;
import ca.onfire.ak.ApplicationFrame;
import java.awt.BorderLayout;

import java.io.File;
import java.net.URL;
import javax.swing.JPanel;
import org.apache.log4j.Level;

/**
 * TODO.
 *
 * @author pdowler
 */
public class JDownloadTest extends AbstractApplication
{
    public static void main(String[] args)
    {
        String baseURL = "http://cadcweb0/getData/anon/";
        try
        {
            Log4jInit.setLevel("ca.nrc.cadc", Level.DEBUG);

            HttpDownload dl = new HttpDownload(
                new URL(baseURL + "HSTCA/J8FU02030_DRZ"),
                new File("/tmp")
            );
            
            dl.setDecompress(true);
            dl.setOverwrite(true);
            
            dl.setTransferListener(new ConsoleEventLogger());
            
            JDownloadTest ui = new JDownloadTest(dl);
            ApplicationFrame frame  = new ApplicationFrame("JDownloadTest", ui);
            frame.getContentPane().add(ui);
            frame.setVisible(true);
                        
            Thread.sleep(3000L);
            long t1 = System.currentTimeMillis();
            Thread t = new Thread(dl);
            t.start();
            t.join();
            
            long dt = System.currentTimeMillis() - t1;
            dt /= 1000L;
            
            msg("duration: " + dt + " sec");
            
            msg("output: " + dl.getFile());
            msg("failure: " + dl.getThrowable());
        }
        catch(Throwable t) { t.printStackTrace(); }
    }

    HttpDownload download;
    
    public JDownloadTest(HttpDownload dl)
    {
        super(new BorderLayout());
        this.download = dl;
    }
    
    protected void makeUI()
    {
        JDownload jdl = new JDownload(download);
        add(new JPanel(), BorderLayout.CENTER);
        add(jdl, BorderLayout.NORTH);
    }

    private static void msg(String s)
    {
         System.out.println("[JDownloadTest] " + s);
    }
}
