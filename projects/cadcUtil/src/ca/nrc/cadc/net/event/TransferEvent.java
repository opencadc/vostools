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


package ca.nrc.cadc.net.event;

import ca.nrc.cadc.util.FileMetadata;
import java.io.File;
import java.net.URL;
import java.util.EventObject;

/**
 * Simple event that specifies a Download changed states.
 *
 * @version $Version$
 * @author pdowler
 */
public class TransferEvent extends EventObject
{
    private static final long serialVersionUID = 201008051500L;
    
    private static int MIN_STATE = 0;
    public static final int CONNECTING = 1;
    public static final int CONNECTED  = 2;
    public static final int TRANSFERING = 3;
    public static final int DECOMPRESSING = 4;
    public static final int COMPLETED  = 5;
    public static final int CANCELLED  = 6;
    public static final int FAILED     = 7;
    public static final int DELETED = 8;
    public static final int RETRYING = 9;
    private static int MAX_STATE = 10;
    private String[] states = new String[]
    {
        "min", 
        "CONNECTING", "CONNECTED", "TRANSFERING", "DECOMPRESSING",
        "COMPLETED", "CANCELLED", "FAILED", "DELETED", "RETRYING",
        "max"
    };
    
    private URL url;
    private File file;
    private FileMetadata meta;
    private int state;
    private Throwable error;
    private String eventID;
    
    private int startingPos; // for resumed download only
    
    /**
     * Convenience constructor for a COMPLETED download event.
     * @param source
     * @param eventID
     * @param url
     * @param file
     */
    public TransferEvent(Object source, String eventID, URL url, File file)
    {
        this(source, eventID, url, file, COMPLETED, null);
    }

    /**
     * Convenience constructor for a FAILED download event.
     * @param source
     * @param eventID
     * @param url
     * @param file
     * @param error
     */
    public TransferEvent(Object source, String eventID, URL url, File file, Throwable error)
    {
        this(source, eventID, url, file, FAILED, error);
    }
    
    /**
     *  Constructor for state transition events.
     *
     * @param source
     * @param eventID
     * @param url
     * @param file
     * @param state
     */
    public TransferEvent(Object source, String eventID, URL url, File file, int state)
    {
        this(source, eventID, url, file, state, (Throwable) null);
    }
    
    private TransferEvent(Object source, String eventID, URL url, File file, int state, Throwable error)
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

    /**
     * Get the state. TODO: change this to an enum.
     *
     * @return
     */
    public int getState() { return state; }

    /**
     * Get the state as a string. This is useful for debugging (eg when getting an unexpected state).
     *
     * @return
     */
    public String getStateLabel() { return states[state]; }
    
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
     * Get metadata about the file acquired during or after the transfer.
     * @return
     */
    public FileMetadata getFileMetadata() { return meta; }

    public void setFileMetadata(FileMetadata meta) { this.meta = meta; }
    
    /**
     * Get associated error. This is null if the download completed successfully or was cancelled.
     * 
     * @return error that caused failure
     */
    public Throwable getError() { return error; }
    
    public void setStartingPosition(int start) { this.startingPos = start; }
    
    public int getStartingPosition() { return startingPos; }
    
    public boolean isFinalState()
    {
        if (state == COMPLETED ||
            state == CANCELLED ||
            state == FAILED)
        {
            return true;
        }
        return false;
    }

    @Override
    public String toString() 
    {
        return this.getClass().getSimpleName()
                + "[url=" + url + ", file=" + file + ",state=" + state + "(" + states[state] + "), error=" + error + "]";
    }
}
