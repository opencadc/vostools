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

<c:catch><c:import url="<%= bodyFooter %>" /></c:catch>
</body>

</html>

