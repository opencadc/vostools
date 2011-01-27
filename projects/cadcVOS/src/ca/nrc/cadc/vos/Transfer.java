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

package ca.nrc.cadc.vos;

import java.io.IOException;
import java.io.StringWriter;
import java.util.List;

import org.apache.log4j.Logger;

/**
 * @author zhangsa
 *
 */
public class Transfer implements Runnable
{
    private static Logger log = Logger.getLogger(Transfer.class);
    /**
     * Transfer Directions
     */
    public enum Direction {
        pushToVoSpace, pullToVoSpace, pushFromVoSpace, pullFromVoSpace;
    }

    protected Direction direction;

    // Reqeust member variables
    protected String serviceUrl;
    protected Node target;
    protected View view;
    protected List<Protocol> protocols;
    protected boolean keepBytes;

    public Transfer()
    {
    }

    public String getUploadEndpoint() 
    {
        return getEndpoint(VOS.PROTOCOL_HTTPS_PUT);
    }

    public String getDownloadEndpoint() 
    {
        String rtn = null;
        rtn = getEndpoint(VOS.PROTOCOL_HTTPS_GET);
        return rtn;
    }

    public String getEndpoint(String strProtocol) 
    {
        String rtn = null;
        if (this.protocols != null)
        {
            for (Protocol p : this.protocols)
            {
                if (p.getUri().equalsIgnoreCase(strProtocol)) {
                    rtn = p.getEndpoint();
                    break;
                }
            }
        }
        return rtn;
    }

    public String getPhase()
    {
        throw new UnsupportedOperationException("Feature under construction.");
    }

    public String getResults()
    {
        //TODO
        return null;
        //throw new UnsupportedOperationException("Feature under construction.");
    }

    public String getErrors()
    {
        throw new UnsupportedOperationException("Feature under construction.");
    }

    public void run()
    {
        throw new UnsupportedOperationException("Feature under construction.");
    }

    public String toXmlString()
    {
        String rtn = null;
        try
        {
            TransferWriter writer = new TransferWriter(this);
            StringWriter sw = new StringWriter();
            writer.write(this, sw);
            rtn = sw.toString();
            sw.close();
        } catch (IOException e)
        {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
        return rtn;
    }

    public Direction getDirection()
    {
        return direction;
    }

    public void setDirection(Direction direction)
    {
        this.direction = direction;
    }

    public String getServiceUrl()
    {
        return serviceUrl;
    }

    public void setServiceUrl(String serviceUrl)
    {
        this.serviceUrl = serviceUrl;
    }

    public Node getTarget()
    {
        return target;
    }

    public void setTarget(Node target)
    {
        this.target = target;
    }

    public View getView()
    {
        return view;
    }

    public void setView(View view)
    {
        this.view = view;
    }

    public List<Protocol> getProtocols()
    {
        return protocols;
    }

    public void setProtocols(List<Protocol> protocols)
    {
        this.protocols = protocols;
    }

    public boolean isKeepBytes()
    {
        return keepBytes;
    }

    public void setKeepBytes(boolean keepBytes)
    {
        this.keepBytes = keepBytes;
    }

    @Override
    public String toString()
    {
        return "Transfer [direction=" + direction + ", keepBytes=" + keepBytes + ", protocols=" + protocols + ", serviceUrl="
                + serviceUrl + ", target=" + target + ", view=" + view + "]";
    }

}
