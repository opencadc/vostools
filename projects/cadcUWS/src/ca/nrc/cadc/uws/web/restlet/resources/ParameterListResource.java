/******************************************************************************
 *
 *  Copyright (C) 2009                          Copyright (C) 2009
 *  National Research Council           Conseil national de recherches
 *  Ottawa, Canada, K1A 0R6                     Ottawa, Canada, K1A 0R6
 *  All rights reserved                         Tous droits reserves
 *
 *  NRC disclaims any warranties,       Le CNRC denie toute garantie
 *  expressed, implied, or statu-       enoncee, implicite ou legale,
 *  tory, of any kind with respect      de quelque nature que se soit,
 *  to the software, including          concernant le logiciel, y com-
 *  without limitation any war-         pris sans restriction toute
 *  ranty of merchantability or         garantie de valeur marchande
 *  fitness for a particular pur-       ou de pertinence pour un usage
 *  pose.  NRC shall not be liable      particulier.  Le CNRC ne
 *  in any event for any damages,       pourra en aucun cas etre tenu
 *  whether direct or indirect,         responsable de tout dommage,
 *  special or general, consequen-      direct ou indirect, particul-
 *  tial or incidental, arising         ier ou general, accessoire ou
 *  from the use of the software.       fortuit, resultant de l'utili-
 *                                                              sation du logiciel.
 *
 *
 *  This file is part of cadcUWS.
 *
 *  cadcUWS is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  cadcUWS is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public License
 *  along with cadcUWS.  If not, see <http://www.gnu.org/licenses/>.
 *
 ******************************************************************************/

package ca.nrc.cadc.uws.web.restlet.resources;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.restlet.resource.Post;
import org.restlet.representation.Representation;
import org.restlet.data.Form;
import org.restlet.data.Status;

import ca.nrc.cadc.uws.JobAttribute;
import ca.nrc.cadc.uws.Parameter;
import ca.nrc.cadc.uws.Job;

import java.util.Map;


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
        final Form form = new Form(entity);
        final Map<String, String> valuesMap = form.getValuesMap();
        final Job job = getJob();

        for (final Map.Entry<String, String> entry : valuesMap.entrySet())
        {
            job.addParameter(new Parameter(entry.getKey(), entry.getValue()));
        }

        final Job persistedJob = getJobManager().persist(job);
        
        getResponse().setStatus(Status.REDIRECTION_SEE_OTHER);
        getResponse().setLocationRef(getContextPath() + "/async/"
                                     + persistedJob.getJobId());        
    }

    /**
     * Assemble the appropriate XML and build the given Document.
     *
     * @param document      The Document to build.
     */
    protected void buildXML(final Document document)
    {
        final Element parametersListElement =
                document.createElementNS(XML_NAMESPACE_URI,
                                         JobAttribute.PARAMETERS.
                                                 getAttributeName());
        parametersListElement.setPrefix(XML_NAMESPACE_PREFIX);

        for (final Parameter parameter : getJob().getParameterList())
        {
            final Element parameterElement =
                    document.createElementNS(XML_NAMESPACE_URI,
                                             JobAttribute.PARAMETER.
                                                     getAttributeName());
            parameterElement.setPrefix(XML_NAMESPACE_PREFIX);
            parameterElement.setAttribute("id", parameter.getName());
            parameterElement.setTextContent(parameter.getValue());
            parametersListElement.appendChild(parameterElement);
        }

        document.appendChild(parametersListElement);       
    }
}
