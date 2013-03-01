/**
 *
 * Sample VOTable XML Document.
 *
 * <VOTABLE version="1.2" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
 * xmlns="http://www.ivoa.net/xml/VOTable/v1.2"
 * xmlns:stc="http://www.ivoa.net/xml/STC/v1.30" >
 <RESOURCE name="myFavouriteGalaxies">
 <TABLE name="results">
 <DESCRIPTION>Velocities and Distance estimations</DESCRIPTION>
 <GROUP ID="J2000" utype="stc:AstroCoords">
 <PARAM datatype="char" arraysize="*" ucd="pos.frame" name="cooframe"
 utype="stc:AstroCoords.coord_system_id" value="UTC-ICRS-TOPO" />
 <FIELDref ref="col1"/>
 <FIELDref ref="col2"/>
 </GROUP>
 <PARAM name="Telescope" datatype="float" ucd="phys.size;instr.tel"
 unit="m" value="3.6"/>
 <FIELD name="RA"   ID="col1" ucd="pos.eq.ra;meta.main" ref="J2000"
 utype="stc:AstroCoords.Position2D.Value2.C1"
 datatype="float" width="6" precision="2" unit="deg"/>
 <FIELD name="Dec"  ID="col2" ucd="pos.eq.dec;meta.main" ref="J2000"
 utype="stc:AstroCoords.Position2D.Value2.C2"
 datatype="float" width="6" precision="2" unit="deg"/>
 <FIELD name="Name" ID="col3" ucd="meta.id;meta.main"
 datatype="char" arraysize="8*"/>
 <FIELD name="RVel" ID="col4" ucd="spect.dopplerVeloc" datatype="int"
 width="5" unit="km/s"/>
 <FIELD name="e_RVel" ID="col5" ucd="stat.error;spect.dopplerVeloc"
 datatype="int" width="3" unit="km/s"/>
 <FIELD name="R" ID="col6" ucd="pos.distance;pos.heliocentric"
 datatype="float" width="4" precision="1" unit="Mpc">
 <DESCRIPTION>Distance of Galaxy, assuming H=75km/s/Mpc</DESCRIPTION>
 </FIELD>
 <DATA>
 <TABLEDATA>
 <TR>
 <TD>010.68</TD><TD>+41.27</TD><TD>N  224</TD><TD>-297</TD><TD>5</TD><TD>0.7</TD>
 </TR>
 <TR>
 <TD>287.43</TD><TD>-63.85</TD><TD>N 6744</TD><TD>839</TD><TD>6</TD><TD>10.4</TD>
 </TR>
 <TR>
 <TD>023.48</TD><TD>+30.66</TD><TD>N  598</TD><TD>-182</TD><TD>3</TD><TD>0.7</TD>
 </TR>
 </TABLEDATA>
 </DATA>
 </TABLE>
 </RESOURCE>
 </VOTABLE>

 The Data Model can be expressed as:
 VOTable 	= 	hierarchy of Metadata + associated TableData, arranged as a set of Tables
 Metadata 	= 	Parameters + Infos + Descriptions + Links + Fields + Groups
 Table 	= 	list of Fields + TableData
 TableData 	= 	stream of Rows
 Row 	= 	list of Cells
 Cell 	=
 Primitive
 or variable-length list of Primitives
 or multidimensional array of Primitives
 Primitive 	= 	integer, character, float, floatComplex, etc (see table of primitives below).
 */


var cadc;
if (!cadc)
{
  cadc = {};
}
else if (typeof cadc != "object")
{
  throw new Error("cadc already exists and is not an object");
}

if (!cadc.vot)
{
  cadc.vot = {};
}
else if (typeof cadc.vot != "object")
{
  throw new Error("cadc.vot already exists and is not an object");
}


/**
 * The VOTable object.
 *
 * @param __metadata    The metadata from the source.
 * @param __resources   The resources from the source.
 * @constructor
 */
cadc.vot.VOTable = function(__metadata, __resources)
{
  this.resources = __resources;
  this.metadata = __metadata;
};

cadc.vot.VOTable.prototype.getResources = function()
{
  return this.resources;
};

cadc.vot.VOTable.prototype.getMetadata = function()
{
  return this.metadata;
};


cadc.vot.VOTable.Metadata = function(__parameters, __infos, _description, __links,
                                 __fields, __groups)
{
  this.parameters = __parameters;
  this.infos = __infos;
  this.description = _description;
  this.links = __links;
  this.fields = __fields;
  this.groups = __groups;
};

cadc.vot.VOTable.Metadata.prototype.getInfos = function()
{
  return this.infos;
};

cadc.vot.VOTable.Metadata.prototype.getDescription = function()
{
  return this.description;
};

cadc.vot.VOTable.Metadata.prototype.getFields = function()
{
  return this.fields;
};

