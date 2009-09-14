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
<%
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

<body>
<c:catch><c:import url="<%= bodyHeader %>" /></c:catch>

<p>
    You can download individual files by clicking on their filenames 
    in the list below.
</p>

<p>
    To speed download of all of the files in this request, save this file to your
    local system with a mouse right click and then execute the following command:
</p>

<p> 
    <code> 
        % wget --http-user=CADC_USER --http-password=CADC_PASSWORD 
        --force-html --input-file FILE_NAME 
    </code> 
</p>

<p> 
<b>
    Be certain to fill in your own CADC_USERNAME, CADC_PASSWORD and the used FILE_NAME
    in the appropriate places in that command, or you will get an error from
    <i>wget</i>.</b> 
</p>

<p> 
    Please note that there are many versions of <i>wget</i> with a variety of 
    options and syntax.  Please consult your local help pages before contacting 
    us.&nbsp;   <code>wget --help</code> should reveal the arguments supported by 
    your version of <i>wget</i>.  
</p>

<p> 
    The <i>wget</i> command should be available on 
    most systems. If not, <i>wget</i> can be downloaded from gnu.org.
    Alternately, you can try one of the several other web download utilities
    such as: curl, HTTrack, leech (mozilla add-on), pavuk, lftp, etc.
</p>

<p>
Please report any problems downloading your files to <a href="mailto:cadc@nrc.ca">cadc@nrc.ca</a>
</p>

<br>

  
<p>
    <c:import url="urlList.jsp" />
</p>
<div style="padding-left: 2em; padding-right: 2em">
    <form action="/downloadManager/download" method="POST">
        <input type="hidden" name="uris" value="<%= uris %>" />
        <input type="hidden" name="fragment" value="<%= fragment %>" />
        <input type="hidden" name="skin" value="<%= skin %>" /> 
        <input type="submit" name="method" value="Chose one of the other download methods" />
    </form>
</div>    
<c:catch><c:import url="<%= bodyFooter%>" /></c:catch>
</body>
</html>
