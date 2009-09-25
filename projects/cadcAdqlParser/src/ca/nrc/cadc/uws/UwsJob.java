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



package ca.nrc.cadc.uws;

import ca.nrc.cadc.util.DateUtil;
import java.net.URI;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.apache.log4j.Logger;

/**
 * Base class for a UWS Job. Specific applications can use this directly or
 * subclass to add methods as desired, but for saving and restoring state only
 * the member variables in this class should be used.
 * 
 * @author pdowler
 */
public class UwsJob 
{
    private static Logger log = Logger.getLogger(UwsJob.class);
    public static Long MAX_EXEC_TIME = new Long(5*60);             // 5 minutes
    public static Long MAX_LIFE_TIME = new Long(7*24*60*60*1000);    // 7 days hours
    
    public static String PHASE_PENDING = "PENDING";
    public static String PHASE_QUEUED = "QUEUED";
    public static String PHASE_EXECUTING = "EXECUTING";
    public static String PHASE_COMPLETED = "COMPLETED";
    public static String PHASE_ERROR = "ERROR";
    public static String PHASE_ABORTED = "ABORTED";
    
    // standard UWS resources under a job
    public static String QUOTE = "quote";
    public static String TERMINATION = "termination";
    public static String DESTRUCTION = "destruction";
    public static String PHASE = "phase";
    public static String START_TIME = "startTime";
    public static String END_TIME = "endTime";
    public static String RESULTS = "results";
    public static String ERROR = "error";
    
    // custom job resources
    public static String SYNC_RESULT = "syncResult";
    public static String SYNC_POKE = "poke";
    
    // package access so JobORM can set it
    boolean deleted;
    String jobID;
    Date creation;
    Date lastModified;
    
    private Date quote;
    private Long termination;
    private Date destruction;
    private String phase;
    private Date startTime;
    private Date endTime;
    
    List resources;
    List results;
    UwsError error;
    
    public UwsJob() 
    { 
        this.phase = PHASE_PENDING;
        this.termination = MAX_EXEC_TIME;
        this.quote = new Date(System.currentTimeMillis() + 1000L*termination.longValue());
        this.destruction = new Date(System.currentTimeMillis() + MAX_LIFE_TIME.longValue());
        this.results = new ArrayList();
        this.resources = new ArrayList();
        
        this.creation = new Date();
        this.lastModified = new Date();
    }
    
    public String toString() { return "UwsJob[" + jobID + "," + phase + "," + deleted + "]"; }
    
    public String getID() { return jobID; }

    public String getPhase() { return phase; }
    
    public void setPhase(String phase) { this.phase = phase; }
    
    public Date getQuote() { return quote; }
    
    public void setQuote(Date quote) { this.quote = quote; }
    
    public Date getDestruction() { return destruction; }
    
    public void setDestruction(Date destruction) { this.destruction = destruction; }

    public Long getTermination() { return termination; }
    
    public void setTermination(Long termination) { this.termination = termination; }
    
    public Date getStartTime() { return startTime; }
    
    public void setStartTime(Date st) { this.startTime = st; }
    
    public Date getEndTime() { return endTime; }
    
    public void setEndTime(Date et) { this.endTime = et; }
    
     /**
     * The UWS error resource. When this error is set (non-null) access to some 
     * other UWS resources (POSTing to phase, for example) or to application 
     * specific resources will be redirected to the UWS error resource. An application
     * is free to clear the error as a side-effect of other state changes as
     * desired.
     */
    public UwsError getError() { return error; }
    
    /**
     * Set the UWS error resource. The phase is null, it is set to a
     * default value of PHASE_FAILED when error is not null and PHASE_CREATED
     * when error is null. Thus, calling setError(null) will reset the job to 
     * the initial (created) state. It is the callers responsibility to clear 
     * or reset the param and result maps.
     * 
     * @param error
     * @param phase
     */
    public void setError(UwsError error) 
    {
        if (error == null)
        {
            // clear the error
            this.error = null;
            this.phase = PHASE_PENDING;
            return;
        }
        this.error = error; 
        this.phase = PHASE_ERROR;
    }
    
    
    /**
     * A List of UwsResult objects. 
     * 
     * @return List of UwsResult
     */
    public List getResults() { return results; }
    
    /**
     * Add a new result to the UWS result list. 
     * If either the name or the uri are null, the result is silently ignored.
     * 
     * @param name
     * @param uri
     */
    public void addResult(String name, URI uri)
    {
        if (name == null || uri == null)
            return;
        results.add( new UwsResult(name, uri));
    }

    public List getResources() { return resources; }
    
    public boolean addResource(String key, Object value)
    {
        log.debug("addResource: " + key + " = " + value);
        if (key == null || value == null)
            return false;
        if (value instanceof String || value instanceof URI)
        {
            resources.add(new UwsResource(key, value));
            return true;
        }
        throw new IllegalArgumentException("illegal UwsResource value type: " + value.getClass().getName());
    }
    
    public UwsResource getResource(String name)
    {
        for (int i=0; i<resources.size(); i++)
        {
            UwsResource ur = (UwsResource) resources.get(i);
            if (ur.name.equals(name))
                return ur;
        }
        return null;
    }

    public static String valueToString(Object value)
    {
        if (value == null)
            return "";
        if (value instanceof String || value instanceof Number || value instanceof URI)
            return value.toString();
        if (value instanceof Date)
        {
            DateFormat df = new SimpleDateFormat(DateUtil.ISO_DATE_FORMAT_TZ);
            return df.format((Date) value);
        }
        if (value instanceof byte[])
        {
            byte[] b = (byte[]) value;
            return "TODO: byte[] of length " + b.length;
        }
        throw new IllegalArgumentException("illegal UwsParam value type: " + value.getClass().getName());
    }
}
