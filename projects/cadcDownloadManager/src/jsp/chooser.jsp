<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN"
        "http://www.w3.org/TR/html4/loose.dtd">

<!--

    Copyright (C) 2009				Copyright (C) 2009
    National Research Council		Conseil national de recherches
    Ottawa, Canada, K1A 0R6			Ottawa, Canada, K1A 0R6
    All rights reserved				Tous droits reserves
    					
    NRC disclaims any warranties,	Le CNRC denie toute garantie
    expressed, implied, or statu-	enoncee, implicite ou legale,
    tory, of any kind with respect	de quelque nature que se soit,
    to the software, including		concernant le logiciel, y com-
    without limitation any war-		pris sans restriction toute
    ranty of merchantability or		garantie de valeur marchande
    fitness for a particular pur-	ou de pertinence pour un usage
    pose.  NRC shall not be liable	particulier.  Le CNRC ne
    in any event for any damages,	pourra en aucun cas etre tenu
    whether direct or indirect,		responsable de tout dommage,
    special or general, consequen-	direct ou indirect, particul-
    tial or incidental, arising		ier ou general, accessoire ou
    from the use of the software.	fortuit, resultant de l'utili-
    								sation du logiciel.
    
    
    This file is part of cadcDownloadManager.
    
    CadcDownloadManager is free software: you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.
    
    CadcDownloadManager is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Affero General Public License for more details.
    
    You should have received a copy of the GNU Affero General Public License
    along with cadcDownloadManager.  If not, see <http://www.gnu.org/licenses/>.	
    
-->

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
                    <td valign="top">The Java DownloadManager Applet is embedded in a web page in this browser window.</td>
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

