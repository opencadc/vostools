<!--
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
-->


<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN"
        "http://www.w3.org/TR/html4/loose.dtd">

<%@ taglib uri="WEB-INF/c.tld" prefix="c"%>

<%@ page import="ca.nrc.cadc.dlm.DownloadUtil" %>
<%@ page import="ca.nrc.cadc.dlm.server.ServerUtil" %>
<%
    String uris = (String) request.getAttribute("uris");
    String fragment = (String) request.getAttribute("fragment");
%>

<%
String skin = (String) request.getParameter("skin");
String skinParam = skin;
System.out.println("download.jsp: skin = " + skin);
if (skin == null || skin.trim().length() == 0)
    skin = "http://localhost/cadc/skin/";
if (!skin.endsWith("/"))
    skin += "/";
if (!skin.startsWith("http://"))
{
    if (!skin.startsWith("/"))
        skin = "/" + skin;
    skin = "http://localhost" + skin;
}
System.out.println("download.jsp: skin = " + skin);
String htmlHead = skin + "htmlHead";
String bodyHeader = skin + "bodyHeader";
String bodyFooter = skin + "bodyFooter";
%>

<html>
<head>
    <c:catch><c:import url="<%= htmlHead %>" /></c:catch>
</head>

<body>
    <c:catch><c:import url="<%= bodyHeader %>" /></c:catch>
    <h2>
        Chose a download method:
    </h2>
    
    <form action="/downloadManager/download" method="POST">
        
        <input type="hidden" name="uris" value="<%= uris %>" />
        <input type="hidden" name="fragment" value="<%= fragment %>" />
        <input type="hidden" name="skin" value="<%= skin %>" /> 
        
        <div style="padding-left: 2em; padding-right: 2em">
        <table width="66%">
            <tbody>
                <tr>
                    <td valign="top"><input type="submit" name="method" value="<%= ServerUtil.APPLET %>" /></td>
                    <td valign="top">The Java DownloadManager Applet is embedded in a web page in this browser window.  <span style="color:red;">WARNING: There currently exists a bug while running the Java Applet in Firefox 3.0.x and 3.5.x on Mac OS X only.  Please use either of the other two options if this is your environment, or use Safari 4.x instead.</span></td>
                </tr>
                <tr><td><br/></td></tr>
                <tr>
                    <td valign="top"><input type="submit" name="method" value="<%= ServerUtil.WEBSTART %>" /></td>
                    <td valign="top">The Java DownloadManager is launched as a desktop application via Java Webstart; the Java software is
                        automatically cached on your computer, so application startup is generally faster than the applet method.
                    The browser window can be used for additional work.</td>
                </tr>
                <tr><td><br/></td></tr>
                <tr>
                    <td valign="top"><input type="submit" name="method" value="<%= ServerUtil.URLS %>" /></td>
                    <td valign="top">The URL list is included in a simple web page and can be downloaded with 
                    command-line tools such as wget.</td>
                </tr>
            </tbody>
        </table>
        </div>

        <div width="66%">
            <p>
                <input type="checkbox" name="remember" value="<%= ServerUtil.APPLET %>">Remember my choice of download method</input>
                (cookies required) 
            </p>
            <p style="color: #800; padding-left: 6em; padding-right: 6em">
                We recommend that you do not check this box until you have tried the various options and 
                found one you like and that works on your system.
            </p>
        </div>
        
    </form>
    
    
    <h2>Help</h2>
    
    <h3>
        How to I get back to this page if I check the <em>remember</em> box and regret it?
    </h3>
    
    <p>
        If you chose to remember your choice of download method (the checkbox), a browser cookie is stored to 
        record the choice. If you have problems with the selected method, you can just delete the cookie and then
        return to this page. The applet and URL list pages have a button which does this for you, but the webstart
        method does not have that option.
    </p>
        
    <h3>
        I want to use the Java option but it didn't work. How can I fix it?
    </h3>
    <p>
        For general help on getting applets or webstart working, we 
        have a <a href="/JavaTest"">Java Test Page</a> with instructions.
    </p>
    
<c:catch><c:import url="<%= bodyFooter%>" /></c:catch>
</body>
</html>

