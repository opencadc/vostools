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

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.restlet.resource.Post;
import org.restlet.representation.Representation;
import org.restlet.data.Form;

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
        redirectSeeOther(getHostPart() + "/async/" + persistedJob.getJobId());        
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
