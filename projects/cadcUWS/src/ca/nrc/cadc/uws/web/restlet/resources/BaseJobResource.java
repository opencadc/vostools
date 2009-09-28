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

import ca.nrc.cadc.uws.Job;
import ca.nrc.cadc.uws.JobRunner;
import ca.nrc.cadc.uws.InvalidServiceException;
import ca.nrc.cadc.uws.JobAttribute;
import ca.nrc.cadc.uws.util.StringUtil;
import ca.nrc.cadc.uws.util.BeanUtil;
import org.w3c.dom.Element;
import org.w3c.dom.Document;
import org.restlet.Client;
import org.restlet.ext.xml.DomRepresentation;
import org.restlet.data.Protocol;
import org.restlet.Response;

import java.io.IOException;


/**
 * Base Job Resource to obtain Jobs.
 */
public abstract class BaseJobResource extends UWSResource
{
    /**
     * Obtain the current Job in the context of this Request.
     *
     * @return      This Request's Job.
     */
    protected Job getJob()
    {
        return getJobManager().getJob(getJobID());
    }


    /**
     * Obtain the current Job ID.
     *
     * @return  long Job ID
     */
    protected long getJobID()
    {
        return Long.parseLong(getRequestAttribute("jobID"));
    }

    /**
     * Obtain a new instance of the Job Runner interface as defined in the
     * Context
     *
     * @return  The JobRunner instance.
     */
    @SuppressWarnings("unchecked")
    protected JobRunner createJobRunner()
    {
        if (!StringUtil.hasText(
                getContext().getParameters().getFirstValue(
                        BeanUtil.UWS_RUNNER)))
        {
            throw new InvalidServiceException(
                    "The JobRunner is mandatory!\n\n Please set the "
                    + BeanUtil.UWS_RUNNER + "context-param in the web.xml, "
                    + "or insert it into the Context manually.");
        }

        final String jobRunnerClassName =
                getContext().getParameters().getFirstValue(BeanUtil.UWS_RUNNER);
        final BeanUtil beanUtil = new BeanUtil(jobRunnerClassName);

        return (JobRunner) beanUtil.createBean();
    }

    /**
     * Obtain the XML List element for the given Attribute.
     *
     * Remember, the Element returned here belongs to the Document from the
     * Response of the call to get the List.  This means that the client of
     * this method call will need to import the Element, via the
     * Document#importNode method, or an exception will occur.
     *
     * @param jobAttribute      The Attribute to obtain XML for.
     * @return                  The Element, or null if none found.
     * @throws java.io.IOException      If the Document could not be formed from the
     *                          Representation.
     */
    protected Element getRemoteElement(final JobAttribute jobAttribute)
            throws IOException
    {
        final StringBuilder elementURI = new StringBuilder(128);
        final Client client = new Client(getContext(), Protocol.HTTP);

        elementURI.append(getHostPart());
        elementURI.append("/async/");
        elementURI.append(getJobID());
        elementURI.append("/");
        elementURI.append(jobAttribute.getAttributeName());

        final Response response = client.get(elementURI.toString());
        final DomRepresentation domRep =
                new DomRepresentation(response.getEntity());
        final Document document = domRep.getDocument();

        document.normalizeDocument();

        return document.getDocumentElement();
    }    
}
