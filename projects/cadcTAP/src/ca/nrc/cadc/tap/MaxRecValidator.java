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

package ca.nrc.cadc.tap;

import ca.nrc.cadc.tap.schema.TableDesc;
import ca.nrc.cadc.tap.schema.TapSchema;
import ca.nrc.cadc.uws.Job;
import ca.nrc.cadc.uws.Parameter;
import ca.nrc.cadc.uws.ParameterUtil;
import java.util.List;
import java.util.Map;
import org.apache.log4j.Logger;

/**
 * This class checks for a TAP parameter named MAXREC, and returns a validated
 * or default value. Implementors can/should subclass this with a class named
 * <code>ca.nrc.cadc.tap.impl.MaxRecValidatorImpl</code> to set the default and
 * maximum allowed values for their service. The values set here are null, which
 * means no limit.
 * 
 * @author jburke
 */
public class MaxRecValidator
{
    private static Logger log = Logger.getLogger(MaxRecValidator.class);

    /**
     * The default value when MAXREC is not specified. May be null for unlimited.
     */
    protected Integer defaultValue;

    /**
     * The maximum allowed value. May be null for unlimited.
     */
    protected Integer maxValue;

    /**
     * The UWS Job. This may be used by subclasses to dynamically determine
     * the limit in the validate method.
     */
    protected Job job;

    /**
     * This gets set to true if the job is running in synchronous mode. In
     * sync mode, the QueryRunner streams the output and thus consumes a finite
     * amount of memory and no storage space.
     */
    protected boolean sync;

    protected TapSchema tapSchema;
    
    protected Map<String, TableDesc> extraTables;

    public MaxRecValidator() { }

    public void setJob(Job job) { this.job = job; }

    public void setSynchronousMode(boolean sync) { this.sync = sync; }

    public void setTapSchema(TapSchema tapSchema)
    {
        this.tapSchema = tapSchema;
    }

    public void setExtraTables(Map<String, TableDesc> extraTables)
    {
        this.extraTables = extraTables;
    }

    /**
     * Checks the parameter List for a parameter named MAXREC.
     * <p>
     * If the MAXREC parameter is found, attempts to parse and return the value
     * of MAXREC as an int. If the parsing fails, or if the value of MAXREC is
     * negative, an IllegalArgumentException is thrown.
     * <p>
     * If the MAXREC parameter is not found in the List of parameters,
     * the default value of {@link Integer.MAX_VALUE} is returned.
     * <p>
     *
     *
     * @param paramList List of TAP parameters.
     * @return int value of MAXREC.
     */
    public Integer validate(List<Parameter> paramList)
    {
        String value = ParameterUtil.findParameterValue("MAXREC", paramList);

        if (value == null || value.trim().length() == 0)
            return defaultValue;

        try
        {
            Integer ret = new Integer(value);
            if (ret < 0)
                throw new IllegalArgumentException("Invalid MAXREC: " + value);
            if (maxValue != null && maxValue < ret)
                return maxValue;
            return ret;
        }
        catch (NumberFormatException nfe)
        {
            throw new IllegalArgumentException("Invalid MAXREC: " + value);
        }
    }
}
