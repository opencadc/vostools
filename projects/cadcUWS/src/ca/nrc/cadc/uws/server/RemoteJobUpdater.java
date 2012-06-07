/*
************************************************************************
*******************  CANADIAN ASTRONOMY DATA CENTRE  *******************
**************  CENTRE CANADIEN DE DONNÉES ASTRONOMIQUES  **************
*
*  (c) 2011.                            (c) 2011.
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
*  $Revision: 5 $
*
************************************************************************
*/

package ca.nrc.cadc.uws.server;

import ca.nrc.cadc.net.HttpDownload;
import ca.nrc.cadc.util.Base64;
import ca.nrc.cadc.uws.ErrorSummary;
import ca.nrc.cadc.uws.ExecutionPhase;
import ca.nrc.cadc.uws.Result;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Date;
import java.util.List;
import org.apache.log4j.Logger;

/**
 * Simple client class to send job updates to the JobUpdaterServlet.
 * 
 * @author pdowler
 */
public class RemoteJobUpdater implements JobUpdater
{
    private static final Logger log = Logger.getLogger(RemoteJobUpdater.class);

    private URL baseURL;

    public RemoteJobUpdater(URL baseURL)
    {
        this.baseURL = baseURL;
    }


    public ExecutionPhase getPhase(String jobID)
        throws JobNotFoundException, JobPersistenceException
    {
        try
        {
            URL u = new URL(baseURL.toExternalForm() + "/" + jobID);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            HttpDownload get = new HttpDownload(u, out);
            get.setUserAgent(this.getClass().getName());
            get.run();
            if (get.getThrowable() != null)
            {
                if (get.getThrowable().getMessage().contains("not found"))
                    throw new JobNotFoundException("not found: " + jobID);
                throw new JobPersistenceException("failed to get " + jobID, get.getThrowable());
            }
            String phase = out.toString().trim();
            log.debug("phase: " + phase);
            ExecutionPhase ret = ExecutionPhase.toValue(phase);
            return ret;
        }
        catch(MalformedURLException bug)
        {
            throw new RuntimeException("BUG - failed to create valid URL", bug);
        }
    }

    /**
     * Set the phase.
     * @param jobID
     * @param ep
     * @throws JobNotFoundException
     * @throws JobPersistenceException
     */
    public void setPhase(String jobID, ExecutionPhase ep)
        throws JobNotFoundException, JobPersistenceException
    {
        throw new UnsupportedOperationException();
    }

    /**
     * Conditionally change phase from start to end.
     *
     * @param jobID
     * @param start
     * @param end
     * @return the final phase (end) or null if not successful
     * @throws JobNotFoundException
     */
    public ExecutionPhase setPhase(String jobID, ExecutionPhase start, ExecutionPhase end)
        throws JobNotFoundException, JobPersistenceException
    {
        return setPhase(jobID, start, end, null);
    }

    /**
     * Conditionally set the phase.
     * 
     * @param jobID
     * @param start
     * @param end
     * @param date ignored (server generates date)
     * @return the final phase (end) or null if not successful
     * @throws JobNotFoundException
     * @throws JobPersistenceException
     */
    public ExecutionPhase setPhase(String jobID, ExecutionPhase start, ExecutionPhase end, Date date)
        throws JobNotFoundException, JobPersistenceException
    {
        ErrorSummary es = null;
        return setPhase(jobID, start, end, es, date);
    }

    /**
     * Not supported in this implementation.
     *
     * @param jobID
     * @param start
     * @param end
     * @param results
     * @param date
     * @return the final phase (end) or null if not successful
     * @throws JobNotFoundException
     */
    public ExecutionPhase setPhase(String jobID, ExecutionPhase start, ExecutionPhase end, List<Result> results, Date date)
        throws JobNotFoundException, JobPersistenceException
    {
        throw new UnsupportedOperationException();
    }

    /**
     * Conditional phase change with error summary. 
     * @param jobID
     * @param start
     * @param end
     * @param error
     * @param date ignored (server generates date)
     * @return the final phase (end) or null if not successful
     * @throws JobNotFoundException
     * @throws JobPersistenceException
     */
    public ExecutionPhase setPhase(String jobID, ExecutionPhase start, ExecutionPhase end, ErrorSummary error, Date date)
        throws JobNotFoundException, JobPersistenceException
    {
        if (start == null)
            throw new IllegalArgumentException("start phase cannot be null");
        if (end == null)
            throw new IllegalArgumentException("end phase cannot be null");
        try
        {
            StringBuilder sb = new StringBuilder();
            sb.append(baseURL.toExternalForm());
            sb.append("/");
            sb.append(jobID);
            sb.append("/");
            sb.append(start.getValue());
            sb.append("/");
            sb.append(end.getValue());
            if (error != null)
            {
                String msg = Base64.encodeString(error.getSummaryMessage());
                String et = error.getErrorType().name();
                sb.append("/");
                sb.append(msg);
                sb.append("/");
                sb.append(et);
            }
            URL u = new URL(sb.toString());
            log.debug("POST: " + u);
            HttpURLConnection con = (HttpURLConnection) u.openConnection();
            con.setRequestMethod("POST");
            int code = con.getResponseCode();
            String msg = con.getResponseMessage();
            if (code == 200)
                return end;
            if (code == 404)
                throw new JobNotFoundException(msg);
            throw new JobPersistenceException("failed to update job " + jobID + ", reason: [" + code + "] " + msg);
        }
        catch(MalformedURLException bug)
        {
            throw new RuntimeException("BUG - failed to create valid URL", bug);
        }
        catch(IOException ex)
        {
            throw new JobPersistenceException("failed to update job " + jobID, ex);
        }
    }
}
