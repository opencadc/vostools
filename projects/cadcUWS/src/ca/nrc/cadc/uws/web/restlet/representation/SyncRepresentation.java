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

package ca.nrc.cadc.uws.web.restlet.representation;

import ca.nrc.cadc.uws.ErrorSummary;
import ca.nrc.cadc.uws.ErrorType;
import ca.nrc.cadc.uws.ExecutionPhase;
import ca.nrc.cadc.uws.Job;
import ca.nrc.cadc.uws.JobManager;
import ca.nrc.cadc.uws.PrivilegedActionJobRunner;
import ca.nrc.cadc.uws.SyncJobRunner;
import ca.nrc.cadc.uws.SyncOutput;
import ca.nrc.cadc.uws.TimeTrackingRunnable;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import javax.security.auth.Subject;
import org.apache.log4j.Logger;
import org.restlet.data.MediaType;
import org.restlet.representation.OutputRepresentation;

/**
 * @deprecated not possible to set the MediaType correctly
 * @see ca.nrc.cadc.uws.server.SyncServlet
 * @author jburke
 */
public class SyncRepresentation extends OutputRepresentation
{
    private static final Logger LOGGER = Logger.getLogger(SyncRepresentation.class);
    private Job job;
    private JobManager jobManager;
    private SyncJobRunner jobRunner;

    public SyncRepresentation(MediaType mediaType, Job job, JobManager jobManager, SyncJobRunner jobRunner)
    {
        super(mediaType);
        this.job = job;
        this.jobManager = jobManager;
        this.jobRunner = jobRunner;
    }

    @Override
    public void write(OutputStream out)
        throws IOException
    {
        try
        {
            SyncOutputImpl syncOutput = new SyncOutputImpl(out);
            jobRunner.setOutput(syncOutput);

            if (job.getOwner() == null)
            {
                Runnable r = new TimeTrackingRunnable(jobManager, jobRunner);
                r.run();
            }
            else
            {
                Subject.doAs(job.getOwner(), new PrivilegedActionJobRunner(jobManager, jobRunner));
            }

            // get current state
            job = jobManager.getJob(job.getID());
        }
        catch (Throwable t)
        {
            LOGGER.error("streaming output from " + jobRunner.getClass().getName()
                    + " failed for job " + job.getID(), t);
            try
            {
                ErrorSummary error = new ErrorSummary(t.getMessage(), ErrorType.FATAL, false);
                job.setErrorSummary(error);
                job.setExecutionPhase(ExecutionPhase.ERROR);
                job = jobManager.persist(job);
            }
            catch(Throwable oops)
            {
                LOGGER.error("failed to persist error stat for job " + job.getID());
            }
        }
    }

    class SyncOutputImpl implements SyncOutput
    {
        private OutputStream out;
                
        public SyncOutputImpl(OutputStream out)
        {
            this.out = new SafeOutputStream(out);
        }

        public void setHeader(String key, String value)
        {
            // silently ignore since OutputStream is already open
        }

        public OutputStream getOutputStream()
        {
            return out;
        }
    }

    private class SafeOutputStream extends FilterOutputStream
    {
        SafeOutputStream(OutputStream ostream) { super(ostream); }

        @Override
        public void close()
            throws IOException
        {
            // must not let the JobRunner call close on the OutputStream!!!
            LOGGER.warn("SafeOutputStream.close() was called");
        }
    }
}
