/**
 ************************************************************************
 *******************  CANADIAN ASTRONOMY DATA CENTRE  *******************
 **************  CENTRE CANADIEN DE DONNÉES ASTRONOMIQUES  **************
 *
 *  (c) 2010.                            (c) 2010.
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
 ************************************************************************
 */
package ca.nrc.cadc.gms.security;

import org.restlet.routing.Filter;
import org.restlet.Request;
import org.restlet.Response;

import javax.security.auth.Subject;
import java.security.Principal;
import java.security.PrivilegedAction;
import java.security.cert.X509Certificate;
import java.util.Collection;


/**
 * Basic injector to add the current Subject to the thread.  This
 * implementation will simply perform a Subject.doAs and proceed with the
 * Request.
 */
public class BasicSubjectInjectorImpl extends Filter
{
    /**
     * Handles the call by distributing it to the next Restlet. If no Restlet is
     * attached, then a {@link org.restlet.data.Status#SERVER_ERROR_INTERNAL} status is returned.
     * Returns {@link #CONTINUE} by default.
     *
     * @param request  The request to handle.
     * @param response The response to update.
     * @return The continuation status. Either {@link #CONTINUE} or
     *         {@link #STOP}.
     */
    @Override
    protected int doHandle(final Request request, final Response response)
    {
        final Subject currentSubject = createSubject(request);

        return Subject.doAs(currentSubject, new PrivilegedAction<Integer>()
        {
            /**
             * Performs the computation.  This method will be called by
             * <code>AccessController.doPrivileged</code> after enabling privileges.
             *
             * @return a class-dependent value that may represent the results of the
             *         computation. Each class that implements
             *         <code>PrivilegedAction</code>
             *         should document what (if anything) this value represents.
             * @see java.security.AccessController#doPrivileged(java.security.PrivilegedAction)
             * @see java.security.AccessController#doPrivileged(java.security.PrivilegedAction ,
             *      java.security.AccessControlContext)
             */
            public Integer run()
            {
                return handleRequest(request, response);
            }
        });
    }

    /**
     * Handles the call by distributing it to the next Restlet. If no Restlet is
     * attached, then a {@link org.restlet.data.Status#SERVER_ERROR_INTERNAL} status is returned.
     * Returns {@link #CONTINUE} by default.
     *
     * @param request  The request to handle.
     * @param response The response to update.
     * @return The continuation status. Either {@link #CONTINUE} or
     *         {@link #STOP}.
     */
    private int handleRequest(final Request request, final Response response)
    {
        return super.doHandle(request, response);
    }

    /**
     * Create a subject, and add all of the Principals into it, including any
     * X509 Certificates from an SSL Request.
     *
     * @param request       The Request to create from.
     * @return              An instance of a Subject.
     */
    @SuppressWarnings("unchecked")
    private Subject createSubject(final Request request)
    {
        final Subject subject = new Subject();
        final Collection<Principal> principals =
                request.getClientInfo().getPrincipals();
        final Collection<X509Certificate> clientCertificates =
                (Collection<X509Certificate>) request.getAttributes().get(
                        "org.restlet.https.clientCertificates");

        subject.getPrincipals().addAll(principals);

        if ((clientCertificates != null) && (!clientCertificates.isEmpty()))
        {
            for (final X509Certificate X509Cert : clientCertificates)
            {
                final Principal principal = X509Cert.getSubjectX500Principal();

                if (principal != null)
                {
                    subject.getPrincipals().add(principal);
                }
            }
        }

        return subject;
    }
}
