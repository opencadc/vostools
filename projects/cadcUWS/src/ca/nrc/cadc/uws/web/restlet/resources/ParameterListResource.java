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

import org.jdom2.Document;
import org.restlet.data.Form;
import org.restlet.representation.Representation;
import org.restlet.resource.Post;

import ca.nrc.cadc.net.TransientException;
import ca.nrc.cadc.uws.JobWriter;
import ca.nrc.cadc.uws.server.JobNotFoundException;
import ca.nrc.cadc.uws.server.JobPersistenceException;
import ca.nrc.cadc.uws.server.JobPhaseException;
import ca.nrc.cadc.uws.web.restlet.RestletJobCreator;

/**
 * Resource to handle the Parameter List.
 */
public class ParameterListResource extends BaseJobResource
{
    /**
     * POST Parameter data to this Job.
     *
     * @param entity    The Representation Entity.
     */
    @Post
    public void accept(final Representation entity)
    {
        Subject subject = getSubject();
        if (subject == null) // anon
        {
            doAccept(entity);
        }
        else
        {
            Subject.doAs(subject, new PrivilegedAction<Object>()
            {
                public Object run()
                {
                    doAccept(entity);
                    return null;
                }
            } );
        }
    }

    private void doAccept(final Representation entity)
    {
        try
        {
            // TODO: only allow in PENDING phase, but optimal
            RestletJobCreator jobCreator = new RestletJobCreator(null);
            getJobManager().update(jobID, jobCreator.getParameterList(new Form(entity)));
            redirectToJob();
        }
        catch(JobNotFoundException ex)
        {
            throw new RuntimeException(ex);
        }
        catch (TransientException t)
        {
            generateRetryRepresentation(t);
        }
        catch(JobPersistenceException ex)
        {
            throw new RuntimeException(ex);
        }
        catch(JobPhaseException ex)
        {
            throw new RuntimeException(ex);
        }
    }

    /**
     * Assemble the appropriate XML and build the given Document.
     *
     * @param document      The Document to build.
     */
    protected void buildXML(Document document)
    {
        JobWriter jobWriter = new JobWriter();
        document.addContent(jobWriter.getParameters(job));
    }

}
