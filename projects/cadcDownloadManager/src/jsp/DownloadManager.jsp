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

<%--
    Simple JSP page to write out a JNLP file that launches the DownloadManager application.
--%>
<%@ page contentType="application/x-java-jnlp-file" %>
<% response.setHeader("Content-Disposition", "attachment; filename=DownloadManager.jnlp"); %>
<?xml version="1.0" encoding="utf-8"?> 

<%
    String uris = (String) request.getAttribute("uris");
    String fragment = (String) request.getAttribute("fragment");
    String codebase = (String) request.getAttribute("codebase");
%>

<jnlp spec="1.0+" codebase="<%= codebase %>"> 
  
  <information> 
    <title>DownloadManager</title> 
    <vendor>Canadian Astronomy Data Centre</vendor> 
    <homepage href="/"/> 
    <description>Simple multithreaded download of data from the CADC</description>
    </information>

    <security> 
        <all-permissions/> 
    </security> 

    <resources> 
        <j2se version="1.4+" initial-heap-size="64m" max-heap-size="128m" 
            href="http://java.sun.com/products/autodl/j2se"/> 
        <jar href="cadcDownloadManagerClient.jar"/> 
        <jar href="cadcDownloadManager.jar" />
    </resources> 

    <application-desc main-class="ca.nrc.cadc.dlm.client.Main">
        <argument>--uris=<%= uris %></argument>
        <argument>--fragment=<%= fragment %></argument>
    </application-desc>
    
</jnlp>

