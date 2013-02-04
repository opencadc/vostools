/*
************************************************************************
*******************  CANADIAN ASTRONOMY DATA CENTRE  *******************
**************  CENTRE CANADIEN DE DONNÉES ASTRONOMIQUES  **************
*
*  (c) 2011.                            (c) 2011.
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
*  $Revision: 5 $
*
************************************************************************
*/

package ca.nrc.cadc.profiler;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.Expose;
import org.apache.log4j.Logger;

/**
 * Simple log4j based profiler for gathering web service performance data from a live
 * system. To profile a particular component, simply create a profiler and then
 * call the checkpoint method with the name of the operation just completed. The name of
 * the calling class, operation, and delta-t since the last checkpoint will be logged.
 * </p><p>
 * Note: the intended use is to gradually refine profiling as needed by adding extra checkpoint
 * calls when needed. The log level of the profiler package can be changed dynamically
 * using the LogControlServlet in order to turn-on and turn-off logging of profile data.
 * 
 * @author pdowler
 */
public class Profiler 
{
    private static final Logger log = Logger.getLogger(Profiler.class);
    
    private Gson gson;
    private long nanos;
    long numOps = 0L;
    
    @Expose
    protected String caller;
    
    @Expose
    protected String op;
    
    @Expose
    protected long dt;
    
    public Profiler(Class caller)
    {
        this.caller = caller.getSimpleName();
        this.nanos = System.nanoTime();
    }
    
    private void lazyInit()
    {
        if (gson == null)
        {
            // init json output only when info-level logging is already on, 
            // therefore only profile complete sequence of calls with a profiler
            GsonBuilder builder = new GsonBuilder().excludeFieldsWithoutExposeAnnotation();
            builder.disableHtmlEscaping();
            this.gson = builder.create();
        }
    }
    
    /**
     * Gather profiling information about a call to the specified operation by
     * the calling class.
     * 
     * @param op the name of the operation
     */
    public void checkpoint(String op)
    {
        numOps++;
        
        long nt = System.nanoTime();
        this.op = op;
        
        this.dt = (nt - nanos)/1000000L;
        
        if (log.isInfoEnabled())
        {
            lazyInit();
            log.info(gson.toJson(this, this.getClass()));
        }

        this.dt = 0;
        this.op = null;
        this.nanos = nt;
    }
}
