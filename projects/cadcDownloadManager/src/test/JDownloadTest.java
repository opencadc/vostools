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

package test;

import ca.nrc.cadc.dlm.client.event.ConsoleEventLogger;
import ca.nrc.cadc.dlm.client.Download;
import ca.nrc.cadc.dlm.client.JDownload;

import ca.onfire.ak.AbstractApplication;
import ca.onfire.ak.ApplicationFrame;
import java.awt.BorderLayout;

import java.io.File;
import java.net.URL;
import javax.swing.JPanel;

/**
 * TODO.
 *
 * @author pdowler
 */
public class JDownloadTest extends AbstractApplication
{
    public static void main(String[] args)
    {
        String baseURL = "http://cadcweb0/getData?";
        try
        {
            Download dl = new Download();
            
            //dl.url = new URL(baseURL + "archive=HST&file_id=U27R9G01B.2");
            //dl.label = "HST/U27R9G01B.2";
            dl.url = new URL(baseURL + "archive=HSTCA&file_id=J8FU02030_DRZ");
            dl.label = "HSTCA/J8FU02030_DRZ";
            //dl.url = new URL(baseURL + "archive=CFHT&file_id=535741p");
            //dl.label = "CFHT/535741p";
            
            dl.decompress = true;
            dl.overwrite = true;
            dl.destDir = new File("/tmp");
            
            dl.setDownloadListener(new ConsoleEventLogger());
            
            JDownloadTest ui = new JDownloadTest(dl);
            ApplicationFrame frame  = new ApplicationFrame("JDownloadTest", ui);
            frame.getContentPane().add(ui);
            frame.setVisible(true);
                        
            Thread.sleep(3000L);
            long t1 = System.currentTimeMillis();
            dl.run();
            long dt = System.currentTimeMillis() - t1;
            dt /= 1000L;
            
            msg("duration: " + dt + " sec");
            
            msg("output: " + dl.destFile);
            msg("skipped: " + dl.skipped);
            msg("failure: " + dl.failure);
            msg("eventID: " + dl.eventID);
        }
        catch(Throwable t) { t.printStackTrace(); }
    }
    
    public JDownloadTest(Download dl)
    {
        super(new BorderLayout());
        JDownload jdl = new JDownload(dl);
        add(new JPanel(), BorderLayout.CENTER);
        add(jdl, BorderLayout.NORTH);
    }
    
    protected void makeUI()
    {
        
    }
    
    private static void msg(String s)
    {
         System.out.println("[JDownloadTest] " + s);
    }
}
