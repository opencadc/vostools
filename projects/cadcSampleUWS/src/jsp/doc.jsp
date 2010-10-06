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
<head>
    <title>cadcSampleUWS</title>
</head>
<body>

<%
String sname = request.getServerName();  
String async = request.getScheme() + "://" + sname + "/cadcSampleUWS/async";
String sync = request.getScheme() + "://" + sname + "/cadcSampleUWS/sync";
%>

<h1>cadcSampleUWS: A Sample UWS Service </h1>

<p>
This is the sample <a href="http://www.ivoa.net/Documents/UWS/">UWS</a> service from the 
<a href="http://googlecode.com/p/opencadc/">OpenCADC</a> project. This
application uses several components from OpenCADC: cadcUtil, cadcUWS (base application code with support for both async 
service and sync services), and cadcSampleUWS (this app).
</p>

<h1>How to use the sample service and explore UWS</h1>

<p>
The async UWS job list endpoint: <b><%= async %></b>
</p>
In  addition to UWS job creation and control, this
service supports two parameters:
</p>

<ul>
<li>RUNFOR=&lt;time in seconds&gt;: the amount of time the sample job should run (e.g. sleep) for
<li>PASS=&lt;true | false&gt;: whether the job should succeed or fail after the specified time
</ul>

<p>
You can create and control the job as documented in the <a href="http://www.ivoa.net/Documents/UWS/">UWS</a>
specification.
</p>

<h1>Now with synchronous job execution mode!</h1>

<p>
In addition to the async UWS access, you can also run the sample job using the same parameters
in synchronous mode. 
</p>
<p>
The sync endpoint: <b><%= sync %></b>
</p>
<p>
Note: The sync implementation supports both GET and POST requests in two modes: streaming output or a redirect
to another resource. (Technically there is always at least one redirect after the request; the latter mode allows
the job code to run and then issue a redirect itself instead of streaming the output.)
</p>
<p>
TODO: document the streaming vs redirect parameter
</p>

</body>

</html>

