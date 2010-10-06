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

package ca.nrc.cadc.uws;

import java.net.URL;

/**
 * Runner interface that will handle the synchronous execution of a Job.
 * An implementation of this interface is required to use the UWSSyncApplication.
 * </p><p>
 * The implementation class name used must be configured as a context-param with
 * key <code>ca.nrc.cadc.uws.JobRunner</code>.
 *
 * JobRunner implementations are always instantiated via their no-arg constructor.
 */
public interface SyncJobRunner extends JobRunner
{
    /**
     * Redirect to the output. If this method returns a URL, the client
     * is redirected to the URL (HTTP "See Other", 303). Otherwise, the setOutput
     * method is called and the job runner is responsible for setting headers and
     * writing content directly (streaming output).
     * </p><p>
     * This mechanism can be used when the real output is produced by some other
     * dynamic resource or available in a static resource, possibly in another
     * application or even on a different server. If the implementation returns a URL here,
     * it must also update the job phase to one of the final states (COMPLETED or ERROR).
     *
     * @return a URL to the actual results, or null to try streaming output
     */
    URL getRedirectURL();

    /**
     * Streaming output. If the getRedirectURL() method returns null, this method is
     * called to do streaming output from the job; it is always called before the run()
     * method so that a single JobRunner can be implemented to work in both synchronous and
     * asynchronous mode.
     *
     * @param out the output destination
     */
    void setOutput(SyncOutput out);

    /**
     * Streaming output. This method is called right after setOutput; this is
     * where the implementation code for the job is executed. The implementation is responsible
     * for (i) setting requried header values before opening the OutputStream
     * and (ii) updating the job phase to one of the final states (COMPLETED or ERROR).
     */
    void run();
}
