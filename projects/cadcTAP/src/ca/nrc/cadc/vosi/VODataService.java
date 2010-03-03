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

import ca.nrc.cadc.tap.schema.ColumnDesc;
import ca.nrc.cadc.tap.schema.SchemaDesc;
import ca.nrc.cadc.tap.schema.TableDesc;
import ca.nrc.cadc.tap.schema.TapSchema;
import org.apache.log4j.Logger;
import org.jdom.Attribute;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.Namespace;

/**
 *
 * @author pdowler
 */
public class VODataService 
{
    private static Logger log = Logger.getLogger(VODataService.class);
    // Uri to the XML schema.
    public static final String XSI_NS_URI = "http://www.w3.org/2001/XMLSchema-instance";

    // Uri to the VODataService schema.
    public static final String VOTABLE_NS_URI = "http://www.ivoa.net/xml/VODataService/v1.0";

    private TapSchema _tapSchema;

    public VODataService(TapSchema tapSchema)
    {
        this._tapSchema = tapSchema;
    }

    /**
     * @return the TapSchema as a document to be rendered as XML
     */
    public Document getDocument()
    {
        Namespace vs = Namespace.getNamespace("vs", VOTABLE_NS_URI);
        Namespace xsi = Namespace.getNamespace("xsi", XSI_NS_URI);
        Element eleTableset = toXmlElement(_tapSchema);
        eleTableset.addNamespaceDeclaration(xsi);
        eleTableset.addNamespaceDeclaration(vs);

        Document document = new Document();
        document.addContent(eleTableset);
        return document;
    }

    /**
     * @param tapSchema
     * @return
     */
    private Element toXmlElement(TapSchema tapSchema)
    {
        Element eleTableset = new Element("tableset");
        for (SchemaDesc sd : tapSchema.getSchemaDescs())
        {
            eleTableset.addContent(toXmlElement(sd));
        }
        return eleTableset;
    }

    /**
     * @param sd
     * @return
     */
    private Element toXmlElement(SchemaDesc sd)
    {
        Element eleSchema = new Element("schema");
        Element ele;
        ele = new Element("name");
        ele.setText(sd.getSchemaName());
        eleSchema.addContent(ele);
        for (TableDesc td : sd.getTableDescs())
        {
            eleSchema.addContent(toXmlElement(td));
        }
        return eleSchema;
    }

    /**
     * @param td
     * @return
     */
    private Element toXmlElement(TableDesc td)
    {
        Element eleTable = new Element("table");
        eleTable.setAttribute("role", "out");

        Element ele;
        ele = new Element("name");
        ele.setText(td.getTableName());
        eleTable.addContent(ele);
        
        for (ColumnDesc cd : td.getColumnDescs())
        {
            eleTable.addContent(toXmlElement(cd));
        }

        return eleTable;
    }

    /**
     * @param cd
     * @return
     */
    private Element toXmlElement(ColumnDesc cd)
    {
        Element eleColumn = new Element("column");
        
        addChild(eleColumn, "name", cd.getColumnName());
        addChild(eleColumn, "description", cd.getDescription());
        addChild(eleColumn, "ucd", cd.getUcd());
        addChild(eleColumn, "utype", cd.getUtype());
        addChild(eleColumn, "unit", cd.getUnit());
        
        Element eleDt = addChild(eleColumn, "dataType", cd.getDatatype());
        if (eleDt != null)
        {
            Namespace xsi = Namespace.getNamespace("xsi", VODataService.XSI_NS_URI);
            Attribute attType = new Attribute("type", "vs:TAP", xsi);
            eleDt.setAttribute(attType);
            
            if (cd.getSize() != null && cd.getSize() > 0)
                eleDt.setAttribute("size", cd.getSize().toString());
        }

        return eleColumn;
    }
    private Element addChild(Element eleColumn, String chdName, String chdText)
    {
        Element ele = null;
        if (chdText != null && !chdText.equals(""))
        {
            ele = new Element(chdName);
            ele.setText(chdText);
            eleColumn.addContent(ele);
        }
        return ele;
    }
    
}