cadc.vot.VOTable.Metadata.prototype.getLinks = function()
{
  return this.links;
};

cadc.vot.VOTable.Metadata.prototype.getGroups = function()
{
  return this.groups;
};


cadc.vot.VOTable.Field = function(_name, _id, _ucd, _utype, _unit, _xtype,
                              _datatype, _arraysize, _description, label)
{
  this.name = _name;
  this.id = _id;
  this.ucd = _ucd;
  this.utype = _utype;
  this.unit = _unit;
  this.xtype = _xtype;
  this.datatype = _datatype;
  this.arraysize = _arraysize;
  this.description = _description;
  this.label = label;
};

cadc.vot.VOTable.Field.prototype.getName = function()
{
  return this.name;
};

cadc.vot.VOTable.Field.prototype.getID = function()
{
  return this.id;
};

cadc.vot.VOTable.Field.prototype.getLabel = function()
{
  return this.label;
};

cadc.vot.VOTable.Field.prototype.getUType = function()
{
  return this.utype;
};

cadc.vot.VOTable.Field.prototype.getXType = function()
{
  return this.xtype;
};

cadc.vot.VOTable.Field.prototype.getDatatype = function()
{
  return this.datatype;
};

cadc.vot.VOTable.Field.prototype.getDescription = function()
{
  return this.description;
};


cadc.vot.VOTable.Parameter = function(_name, _id, _ucd, _utype, _unit, _xtype,
                                  _datatype, _arraysize, _description, _value)
{
  this.name = _name;
  this.id = _id;
  this.ucd = _ucd;
  this.utype = _utype;
  this.unit = _unit;
  this.xtype = _xtype;
  this.datatype = _datatype;
  this.arraysize = _arraysize;
  this.description = _description;
  this.value = _value;
};

cadc.vot.VOTable.Parameter.prototype.getName = function()
{
  return this.name;
};

cadc.vot.VOTable.Parameter.prototype.getValue = function()
{
  return this.value;
};

cadc.vot.VOTable.Parameter.prototype.getUType = function()
{
  return this.utype;
};

cadc.vot.VOTable.Parameter.prototype.getID = function()
{
  return this.id;
};

cadc.vot.VOTable.Parameter.prototype.getUCD = function()
{
  return this.ucd;
};

cadc.vot.VOTable.Parameter.prototype.getDescription = function()
{
  return this.description;
};

cadc.vot.VOTable.Info = function(_name, _value)
{
  this.name = _name;
  this.value = _value;
};

cadc.vot.VOTable.Info.prototype.getName = function()
{
  return this.name;
};

cadc.vot.VOTable.Info.prototype.getValue = function()
{
  return this.value;
};

cadc.vot.VOTable.Info.prototype.isError = function()
{
  return this.getName() == "ERROR";
};


cadc.vot.VOTable.Resource = function(_type, __metadata, __tables)
{
  this.type = _type;
  this.metadata = __metadata;
  this.tables = __tables;
};

cadc.vot.VOTable.Resource.prototype.getTables = function()
{
  return this.tables;
};

cadc.vot.VOTable.Resource.prototype.getType = function()
{
  return this.type;
};

cadc.vot.VOTable.Resource.prototype.getDescription = function()
{
  return this.metadata.getDescription();
};

cadc.vot.VOTable.Resource.prototype.getInfos = function()
{
  return this.metadata.getInfos();
};


cadc.vot.VOTable.Table = function(__metadata, __tabledata)
{
  this.metadata = __metadata;
  this.tabledata = __tabledata;
};

cadc.vot.VOTable.Table.prototype.getTableData = function()
{
  return this.tabledata;
};

cadc.vot.VOTable.Table.prototype.getFields = function()
{
  return this.metadata.getFields();
};


cadc.vot.VOTable.Row = function(_id, __cells)
{
  this.id = _id;
  this.cells = __cells;
};

cadc.vot.VOTable.Row.prototype.getID = function()
{
  return this.id;
};

cadc.vot.VOTable.Row.prototype.getCells = function()
{
  return this.cells;
};

cadc.vot.VOTable.Row.prototype.getSize = function()
{
  if (this.getCells())
  {
    return this.getCells().length;
  }
  else
  {
    return 0;
  }
};

cadc.vot.VOTable.Cell = function(_value, __field)
{
  this.value = _value;
  this.field = __field;
};

cadc.vot.VOTable.Cell.prototype.getValue = function()
{
  return this.value;
};

cadc.vot.VOTable.Cell.prototype.getField = function()
{
  return this.field;
};

cadc.vot.VOTable.TableData = function(__rows)
{
  this.rows = __rows;
};

cadc.vot.VOTable.TableData.prototype.getRows = function()
{
  return this.rows;
};
