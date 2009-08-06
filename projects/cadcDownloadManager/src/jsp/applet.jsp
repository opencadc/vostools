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
<%
    // we need these to offer the option to go back to the method choice page
    String uris = (String) request.getAttribute("uris");
    String fragment = (String) request.getAttribute("fragment");
%>

<%
String skin = (String) request.getParameter("skin");
if (skin == null)
    skin = "http://localhost/cadc/skin/";
if (!skin.endsWith("/"))
    skin += "/";
String htmlHead = skin + "htmlHead";
String bodyHeader = skin + "bodyHeader";
String bodyFooter = skin + "bodyFooter";
%>

<html>
<head>
    <c:catch><c:import url="<%= htmlHead %>" /></c:catch>
</head>

<body onResize="resize()">
    
    <c:catch><c:import url="<%= bodyHeader %>" /></c:catch>
    
    <script language="JavaScript">
    function resize() {
        if (navigator.appName.indexOf("Microsoft") != -1) 
        {
            width = document.body.clientWidth;
            height = document.body.clientHeight;
        }
        else 
        {
            width = window.innerWidth;
            height = window.innerHeight;
        }
        hfHeight = 120;
        document.DownloadManager.width = width - 60;
        document.DownloadManager.height = height - 60 - hfHeight;
    }
    window.onResize = resize;
    </script>   
    
    <p>
        <center>
            <c:import url="appletTag.jsp" />
        </center>
    </p>
    
<div style="padding-left: 2em; padding-right: 2em">
    <form action="/downloadManager/download" method="POST">
        <input type="hidden" name="uris" value="<%= uris %>" />
        <input type="hidden" name="fragment" value="<%= fragment %>" />
        <input type="hidden" name="skin" value="<%= skin %>" /> 
        <input type="submit" name="method" value="Chose one of the other download methods" />
    </form>
</div

<c:catch><c:import url="<%= bodyFooter%>" /></c:catch>
</body>
</html>