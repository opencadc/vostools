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

package ca.nrc.cadc.uws.web.restlet;

import ca.nrc.cadc.uws.ExecutionPhase;
import ca.nrc.cadc.uws.Job;
import ca.nrc.cadc.uws.JobAttribute;
import ca.nrc.cadc.uws.Parameter;
import ca.nrc.cadc.uws.web.InlineContentHandler;
import ca.nrc.cadc.uws.web.JobCreator;
import ca.nrc.cadc.uws.web.restlet.validators.JobFormValidatorImpl;
import ca.nrc.cadc.uws.web.validators.FormValidator;
import java.io.IOException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.commons.fileupload.FileItemIterator;
import org.apache.commons.fileupload.FileUploadException;
import org.apache.log4j.Logger;
import org.restlet.data.Form;
import org.restlet.data.MediaType;
import org.restlet.ext.fileupload.RestletFileUpload;

import org.restlet.representation.Representation;

/**
 * Simple class to assemble items from a Request into a job.
 */
public class RestletJobCreator extends JobCreator
{
    private final static Logger log = Logger.getLogger(RestletJobCreator.class);

    public RestletJobCreator(InlineContentHandler inlineContentHandler)
    {
        super(inlineContentHandler);
    }

    public Job create(Representation entity)
        throws FileUploadException, IOException
    {
        Job job = new Job();
        job.setExecutionPhase(ExecutionPhase.PENDING);
        job.setParameterList(new ArrayList<Parameter>());

        if (entity == null || entity.getMediaType().equals(MediaType.APPLICATION_WWW_FORM, true))
        {
            Form form = new Form(entity);
            FormValidator validator = new JobFormValidatorImpl(form);
            Map<String, String> errors = validator.validate();
            if (!errors.isEmpty())
            {
                String message = getErrorMessage(errors);
                log.error(message);
                throw new WebRepresentationException(message);
            }

            Set<String> names = form.getNames();
            for (String name : names)
                processParameter(job, name, form.getValuesArray(name, true));
        }
        else if (inlineContentHandler != null)
        {
            if (entity.getMediaType().equals(MediaType.MULTIPART_FORM_DATA, true))
            {
                RestletFileUpload upload = new RestletFileUpload();
                FileItemIterator itemIterator = upload.getItemIterator(entity);
                processMultiPart(job, itemIterator);
            }
            else
            {
                processStream(null, entity.getMediaType().getName(), entity.getStream());
            }
            inlineContentHandler.setParameterList(job.getParameterList());
            job.setParameterList(inlineContentHandler.getParameterList());
            job.setJobInfo(inlineContentHandler.getJobInfo());
        }

        return job;
    }

    // this is called by JobAsynchResource and ParameterListResource, could be refactored
    // to be less wasteful
    public List<Parameter> getParameterList(Form form)
    {
        Job job = new Job();
        for (String name : form.getNames())
            processParameter(job, name, form.getValuesArray(name, true));
        return job.getParameterList();
    }

    private String getErrorMessage(Map<String, String> errors)
    {
        StringBuilder sb = new StringBuilder();
        sb.append("Errors found during Job Creation: \n");
        for (Map.Entry<String, String> error : errors.entrySet())
        {
            sb.append("\n");
            sb.append(error.getKey());
            sb.append(": ");
            sb.append(error.getValue());
        }
        return sb.toString();
    }
    
}
