<%@ page contentType="application/x-java-jnlp-file" %>
<% response.setHeader("Content-Disposition", "attachment; filename=DownloadManager.jnlp"); %>
<?xml version="1.0" encoding="utf-8"?>

<%--
    Simple JSP page to write out a JNLP file that launches the DownloadManager application.
--%>

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
<%@ page import="ca.nrc.cadc.reg.client.RegistryClient" %>

<%

    String uris = (String) request.getAttribute("uris");
    String params = (String) request.getAttribute("params");
    String codebase = (String) request.getAttribute("codebase");
    String ssocookie = (String) request.getAttribute("ssocookie");
    String ssocookiedomain = (String) request.getAttribute("ssocookiedomain");

    String rcHostProp = RegistryClient.class.getName() + ".host";
    String rcHost = (String) request.getAttribute("targetHost");
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
        <j2se version="1.5+" initial-heap-size="64m" max-heap-size="256m" />
        <jar href="cadcDownloadManagerClient.jar"/>
        <jar href="cadcUtil.jar"/>
        <jar href="cadcRegistryClient.jar"/>
        <jar href="log4j.jar"/>
        <!-- these are needed for VOTable parsing in the cadcDALI library -->
        <jar href="cadcDALI.jar"/>
        <jar href="jdom2.jar"/>
        <jar href="xerces.jar"/>
<%
    if (rcHost != null)
    {
%>
        <property name="<%= rcHostProp %>" value="<%= rcHost %>" />
<%
    }
%>
    </resources> 

    <application-desc main-class="ca.nrc.cadc.dlm.client.Main">
        <argument>--verbose</argument>
        <argument>--uris=<%= uris %></argument>
        <argument>--params=<%= params %></argument>
	<argument>--ssocookie=<%= ssocookie %></argument>
	<argument>--ssocookiedomain=<%= ssocookiedomain %></argument>
    </application-desc>
    
</jnlp>

