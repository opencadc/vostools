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

package ca.nrc.cadc.vosi;

import java.util.List;

import org.jdom.Attribute;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.Namespace;

/**
 * @author zhangsa
 *
 */
public class Capabilities
{
    // xsi schema location
    public static final String XSI_LOC = "http://www.ivoa.net/xml/VOSI/v1.0 http://www.ivoa.net/xml/VOSI/v1.0 "
            + "http://www.ivoa.net/xml/VODataService/v1.0 http://www.ivoa.net/xml/VODataService/v1.0";

    private List<Capability> _caps;

    /**
     * @param host
     * @param context
     * @param standardID
     * @param resourceName
     * @param role
     */
    public Capabilities(List<Capability> caps)
    {
        _caps = caps;
    }
    
    public Document toXmlDocument()
    {
        Namespace vosi = Namespace.getNamespace("vosi", VOSI.VOSI_NS_URI);
        Namespace xsi = Namespace.getNamespace("xsi", VOSI.XSI_NS_URI);
        Namespace vs = Namespace.getNamespace("vs", VOSI.VS_NS_URI);
        Namespace vr = Namespace.getNamespace("vr", VOSI.VR_NS_URI);
        Element eleCapabilities = new Element("capabilities", vosi);
        eleCapabilities.addNamespaceDeclaration(xsi);
        eleCapabilities.addNamespaceDeclaration(vs);
        eleCapabilities.addNamespaceDeclaration(vr);

        //Attribute attSchemaLocation = new Attribute("schemaLocation", XSI_LOC, xsi);
        //eleCapabilities.setAttribute(attSchemaLocation);

        Document document = new Document();
        document.addContent(eleCapabilities);

        for (Capability capability : this._caps)
        {
            eleCapabilities.addContent(capability.toXmlElement());
        }
        return document;
    }

    public List<Capability> getCaps()
    {
        return _caps;
    }

    public void setCaps(List<Capability> caps)
    {
        _caps = caps;
    }
}
