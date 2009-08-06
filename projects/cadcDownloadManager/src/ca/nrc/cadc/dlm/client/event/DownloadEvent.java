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

package ca.nrc.cadc.dlm.client.event;

import java.io.File;
import java.net.URL;
import java.util.EventObject;

/**
 * Simple event that specifies a Download changed states.
 *
 * @version $Version$
 * @author pdowler
 */
public class DownloadEvent extends EventObject
{
    private static int MIN_STATE = 0;
    public static final int CONNECTING = 1;
    public static final int CONNECTED  = 2;
    public static final int DOWNLOADING = 3;
    public static final int DECOMPRESSING = 4;
    public static final int COMPLETED  = 5;
    public static final int CANCELLED  = 6;
    public static final int FAILED     = 7;
    private static int MAX_STATE = 8;
    private String[] states = new String[]
    {
        "min", "CONNECTING", "CONNECTED", "DOWNLOADING", "DECOMPRESSING", "COMPLETED", "CANCELLED", "FAILED", "max"
    };
    
    private URL url;
    private File file;
    private int state;
    private Throwable error;
    private String eventID;
    
    private int startingPos; // for resumed download only
    
    /**
     * Convenience constructor for a COMPLETED download event.
     * @param source
     * @param url
     * @param file
     */
    public DownloadEvent(Object source, String eventID, URL url, File file)
    {
        this(source, eventID, url, file, COMPLETED, null);
    }

    /**
     * Convenience constructor for a FAILED download event.
     * @param source
     * @param url
     * @param file
     * @param error
     */
    public DownloadEvent(Object source, String eventID, URL url, File file, Throwable error)
    {
        this(source, eventID, url, file, FAILED, error);
    }
    
    /**
     * Generic sate transition constructor.
     */
    public DownloadEvent(Object source, String eventID, URL url, File file, int state)
    {
        this(source, eventID, url, file, state, (Throwable) null);
    }
    
    private DownloadEvent(Object source, String eventID, URL url, File file, int state, Throwable error)
    {
        super(source);
        this.eventID = eventID;
        this.url = url;
        this.file = file;
        this.state = state;
        this.error = error;
        if (error != null && state != FAILED)
            throw new IllegalArgumentException("state: " + state + " error: " + error);
        if (state <= MIN_STATE || state >= MAX_STATE)
            throw new IllegalArgumentException("unknown state: " + state);
    }
  
    public int getState() { return state; }
    
    /**
     * Get the eventID for the download.
     * 
     * @return
     */
    public String getEventID() { return eventID; }
    
    /**
     * Get the source URL for the download.
     * 
     * @return
     */
    public URL getURL() { return url; }
    
    /**
     * Get the destination file.
     * 
     * @return
     */
    public File getFile() { return file; }
    
    /**
     * Get associated error. This is null if the download completed successfully or was cancelled.
     * 
     * @return error that caused failure
     */
    public Throwable getError() { return error; }
    
    public void setStartingPosition(int start) { this.startingPos = start; }
    
    public int getStartingPosition() { return startingPos; }
    
    public String toString() 
    {
        return "DownloadEvent[url=" + url + ", file=" + file + ",state=" + state + "(" + states[state] + "), error=" + error + "]";
    }
}
