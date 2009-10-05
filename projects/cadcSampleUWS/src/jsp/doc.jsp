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

<%@ page language="java" %>
<%@ page import="java.util.*" %>
<%@ page import="ca.nrc.cadc.preview.*" %>

<%@ taglib uri="WEB-INF/c.tld" prefix="c"%>

<%
    String skin = "http://localhost/cadc/skin/";
    String htmlHead = skin + "htmlHead";
    String bodyHeader = skin + "bodyHeader";
    String bodyFooter = skin + "bodyFooter";
%>

<head>
    <title>cadcSampleUWS</title>
    <c:catch><c:import url="<%= htmlHead %>" /></c:catch>
</head>
<body>
<c:catch><c:import url="<%= bodyHeader %>" /></c:catch>

<div class="main">

<h1>cadcSampleUWS Documentation</h1>

<p>
The cadcSampleUWS project contains the code for a sample implementation of the cadcUWS JobRunner interface.
</p>

<h1>How to use</h1>

<p>
Verify that this webapp has been deployed by browsing this page on the target server.
</p>
<p>
The HelloWorld class either succeeds or fails, depending on the values supplied for the two parameters of its Job object.
The parameter combination:<br>
&nbsp;&nbsp;<code>RUNFOR=&lt;some non-negative integer number of seconds&gt;</code><br>
&nbsp;&nbsp;<code>PASS=true</code><br>
will cause HelloWorld.run() to sleep for the specified number of seconds and return the contents of results.txt as the result.
A negative run time will immediately cause the contents of error.txt to be returned as the error.
A valid run time combined with PASS=false is intended to simulate a run that works fine for a while before failing.
</p>

<h1>How to customize</h1>

<p>
The easiest way to create a custom UWS would be to simply copy and modify this one.
</p>
<p>
Replace ca.nrc.cadc.uws.sample.HelloWorld with your own service by changing what the doit()
method does between when it sets the phase to EXECUTING and when it gets set to COMPLETED or ERROR.
</p>
<p>
The in the build.xml file, the ext.lib property is the directory that collects together any required external JAR files
such as those for log4j, restlets, and the servlet API.
The LogControlServlet is used to initialize log4j logging of java packages specified in the web.xml file.
It works best if it is configured to be the first servlet loaded by Tomcat 5.5 or later.
</p>

<c:catch><c:import url="<%= bodyFooter %>" /></c:catch>
</body>

</html>

