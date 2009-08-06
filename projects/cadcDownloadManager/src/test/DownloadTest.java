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
import ca.nrc.cadc.dlm.client.event.DownloadEvent;
import ca.nrc.cadc.dlm.client.event.ProgressListener;
import java.io.File;
import java.net.URL;

/**
 * TODO.
 *
 * @author pdowler
 */
public class DownloadTest
{
    public static void main(String[] args)
    {
        String baseURL = "http://scapa/getData?";
        try
        {
            Download dl = new Download();
            PL prog = new PL();
                
            dl.setDownloadListener(new ConsoleEventLogger());
            dl.setProgressListener(prog);
            
            //dl.url = new URL(baseURL + "archive=HST&file_id=U27R9G01B.2");
            //dl.label = "HST/U27R9G01B.2";
            //dl.url = new URL(baseURL + "archive=HSTCA&file_id=J8FU02030_DRZ");
            //dl.label = "HSTCA/J8FU02030_DRZ";
            //dl.url = new URL(baseURL + "archive=CFHT&file_id=535741p&cutout=[1]&compression=off");
            //dl.label = "CFHT/535741p";
            dl.url = new URL(baseURL + "archive=CFHT&file_id=686424o");
            dl.label = "CFHT/686424o";
            
            dl.decompress = false;
            dl.overwrite = true;
            dl.destDir = new File("/tmp");
            
            long t1 = System.currentTimeMillis();
            dl.run();
            long dt = System.currentTimeMillis() - t1;
            // average rate
            long rate = 1000 * (prog.totalBytes/1024) / dt;
            
            msg("duration: " + dt + " ms");
            msg("    rate: " + rate +  " KB/sec");
            msg("  output: " + dl.destFile);
            msg(" skipped: " + dl.skipped);
            msg(" failure: " + dl.failure);
            msg(" eventID: " + dl.eventID);
        }
        catch(Throwable t) { t.printStackTrace(); }
    }
    
    private static void msg(String s)
    {
         System.out.println("[DownloadTest] " + s);
    }

    private static class PL implements ProgressListener
    {
        long lastUpdate;
        int totalBytes;
        public void update(int numBytes, int totalBytes)
        {
            this.totalBytes = totalBytes;
            // try to not fire too many UI updates into the EventQueue
            long t = System.currentTimeMillis();
            long dt = t - lastUpdate;
            if (dt > 200)
            {
                lastUpdate = t;
                System.out.println("[PL] update: " + totalBytes);
            }
         }
        
         public void downloadEvent(DownloadEvent e) 
         {
             System.out.println("[PL] downloadEvent: " + e); 
         }

        public String getEventHeader()
        {
            return null;
        }
         
         
    }
}
