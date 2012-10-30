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


package ca.nrc.cadc.uws.web.restlet.resources;

import java.security.PrivilegedAction;

import javax.security.auth.Subject;

import org.apache.log4j.Logger;
import org.restlet.data.Form;
import org.restlet.representation.Representation;
import org.restlet.resource.Get;

import ca.nrc.cadc.net.TransientException;
import ca.nrc.cadc.util.StringUtil;
import ca.nrc.cadc.uws.ExecutionPhase;
import ca.nrc.cadc.uws.Job;
import ca.nrc.cadc.uws.JobAttribute;
import ca.nrc.cadc.uws.server.JobNotFoundException;
import ca.nrc.cadc.uws.server.JobPersistenceException;


/**
 * Base Job Resource to obtain Jobs.
 */
public abstract class BaseJobResource extends UWSResource
{
    private static final Logger LOGGER = Logger.getLogger(UWSResource.class);

    protected String jobID;
    protected String jobList;
    protected Job job;
    protected String protocol;

    @Override
    protected void doInit()
    {
        super.doInit();
        this.jobID = (String) getRequest().getAttributes().get("jobID");
        String path = getRequestPath();
        int i = path.indexOf(jobID);
        this.jobList = path.substring(0,i-1);
        this.protocol = getProtocol().getSchemeName();
        LOGGER.debug("doInit: jobID=" + jobID + ", jobList="+jobList + ", protocol="+protocol);
    }

    @Get
    @Override
    public Representation represent()
    {
        Subject subject = getSubject();
        if (subject == null) // anon
        {
            return doRepresent();
        }

        return (Representation) Subject.doAs(subject,
            new PrivilegedAction<Object>()
            {
                public Object run()
                {
                    return doRepresent();
                }
            } );
    }

    private Representation doRepresent()
    {
        try
        {
            if (job == null)
            {
                job = getJobManager().get(jobID);
                job.setProtocol(protocol);
            }
            return super.represent();
        }
        catch (TransientException t)
        {
            return generateRetryRepresentation(t);
        }
        catch(JobPersistenceException ex)
        {
            throw new RuntimeException(ex);
        }
        catch(JobNotFoundException ex)
        {
            throw new RuntimeException(ex);
        }
    }

    protected void redirectToJobList()
    {
        String url = getHostPart() + jobList;
        LOGGER.debug("redirectToJobList: " + url);
        redirectSeeOther(url);
    }
    
    protected void redirectToJob()
    {
        String url = getHostPart() + jobList + "/" + jobID;
        LOGGER.debug("redirectToJob: " + url);
        redirectSeeOther(url);
    }

    protected boolean jobIsActive(ExecutionPhase executionPhase)
    {
        return (executionPhase.equals(ExecutionPhase.QUEUED))
               || (executionPhase.equals(ExecutionPhase.EXECUTING));
    }

    protected boolean jobHasRun(ExecutionPhase executionPhase)
    {
        return executionPhase.equals(ExecutionPhase.COMPLETED)
               || executionPhase.equals(ExecutionPhase.ERROR)
               || executionPhase.equals(ExecutionPhase.ABORTED);
    }

    protected boolean jobIsPending(ExecutionPhase executionPhase)
    {
        return executionPhase.equals(ExecutionPhase.PENDING);
    }

    protected boolean jobModificationAllowed(Form form, ExecutionPhase executionPhase)
    {
        final String phase =
                form.getFirstValue(JobAttribute.EXECUTION_PHASE.
                        getAttributeName().toUpperCase());

        return jobIsPending(executionPhase) || ((getPathInfo().endsWith("phase")
                                   && StringUtil.hasLength(phase)
                                   && phase.equals("ABORT")));
    }

}
