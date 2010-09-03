<?xml version="1.0" encoding="iso-8859-1"?>
<xsl:stylesheet version="1.0"
xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns="http://www.ivoa.net/xml/VOSpace/v2.0" xmlns:vos="http://www.ivoa.net/xml/VOSpace/v2.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">

<!-- Sample XSL file for rendering HTML from Node XML -->

<xsl:output method="html" indent="yes" media-type="text/xml"/>

<xsl:template match="/">
 
 <html xmlns="http://www.w3.org/1999/xhtml">
 
    <xsl:variable name="nodeuri" select="vos:node/@uri"/>
 
 	<head>
 		<title>
 		  <xsl:value-of select="vos:node/@xsi:type"/>: <xsl:value-of select="$nodeuri"/>
 		</title>
 		<link href='http://fonts.googleapis.com/css?family=Nobile' rel='stylesheet' type='text/css'/>
 		<style>
 			body {
 				font-family: 'Nobile', serif;
 				font-size: 12px;
 				color: #5F5F5F;
 			}
 		</style>
 	</head>
 	<body>

		<h1>
 			<xsl:value-of select="vos:node/@xsi:type"/>: <xsl:value-of select="$nodeuri"/>
 		</h1>
 	
	  	<xsl:apply-templates select="vos:node/vos:properties"/>
	  	<xsl:apply-templates select="vos:node/vos:nodes"/>
	  	<xsl:apply-templates select="vos:node/vos:accepts"/>
	  	<xsl:apply-templates select="vos:node/vos:provides"/>
	  	
	  	<hr/>
 		
 	</body>
 </html>
</xsl:template>

<xsl:template match="vos:node/vos:properties">
	<div xmlns="http://www.w3.org/1999/xhtml">
 	<h2>Properties</h2>
 	<ul>
 		<xsl:apply-templates select="vos:property"/>
   </ul>
   </div>
</xsl:template>

 <xsl:template match="//vos:property">
 	<li xmlns="http://www.w3.org/1999/xhtml">
 		<xsl:value-of select="@uri"/>
 		<xsl:text>=</xsl:text>
 		<xsl:value-of select="."/>
   </li>
 </xsl:template>

<xsl:template match="vos:node/vos:nodes">
	<div xmlns="http://www.w3.org/1999/xhtml">
 	<h2>Child Nodes</h2>
 	<ul>
 		<xsl:apply-templates select="vos:node"/>
   </ul> 		
   </div>
</xsl:template>

 <xsl:template match="//vos:node">
 	<li xmlns="http://www.w3.org/1999/xhtml">
 		<xsl:variable name="nodeurl">
 			<xsl:value-of select="'/vospace/nodes'"/>
			<xsl:value-of select="substring-after(@uri, '!vospace')"/>
 		</xsl:variable>
 		<a href="{$nodeurl}"> 	
 			<xsl:value-of select="@uri"/>
 		</a>
   </li>
 </xsl:template>
 
 <xsl:template match="vos:node/vos:accepts">
	<div xmlns="http://www.w3.org/1999/xhtml">
 	<h2>Accepts Views</h2>
 	<ul>
 		<xsl:apply-templates select="vos:view">
 			<xsl:with-param name="link" select="'false'"/>
 		</xsl:apply-templates>
   </ul>
   </div>
</xsl:template>

 <xsl:template match="vos:node/vos:provides">
	<div xmlns="http://www.w3.org/1999/xhtml">
 	<h2>Provides Views</h2>
 	<ul>
 		<xsl:apply-templates select="vos:view">
 			<xsl:with-param name="link" select="'true'"/>
 		</xsl:apply-templates>
   </ul>
   </div>
</xsl:template>

 <xsl:template match="//vos:view">
 	<xsl:param name="link"/>
 	<xsl:variable name="nodeuri" select="vos:node/@uri"/>
 	<xsl:if test="$link='true'">
 		<li xmlns="http://www.w3.org/1999/xhtml">
 			<xsl:variable name="viewurl">
				<xsl:value-of select="substring-after($nodeuri, '!vospace')"/>
				<xsl:value-of select="'?view='"/>
				<xsl:value-of select="@uri"/>
 			</xsl:variable>
 			<a href="{$viewurl}">
 				<xsl:value-of select="@uri"/>
 			</a>
  		</li>
  	</xsl:if>
  	<xsl:if test="$link='false'">
  		<xsl:value-of select="@uri"/>
  	</xsl:if>
 </xsl:template>

</xsl:stylesheet>