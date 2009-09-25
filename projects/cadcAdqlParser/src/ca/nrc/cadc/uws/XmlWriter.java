/*
************************************************************************
*******************  CANADIAN ASTRONOMY DATA CENTRE  *******************
**************  CENTRE CANADIEN DE DONNÃES ASTRONOMIQUES  **************
*
*  (c) 2009.                            (c) 2009.
*  Government of Canada                 Gouvernement du Canada
*  National Research Council            Conseil national de recherches
*  Ottawa, Canada, K1A 0R6              Ottawa, Canada, K1A 0R6
*  All rights reserved                  Tous droits rÃ©servÃ©s
*                                       
*  NRC disclaims any warranties,        Le CNRC dÃ©nie toute garantie
*  expressed, implied, or               Ã©noncÃ©e, implicite ou lÃ©gale,
*  statutory, of any kind with          de quelque nature que ce
*  respect to the software,             soit, concernant le logiciel,
*  including without limitation         y compris sans restriction
*  any warranty of merchantability      toute garantie de valeur
*  or fitness for a particular          marchande ou de pertinence
*  purpose. NRC shall not be            pour un usage particulier.
*  liable in any event for any          Le CNRC ne pourra en aucun cas
*  damages, whether direct or           Ãªtre tenu responsable de tout
*  indirect, special or general,        dommage, direct ou indirect,
*  consequential or incidental,         particulier ou gÃ©nÃ©ral,
*  arising from the use of the          accessoire ou fortuit, rÃ©sultant
*  software.  Neither the name          de l'utilisation du logiciel. Ni
*  of the National Research             le nom du Conseil National de
*  Council of Canada nor the            Recherches du Canada ni les noms
*  names of its contributors may        de ses  participants ne peuvent
*  be used to endorse or promote        Ãªtre utilisÃ©s pour approuver ou
*  products derived from this           promouvoir les produits dÃ©rivÃ©s
*  software without specific prior      de ce logiciel sans autorisation
*  written permission.                  prÃ©alable et particuliÃ¨re
*                                       par Ã©crit.
*                                       
*  This file is part of the             Ce fichier fait partie du projet
*  OpenCADC project.                    OpenCADC.
*                                       
*  OpenCADC is free software:           OpenCADC est un logiciel libre ;
*  you can redistribute it and/or       vous pouvez le redistribuer ou le
*  modify it under the terms of         modifier suivant les termes de
*  the GNU Affero General Public        la âGNU Affero General Public
*  License as published by the          Licenseâ telle que publiÃ©e
*  Free Software Foundation,            par la Free Software Foundation
*  either version 3 of the              : soit la version 3 de cette
*  License, or (at your option)         licence, soit (Ã  votre grÃ©)
*  any later version.                   toute version ultÃ©rieure.
*                                       
*  OpenCADC is distributed in the       OpenCADC est distribuÃ©
*  hope that it will be useful,         dans lâespoir quâil vous
*  but WITHOUT ANY WARRANTY;            sera utile, mais SANS AUCUNE
*  without even the implied             GARANTIE : sans mÃªme la garantie
*  warranty of MERCHANTABILITY          implicite de COMMERCIALISABILITÃ
*  or FITNESS FOR A PARTICULAR          ni dâADÃQUATION Ã UN OBJECTIF
*  PURPOSE.  See the GNU Affero         PARTICULIER. Consultez la Licence
*  General Public License for           GÃ©nÃ©rale Publique GNU Affero
*  more details.                        pour plus de dÃ©tails.
*                                       
*  You should have received             Vous devriez avoir reÃ§u une
*  a copy of the GNU Affero             copie de la Licence GÃ©nÃ©rale
*  General Public License along         Publique GNU Affero avec
*  with OpenCADC.  If not, see          OpenCADC ; si ce nâest
*  <http://www.gnu.org/licenses/>.      pas le cas, consultez :
*                                       <http://www.gnu.org/licenses/>.
*
*  $Revision: 4 $
*
************************************************************************
*/
/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package ca.nrc.cadc.uws;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import javax.servlet.http.HttpServletResponse;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.Namespace;
import org.jdom.output.Format;
import org.jdom.output.XMLOutputter;

/**
 * Write a job or job resource in XML format.
 * 
 * @author pdowler
 */
public class XmlWriter 
{
    public static Namespace UWS_NAMESPACE = Namespace.getNamespace("uws", "http://www.ivoa.net/xml/UWS/v0.9");
    
    private HttpServletResponse response;
    private String resourcePrefix; // namespace prefix for custom resources
    private String resourceURI;    // namespace uri for custom resources
    
    public XmlWriter(HttpServletResponse response)
    {
        this.response = response;
    }
    
    public void setResourceNamespace(String prefix, String uri)
    {
        this.resourcePrefix = prefix;
        this.resourceURI = uri;
    }
    
