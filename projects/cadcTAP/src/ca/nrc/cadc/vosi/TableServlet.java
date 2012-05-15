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

import ca.nrc.cadc.auth.AuthenticationUtil;
import java.io.IOException;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.sql.DataSource;

import org.apache.log4j.Logger;
import org.jdom.Document;
import org.jdom.output.Format;
import org.jdom.output.XMLOutputter;

import ca.nrc.cadc.tap.schema.TapSchema;
import ca.nrc.cadc.tap.schema.TapSchemaDAO;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import javax.naming.NamingException;
import javax.security.auth.Subject;

/**
 * Simple servlet that reads metadata using <code>ca.nrc.cadc.tap.schema</code>
 * and writes it in XML.
 * 
 * @author pdowler, Sailor Zhang
 */
public class TableServlet extends HttpServlet
{
    private static Logger log = Logger.getLogger(TableServlet.class);
    private static final long serialVersionUID = 201003131300L;

    private static String queryDataSourceName = "jdbc/tapuser";

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
        throws ServletException, IOException
    {
        Subject subject = AuthenticationUtil.getSubject(request);
        GetTablesAction action = new GetTablesAction();
        action.response = response;
        try
        {
            Subject.doAs(subject, action);
        }
        catch(PrivilegedActionException pex)
        {
            Exception ex = pex.getException();
            if (ex instanceof IOException)
                throw (IOException) ex;
        }
    }

    private class GetTablesAction implements PrivilegedExceptionAction<Object>
    {
        HttpServletResponse response;

        public Object run() throws Exception
        {
            boolean started = false;
            try
            {
                // find DataSource via JNDI lookup
                Context initContext = new InitialContext();
                Context envContext = (Context) initContext.lookup("java:/comp/env");
                DataSource queryDataSource = (DataSource) envContext.lookup(queryDataSourceName);

                // extract TapSchema
                TapSchemaDAO dao = new TapSchemaDAO(queryDataSource, true);
                TapSchema tapSchema = dao.get();

                TableSet vods = new TableSet(tapSchema);
                Document doc = vods.getDocument();
                XMLOutputter out = new XMLOutputter(Format.getPrettyFormat());
                started = true;
                response.setContentType("text/xml");
                out.output(doc, response.getOutputStream());
            }
            catch(IOException ex)
            {
                throw ex;
            }
            catch(NamingException ex)
            {
                log.error("CONFIGURATION ERROR: failed to find JNDI DataSource "+queryDataSourceName);
                if (!started) response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                        "service unavailable (configuration error)");
            }
            catch (Throwable t)
            {
                log.error("BUG", t);
                if (!started) response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                        t.getMessage());
            }
            return null;
        }
    }
}
