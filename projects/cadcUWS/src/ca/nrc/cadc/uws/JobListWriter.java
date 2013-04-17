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
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.Iterator;

import org.apache.log4j.Logger;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.output.Format;
import org.jdom2.output.XMLOutputter;

/**
 * Writes a JobList as XML.
 * 
 * @author majorb
 */
public class JobListWriter
{
    private static Logger log = Logger.getLogger(JobListWriter.class);

    public JobListWriter() 
    {
    }
    
    /**
     * Write to root Element to a writer.
     *
     * @param root Root Element to write.
     * @param writer Writer to write to.
     * @throws IOException if the writer fails to write.
     */
    protected void writeDocument(Element root, Writer writer)
        throws IOException
    {
        XMLOutputter outputter = new XMLOutputter();
        outputter.setFormat(Format.getPrettyFormat());
        Document document = new Document(root);
        outputter.output(document, writer);
    }

    /**
     * Write the job list to an OutputStream.
     *
     * @param job
     * @param out OutputStream to write to.
     * @throws IOException if the writer fails to write.
     */
    public void write(Iterator<Job> jobs, OutputStream out)
        throws IOException
    {
        write(jobs, new OutputStreamWriter(out));
    }

    /**
     * Write the job list to our streaming xml writer.
     *
     * @param job
     * @param writer Writer to write to.
     * @throws IOException if the writer fails to write.
     */
    public void write(Iterator<Job> jobs, Writer writer)
        throws IOException
    {
        Element root = getRootElement(jobs);
        XMLOutputter outputter = new XMLOutputter();
        outputter.setFormat(Format.getPrettyFormat());
        Document document = new Document(root);
        outputter.output(document, writer);
    }
    
    public Element getRootElement(Iterator<Job> jobs)
    {
        Element root = new Element(JobAttribute.JOBS.getAttributeName(), UWS.NS);
        root.addNamespaceDeclaration(UWS.NS);
        root.addNamespaceDeclaration(UWS.XLINK_NS);
        
        Job next = null;
        Element nextElement = null;
        
        while (jobs.hasNext())
        {
            next = jobs.next();
            nextElement = getShortJobDescription(next);
            root.addContent(nextElement);
        }
        
        return root;
    }

    /**
     * Create the XML for a short job description.
     * @param job
     * @return
     */
    public Element getShortJobDescription(Job job)
    {
        Element shortJobDescription = new Element(JobAttribute.JOB_REF.getAttributeName(), UWS.NS);
        shortJobDescription.setAttribute("id", job.getID());
        shortJobDescription.addContent(getPhase(job));
        return shortJobDescription;
    }

    /**
     * Get an Element representing the Job phase.
     *
     * @return The Job phase Element.
     */
    private Element getPhase(Job job)
    {
        Element element = new Element(JobAttribute.EXECUTION_PHASE.getAttributeName(), UWS.NS);
        element.addContent(job.getExecutionPhase().toString());
        return element;
    }
    

}
    