    // document == job
    public void doXmlResponse(UwsJob job)
        throws IOException
    {
        Element root = new Element("job", UWS_NAMESPACE);
        Element id = new Element("jobId", UWS_NAMESPACE);
        id.addContent(job.getID());
        root.addContent(id);
        root.addContent( toElement(UwsJob.QUOTE, job, UWS_NAMESPACE) );
        root.addContent( toElement(UwsJob.TERMINATION, job, UWS_NAMESPACE) );
        root.addContent( toElement(UwsJob.DESTRUCTION, job, UWS_NAMESPACE) );
        root.addContent( toElement(UwsJob.PHASE, job, UWS_NAMESPACE) );
        root.addContent( toElement(UwsJob.START_TIME, job, UWS_NAMESPACE) );
        root.addContent( toElement(UwsJob.END_TIME, job, UWS_NAMESPACE) );
        root.addContent( toElement(UwsJob.RESULTS, job, UWS_NAMESPACE) );
        root.addContent( toElement(UwsJob.ERROR, job, UWS_NAMESPACE) );

        // namespace for custom content
        Namespace ns = Namespace.NO_NAMESPACE;
        if (resourcePrefix != null && resourceURI != null)
            ns = Namespace.getNamespace(resourcePrefix, resourceURI);
        // handle custom resources
        Iterator i = job.getResources().iterator();
        while ( i.hasNext() )
        {
            UwsResource ur = (UwsResource) i.next();
            root.addContent( toElement(ur, job, ns) );
        }
        // TODO: handle custom params
        
        doXmlResponse(root);
    }
    
    // document == resource
    public void doXmlResponse(String uwsResource, UwsJob job)
        throws IOException
    {
        Element root = toElement(uwsResource, job, null); // do not know namespace
        doXmlResponse(root);
        
    }
    
    // create document and write to response
    private void doXmlResponse(Element root)
        throws IOException
    {
        Document doc = new Document(root);
        response.setContentType("application/xml;charset=UTF-8");
        response.setStatus(HttpServletResponse.SC_OK);
        XMLOutputter xout = new XMLOutputter(Format.getPrettyFormat());
        OutputStream ostream = response.getOutputStream();
        xout.output(doc, ostream);
        ostream.close();
    }
    
    // handle standard UWS resources by name
    private Element toElement(String name, UwsJob job, Namespace ns)
    {
        String tag = "";
        String content = null;
        List children = null;
        
        if (name == UwsJob.PHASE)
        {
            tag = "phase";
            content = job.getPhase();
        }
        else if (name == UwsJob.QUOTE)
        {
            tag = "quote";
            content = UwsJob.valueToString(job.getQuote());
        }
        else if (name == UwsJob.TERMINATION)
        {
            tag = "termination";
            content = job.getTermination().toString();
        }
        else if (name == UwsJob.DESTRUCTION)
        {
            tag = "destruction";
            content = UwsJob.valueToString(job.getDestruction());
        }
        else if (name == UwsJob.START_TIME)
        {
            tag = "startTime";
            content = UwsJob.valueToString(job.getStartTime());
        }
        else if (name == UwsJob.END_TIME)
        {
            tag = "endTime";
            content = UwsJob.valueToString(job.getEndTime());
        }
        else if (name == UwsJob.ERROR)
        {
            tag = "error";
            if (job.getError() != null)
                content = job.getError().message;
            // TODO: render error?
        }
        else if (name == UwsJob.RESULTS)
        {
            tag = "resultList";
            List results = job.getResults();
            if (results != null && results.size() > 0)
            {
                children = new ArrayList();
                for (int i=0; i<results.size(); i++)
                {
                    UwsResult ur = (UwsResult) results.get(i);
                    Element res = new Element("result", UWS_NAMESPACE);
                    // TODO: what is supposed to go in here?
                    res.addContent(ur.name);
                    children.add(res);
                }
            }
        }
        // non-spec compliant to expose the params this way
        /*
        else if (uwsResource == UwsJob.PARAMS)
        {
            tag = "paramList";
            List params = job.getParams();
            if (params != null && params.size() > 0)
            {
                children = new ArrayList();
                for (int i=0; i<params.size(); i++)
                {
                    UwsParam p = (UwsParam) params.get(i);
                    Element param = new Element(p.key, Namespace.NO_NAMESPACE);
                    for (int j=0; j<p.value.size(); j++)
                    {
                        Object v = p.value.get(j);
                        Element e = new Element("value", Namespace.NO_NAMESPACE);
                        e.addContent(UwsJob.valueToString(v));
                        param.addContent(e);
                    }
                    children.add(param);
                }
            }
        }
        */
        
        if (ns == null)
            ns = UWS_NAMESPACE;
        
        Element e = new Element(tag, ns);
        if (content != null)
            e.addContent(content);
        if (children != null && children.size() > 0)
            e.addContent(children);
        
        return e;
    }
    
    // custom resource handling
    private Element toElement(UwsResource ur, UwsJob job, Namespace ns)
    {
        String tag = ur.name;
        String content = ur.value.toString();
        if (ns == null)
            ns = UWS_NAMESPACE;
        Element e = new Element(tag, ns);
        if (content != null)
            e.addContent(content);
        return e;
    }
}
