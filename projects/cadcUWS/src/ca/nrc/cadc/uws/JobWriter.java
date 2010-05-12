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

import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;
import java.security.Principal;
import java.util.Date;
import java.util.Set;

import javax.security.auth.Subject;

import org.apache.log4j.Logger;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.output.Format;
import org.jdom.output.XMLOutputter;

import ca.nrc.cadc.date.DateUtil;

/**
 * Writes a Job as XML to an output.
 * 
 * @author Sailor Zhang
 */
public class JobWriter
{
    @SuppressWarnings("unused")
    private static Logger log = Logger.getLogger(JobWriter.class);

    private Job job;
    private Document document;
    private XMLOutputter outputter;

    public JobWriter(Job job)
    {
        this.job = job;
        buildDocument();
        this.outputter = new XMLOutputter();
        this.outputter.setFormat(Format.getPrettyFormat());
    }

    /**
     * Write the job to a String.
     */
    public String toString() {
        return this.outputter.outputString(this.document);
    }

    /**
     * Write the job to an OutputStream.
     *
     * @param out OutputStream to write to.
     * @throws IOException if the writer fails to write.
     */
    public void writeTo(OutputStream out) throws IOException {
        this.outputter.output(this.document, out);
    }

    /**
     * Write the job to a writer
     *
     * @param writer Writer to write to.
     * @throws IOException if the writer fails to write.
     */
    public void writeTo(Writer writer) throws IOException {
        this.outputter.output(this.document, writer);
    }

    /**
     * Build XML Document for the job
     */
    private void buildDocument() {
        Element root = new Element("job", UWS.NS);
        root.addNamespaceDeclaration(UWS.NS);
        root.addNamespaceDeclaration(UWS.XLINK_NS);
        root.setAttribute("schemaLocation", "http://www.ivoa.net/xml/UWS/v1.0 UWS.xsd", UWS.XSI_NS);

        Element e = null;

        e = new Element("jobId", UWS.NS);
        e.addContent(this.job.getID());
        root.addContent(e);

        e = new Element("runId", UWS.NS);
        e.addContent(this.job.getRunID());
        root.addContent(e);

        e = createOwnerId();
        root.addContent(e);

        e = new Element("phase", UWS.NS);
        e.addContent(this.job.getExecutionPhase().toString());
        root.addContent(e);

        e = new Element("quote", UWS.NS);
        //e.setAttribute("nil", "true", XSI_NS);
        e.addContent(dateToString(this.job.getQuote()));
        root.addContent(e);

        e = new Element("startTime", UWS.NS);
        //e.setAttribute("nil", "true", XSI_NS);
        e.addContent(dateToString(this.job.getStartTime()));
        root.addContent(e);

        e = new Element("endTime", UWS.NS);
        //e.setAttribute("nil", "true", XSI_NS);
        e.addContent(dateToString(this.job.getEndTime()));
        root.addContent(e);

        e = new Element("executionDuration", UWS.NS);
        e.addContent(Long.toString(this.job.getExecutionDuration()));
        root.addContent(e);

        e = new Element("destruction", UWS.NS);
        //e.setAttribute("nil", "true", XSI_NS);
        e.addContent(dateToString(this.job.getDestructionTime()));
        root.addContent(e);

        e = createParameters();
        root.addContent(e);

        e = createResults();
        root.addContent(e);

        e = createErrorSummary();
        if (e != null)
            root.addContent(e);

        this.document = new Document();
        this.document.addContent(root);
    }

    private Element createOwnerId() {
        Element e = null;
        e = new Element("ownerId", UWS.NS);
        e.setAttribute("nil", "true", UWS.XSI_NS);
        Subject subjectOwner = this.job.getOwner();
        if (subjectOwner != null)
        {
            Set<Principal> setPrincipal = subjectOwner.getPrincipals();
            for (Principal prc : setPrincipal)
            {
                e.addContent(prc.getName());
                break; // a convenient way to get the first principal in the set ONLY.
            }
        }
        return e;
    }

    private static String dateToString(Date date) {
        String rtn = null;
        rtn = DateUtil.toString(date, DateUtil.IVOA_DATE_FORMAT, DateUtil.UTC);
        return rtn;
    }

    private Element createParameters() {
        Element rtn = new Element("parameters", UWS.NS);
        Element e = null;
        for (Parameter par : this.job.getParameterList())
        {
            e = new Element("parameter", UWS.NS);
            e.setAttribute("id", par.getName());
            e.addContent(par.getValue());
            rtn.addContent(e);
        }
        return rtn;
    }

    private Element createResults() {
        Element rtn = new Element("results", UWS.NS);
        Element e = null;
        for (Result rs : this.job.getResultsList())
        {
            e = new Element("result", UWS.NS);
            e.setAttribute("id", rs.getName());
            e.setAttribute("href", rs.getURL().toString(), UWS.XLINK_NS);
            rtn.addContent(e);
        }
        return rtn;
    }

    private Element createErrorSummary() {
        Element rtn = null;
        ErrorSummary es = this.job.getErrorSummary();
        if (es != null)
        {
            rtn = new Element("errorSummary", UWS.NS);
            rtn.setAttribute("type", es.getErrorType().toString());

            Element e = null;

            e = new Element("message", UWS.NS);
            e.addContent(this.job.getErrorSummary().getSummaryMessage());
            rtn.addContent(e);

            e = new Element("detail", UWS.NS);
            e.setAttribute("href", this.job.getErrorSummary().getDocumentURL().toString(), UWS.XLINK_NS);
            rtn.addContent(e);
        }

        return rtn;
    }

}
