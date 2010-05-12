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
import java.io.OutputStream;
import java.io.Writer;

import org.apache.log4j.Logger;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.output.Format;
import org.jdom.output.XMLOutputter;

import ca.nrc.cadc.uws.UWS;

/**
 * Writes a Transfer as XML to an output.
 * 
 * @author Sailor Zhang
 */
public class TransferWriter
{
    @SuppressWarnings("unused")
    private static Logger log = Logger.getLogger(TransferWriter.class);

    private Transfer transfer;
    private Document document;
    private XMLOutputter outputter;

    public TransferWriter(Transfer transfer)
    {
        this.transfer = transfer;
        buildDocument();
        this.outputter = new XMLOutputter();
        this.outputter.setFormat(Format.getPrettyFormat());
    }

    /**
     * Write the transfer to a String.
     */
    public String toString() {
        return this.outputter.outputString(this.document);
    }

    /**
     * Write the transfer to an OutputStream.
     *
     * @param out OutputStream to write to.
     * @throws IOException if the writer fails to write.
     */
    public void writeTo(OutputStream out) throws IOException {
        this.outputter.output(this.document, out);
    }

    /**
     * Write the transfer to a writer
     *
     * @param writer Writer to write to.
     * @throws IOException if the writer fails to write.
     */
    public void writeTo(Writer writer) throws IOException {
        this.outputter.output(this.document, writer);
    }

    /**
     * Build XML Document for the transfer
     */
    private void buildDocument() {
        Element root = new Element("transfer", VOS.NS);
        root.addNamespaceDeclaration(VOS.NS);
        root.addNamespaceDeclaration(UWS.XLINK_NS);
        root.setAttribute("schemaLocation", VOS.EXT_SCHEMA_LOCATION, UWS.XSI_NS);

        Element e = null;

        e = new Element("target", VOS.NS);
        e.addContent(this.transfer.getTarget().getUri().toString());
        root.addContent(e);

        e = new Element("direction", VOS.NS);
        e.addContent(this.transfer.getDirection().toString());
        root.addContent(e);

        e = new Element("view", VOS.NS);
        e.addContent(this.transfer.getView().getUri());
        root.addContent(e);

        e = createProtocols();
        root.addContent(e);

        this.document = new Document();
        this.document.addContent(root);
    }

    private Element createProtocols() {
        Element rtn = new Element("protocols", VOS.NS);
        Element e = null;
        Element e2 = null;
        for (Protocol protocol : this.transfer.getProtocols())
        {
            e = new Element("protocol", VOS.NS);
            e.setAttribute("uri", protocol.getUri());
            e2 = new Element("endpoint", VOS.NS);
            e2.addContent(protocol.getEndpoint());
            e.addContent(e2);
            rtn.addContent(e);
        }
        return rtn;
    }
}
