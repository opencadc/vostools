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

import org.restlet.Restlet;
import org.restlet.Context;
import org.restlet.routing.Router;
import ca.nrc.cadc.uws.util.BeanUtil;
import ca.nrc.cadc.uws.JobManager;
import ca.nrc.cadc.uws.JobPersistence;


/**
 * The UWS Restlet Application to handle Asynchronous calls.
 */
public class UWSAsyncApplication extends AbstractUWSApplication
{
    /**
     * Constructor. Note this constructor is convenient because you don't have
     * to provide a context like for {@link #Application(org.restlet.Context)}.
     * Therefore the context will initially be null. It's only when you attach
     * the application to a virtual host via one of its attach*() methods that
     * a proper context will be set.
     */
    public UWSAsyncApplication()
    {
        init();
    }

    /**
     * Constructor.
     *
     * @param context The context to use based on parent component context. This
     *                context should be created using the
     *                {@link org.restlet.Context#createChildContext()} method to
     *                ensure a proper isolation with the other applications.
     */
    public UWSAsyncApplication(final Context context)
    {
        super(context);
        init();
    }


    /**
     * Creates a root Restlet that will receive all incoming calls. In general,
     * instances of Router, Filter or Handler classes will be used as initial
     * application Restlet. The default implementation returns null by default.
     * This method is intended to be overridden by subclasses.
     *
     * This method will also setup singleton Service objects in the Context.
     * This gets done here so as to ensure the Context is properly initialized.
     *
     * @return The root Restlet.
     */
    @Override
    public Restlet createRoot()
    {
        final Router router = new UWSAsyncRouter(getContext());

        getContext().getAttributes().put(
                BeanUtil.UWS_EXECUTOR_SERVICE,
                createBean(BeanUtil.UWS_EXECUTOR_SERVICE, true));

        final JobManager jobManager =
                (JobManager) createBean(BeanUtil.UWS_JOB_MANAGER_SERVICE,
                                        true);
        final JobPersistence jobPersistence =
                (JobPersistence) createBean(BeanUtil.UWS_PERSISTENCE, true);

        jobManager.setJobPersistence(jobPersistence);

        getContext().getAttributes().put(BeanUtil.UWS_JOB_MANAGER_SERVICE,
                                         jobManager);

        getContext().getAttributes().put(BeanUtil.UWS_PERSISTENCE,
                                         jobPersistence);        

        return router;
    }
}
