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

<%@ taglib uri="WEB-INF/c.tld" prefix="c"%>

<%@ page import="ca.nrc.cadc.dlm.DownloadUtil" %>
<%@ page import="ca.nrc.cadc.dlm.DownloadDescriptor" %>
<%@ page import="java.util.Iterator" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Map" %>

<%
    String uris = (String) request.getAttribute("uris");
    String params = (String) request.getAttribute("params");

    List<String> uriList = DownloadUtil.decodeListURI(uris);
    Map<String,List<String>> paramMap = DownloadUtil.decodeParamMap(params);
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
    <script type="text/javascript">
	function closemyself()
	{
		window.opener = self;
		self.close();
	}
</script>
</head>

<body>
<c:catch><c:import url="<%= bodyHeader %>" /></c:catch>

<p>
    You can download individual files by clicking on their filenames 
    in the list below.
</p>

<br>

  
<p>
    <ul>
<%
    Iterator<DownloadDescriptor> iter = DownloadUtil.iterateURLs(uriList, paramMap, true);
    while ( iter.hasNext() )
    {
        DownloadDescriptor dd = iter.next();
        if (dd.url != null)
        {
%>
<li>
    <a href="<%= dd.url %>"> <%= dd.url %></a>
</li>
<%
        }
        else
        {
%>
<li>
    <%= dd.uri %>: <%= dd.error %>
</li>
<%
        }
        out.flush();
    }
    out.flush();
%>
</ul>

</p>

<div style="padding-left: 2em; padding-right: 2em">
    <form action="/downloadManager/download" method="POST">
        <input type="hidden" name="uris" value="<%= uris %>" />
        <input type="hidden" name="params" value="<%= params %>" />
        <input type="hidden" name="skin" value="<%= skin %>" /> 
        <input type="submit" name="clearCookie" value="Chose one of the other download methods" />
        <input type="submit" OnClick="closemyself()" value="Close window" />
    </form>
</div>    
<c:catch><c:import url="<%= bodyFooter%>" /></c:catch>
</body>
</html>
