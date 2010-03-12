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

import java.util.Date;

import org.jdom.Attribute;
import org.jdom.Comment;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.Namespace;

import ca.nrc.cadc.date.DateUtil;
import ca.nrc.cadc.tap.schema.ColumnDesc;
import ca.nrc.cadc.tap.schema.SchemaDesc;
import ca.nrc.cadc.tap.schema.TableDesc;
import ca.nrc.cadc.tap.schema.TapSchema;

/**
 *
 * @author pdowler
 */
public class VODataService
{
//    private static Logger log = Logger.getLogger(VODataService.class);

    public static final String XSI_NS_URI = "http://www.w3.org/2001/XMLSchema-instance";
    public static final String VS_NS_URI = "http://www.ivoa.net/xml/VODataService/v1.1";
    public static final String RI_NS_URI = "http://www.ivoa.net/xml/RegistryInterface/v1.0";
    public static final String VR_NS_URI = "http://www.ivoa.net/xml/VOResource/v1.0";

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
        Namespace vs = Namespace.getNamespace("vs", VS_NS_URI);
        Namespace xsi = Namespace.getNamespace("xsi", XSI_NS_URI);
//        Namespace ri = Namespace.getNamespace("ri", RI_NS_URI);
        Namespace vr = Namespace.getNamespace("vr", VR_NS_URI);

//        Element eleResource = new Element("Resource", ri);

//        eleResource.setAttribute("status", "active");
//
//        Date dateUpdated = new Date();
//        Date dateCreated = this._dateCreated;
//        eleResource.setAttribute("updated", DateUtil.toString(dateUpdated, DateUtil.IVOA_DATE_FORMAT, DateUtil.UTC));
//        eleResource.setAttribute("created", DateUtil.toString(dateCreated, DateUtil.IVOA_DATE_FORMAT, DateUtil.UTC));
//
//        Attribute attType = new Attribute("type", "vs:CatalogService", xsi);
//        eleResource.setAttribute(attType);
//
//        eleResource.addNamespaceDeclaration(ri);
//        eleResource.addNamespaceDeclaration(vs);
//        eleResource.addNamespaceDeclaration(xsi);
//        eleResource.addNamespaceDeclaration(vr);
//
//        addChild(eleResource, "title", " ");
//        addChild(eleResource, "identifier", this._identifier);
//
//        Element eleContact = new Element("contact");
//        addChild(eleContact, "name", " ");
//
//        Element eleCuration = new Element("curation");
//        addChild(eleCuration, "publisher", " ");
//        eleCuration.addContent(eleContact);
//        eleResource.addContent(eleCuration);
//
//        Element eleContent = new Element("content");
//        addChild(eleContent, "subject", " ");
//        addChild(eleContent, "description", " ");
//        addChild(eleContent, "referenceURL", " ");
//        eleResource.addContent(eleContent);

        Element eleTableset = toXmlElement(_tapSchema);
        eleTableset.addNamespaceDeclaration(vs);
        eleTableset.addNamespaceDeclaration(xsi);
        eleTableset.addNamespaceDeclaration(vr);
        
        Attribute attType = new Attribute("type", "vs:TableSet", xsi);
        eleTableset.setAttribute(attType);
        

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
        Comment comment = new Comment("This is a temporary solution as of 2010-03-12.");
        eleTableset.addContent(comment);
        if (tapSchema.getSchemaDescs().size() ==0)
            throw new IllegalArgumentException("Error: at least one schema is required.");
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
        eleTable.setAttribute("type", "output");

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
        addChild(eleColumn, "unit", cd.getUnit());
        addChild(eleColumn, "ucd", cd.getUcd());
        addChild(eleColumn, "utype", cd.getUtype());

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

    private Element addChild(Element eleParent, String chdName, String chdText)
    {
        Element ele = null;
        if (chdText != null && !chdText.equals(""))
        {
            ele = new Element(chdName);
            ele.setText(chdText);
            eleParent.addContent(ele);
        }
        return ele;
    }

    public TapSchema getTapSchema()
    {
        return _tapSchema;
    }

    public void setTapSchema(TapSchema tapSchema)
    {
        _tapSchema = tapSchema;
    }
}
