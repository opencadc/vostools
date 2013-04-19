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

cadc.vot.Builder = function(input, readyCallback, errorCallback)
{
  this.voTable = null;
  this._builder = null;

  // Self-referencing to allow it to be used in other places where referencing
  // 'this' would be ambiguous.
  var voTableBuilder = this;

  // Get to parsing!
  if (input.xmlDOM && (input.xmlDOM.documentElement != null))
  {
    voTableBuilder._builder = new cadc.vot.XMLBuilder(input.xmlDOM);

    if (readyCallback)
    {
      readyCallback(voTableBuilder);
    }
  }
  else if (input.json)
  {
    voTableBuilder._builder = new cadc.vot.JSONBuilder(input.json);

    if (readyCallback)
    {
      readyCallback(voTableBuilder);
    }
  }
  else if (input.url)
  {
    var errorCallbackFunction;

    if (errorCallback)
    {
      errorCallbackFunction = errorCallback;
    }
    else
    {
      errorCallbackFunction = function(jqXHR, status, message)
      {
        var outputMessage =
            "VOView: Unable to read from URL (" + input.url + ").";

        if (message && ($.trim(message) != ""))
        {
          outputMessage += "\n\nMessage from server: " + message;
        }

        alert(outputMessage);
      };
    }

    $.get(input.url, {}, function(data, textStatus, jqXHR)
    {
      var contentType = jqXHR.getResponseHeader("Content-Type");

      if (contentType.indexOf("xml") > 0)
      {
        voTableBuilder._builder = new cadc.vot.XMLBuilder(data);

        if (readyCallback)
        {
          readyCallback(voTableBuilder);
        }
      }
      else if (contentType.indexOf("json") > 0)
      {
        voTableBuilder._builder = new cadc.vot.JSONBuilder(data);

        if (readyCallback)
        {
          readyCallback(voTableBuilder);
        }
      }
      else
      {
        alert("VOView: Unable to obtain XML or JSON VOTable from URL ("
              + input.url + ").");
      }
    }).error(errorCallbackFunction);
  }
  else
  {
    alert("VOView: input object is not set or not recognizeable. \n\n"
          + input);
  }
};

cadc.vot.Builder.prototype.getVOTable = function()
{
  return this.voTable;
};

cadc.vot.Builder.prototype.getData = function()
{
  if (this._builder)
  {
    return this._builder.getData();
  }
  else
  {
    return null;
  }
};

cadc.vot.Builder.prototype.build = function()
{
  if (this._builder)
  {
    this._builder.build();
    this.voTable = this._builder.getVOTable();
  }
};

/**
 * The XML plugin reader.
 *
 * @param _xmlDOM    The XML DOM to use.
 * @constructor
 */
cadc.vot.XMLBuilder = function(_xmlDOM)
{
  this.voTable = null;
  this.xmlDOM = _xmlDOM;

  if (this.xmlDOM.documentElement.nodeName == 'parsererror')
  {
    alert("VOView: XML input is invalid.\n\n");
  }
};

cadc.vot.XMLBuilder.prototype.getVOTable = function()
{
  return this.voTable;
};

/**
 * Evaluate an XPath expression aExpression against a given DOM node
 * or Document object (aNode), returning the results as an array
 * thanks wanderingstan at morethanwarm dot mail dot com for the
 * initial work.
 * @param _node     The Node to begin looking in.
 * @param _expression   The expression XPath to look for.
 */
cadc.vot.XMLBuilder.prototype.evaluateXPath = function(_node, _expression)
{
//  // Mozilla XPath Evaluator
////  var xpe = new XPathEvaluator();
  var xpe = _node.ownerDocument || _node;
//  var nsResolver = xpe.createNSResolver(_node.ownerDocument == null ?
//      _node.documentElement
//                                            : _node.ownerDocument.documentElement);
//  var nsResolver = _node.createNSResolver(_node.documentElement);

  var result = xpe.evaluate(_expression, _node, null,
                              XPathResult.ORDERED_NODE_ITERATOR_TYPE, null);
  var found = [];
  var res;

  while (res = result.iterateNext())
  {
    found.push(res);
  }

  return found;
};

cadc.vot.XMLBuilder.prototype.getData = function()
{
  return this.xmlDOM;
};

cadc.vot.XMLBuilder.prototype.build = function()
{
  // Why can't the XPath be found with the xmlns in the tag?  Makes no sense!
  // This little bit will use regex to remove all of the xmlns attributes.
  // jenkinsd 2013.01.21
  //
  var xmlString = (new XMLSerializer()).serializeToString(this.getData());
  xmlString = xmlString.replace(/<([a-zA-Z0-9 ]+)(?:xml)ns=\".*\"(.*)>/g,
                                "<" + $.trim("$1$2") + ">");
  var xmlDOM = (new DOMParser()).parseFromString(xmlString, "text/xml");

  var xmlVOTableDOM = this.evaluateXPath(xmlDOM, "VOTABLE[1]");
  var xmlVOTableResourceDOM = this.evaluateXPath(xmlVOTableDOM[0],
                                                 "RESOURCE[1]");

  var voTableParameters = [];
  var voTableResources = [];
  var voTableInfos = [];
  var resourceTables = [];
  var resourceInfos = [];

  var votableResourceInfoDOM = this.evaluateXPath(xmlVOTableResourceDOM[0],
                                                  "INFO");

  for (var infoIndex in votableResourceInfoDOM)
  {
    var nextInfo = votableResourceInfoDOM[infoIndex];
    resourceInfos.push(new cadc.vot.VOTable.Info(nextInfo.getAttribute("name"),
                                             nextInfo.getAttribute("value")));
  }

  var votableResourceDescriptionDOM =
      this.evaluateXPath(xmlVOTableResourceDOM[0], "DESCRIPTION[1]");

  var resourceDescription =
      votableResourceDescriptionDOM.length > 0
          ? votableResourceDescriptionDOM[0].value : "";
  var resourceMetadata = new cadc.vot.VOTable.Metadata(null, resourceInfos,
                                                   resourceDescription, null,
                                                   null, null);
  var xmlVOTableResourceTableDOM = this.evaluateXPath(xmlVOTableResourceDOM[0], "TABLE");

  // Iterate over tables.
  for (var tableIndex in xmlVOTableResourceTableDOM)
  {
    var resourceTableDOM = xmlVOTableResourceTableDOM[tableIndex];

    var tableFields = [];
    var resourceTableDescriptionDOM = this.evaluateXPath(resourceTableDOM,
                                                    "DESCRIPTION[1]");
    var resourceTableDescription =
        resourceTableDescriptionDOM.length > 0
            ? resourceTableDescriptionDOM[0].value : "";

    var resourceTableFieldDOM = this.evaluateXPath(resourceTableDOM, "FIELD");

    // To record the longest value for each field (Column).  Will be stored in
    // the TableData instance.
    //
    // It contains a key of the field ID, and the value is the integer length.
    //
    // Born from User Story 1103.
    var longestValues = {};

    for (var fieldIndex in resourceTableFieldDOM)
    {
      var fieldDOM = resourceTableFieldDOM[fieldIndex];
      var fieldID;
      var xmlFieldID = fieldDOM.getAttribute("id");
      var xmlFieldUType = fieldDOM.getAttribute("utype");
      var xmlFieldName = fieldDOM.getAttribute("name");

      if (xmlFieldID && (xmlFieldID != ""))
      {
        fieldID = xmlFieldID;
      }
      else if (xmlFieldUType && (xmlFieldUType != ""))
      {
        fieldID = xmlFieldUType;
      }
      else
      {
        fieldID = xmlFieldName;
      }

      longestValues[fieldID] = -1;

      var field = new cadc.vot.VOTable.Field(
          xmlFieldName,
          fieldID,
          fieldDOM.getAttribute("ucd"),
          xmlFieldUType,
          fieldDOM.getAttribute("unit"),
          fieldDOM.getAttribute("xtype"),
          fieldDOM.getAttribute("datatype"),
          fieldDOM.getAttribute("arraysize"),
          fieldDOM.getAttribute("description"),
          fieldDOM.getAttribute("name"));

      tableFields.push(field);
    }

    var tableMetadata = new cadc.vot.VOTable.Metadata(null, null,
                                                  resourceTableDescription,
                                                  null, tableFields, null);

    var tableDataRows = [];
    var tableDataRowsDOM = this.evaluateXPath(resourceTableDOM,
                                         "DATA[1]/TABLEDATA[1]/TR");

    for (var rowIndex in tableDataRowsDOM)
    {
      var rowDataDOM = tableDataRowsDOM[rowIndex];
      var rowCells = [];
      var rowCellsDOM = this.evaluateXPath(rowDataDOM, "TD");

      for (var cellIndex in rowCellsDOM)
      {
        var cellDataDOM = rowCellsDOM[cellIndex];
        var cellField = tableFields[cellIndex];
        var dataType = cellField.getDatatype()
                       ? cellField.getDatatype().toLowerCase() : "";
        var stringValue = (cellDataDOM.childNodes && cellDataDOM.childNodes[0])
                          ? cellDataDOM.childNodes[0].nodeValue : "";
        var stringValueLength = (stringValue && stringValue.length)
                                ? stringValue.length : -1;
        var currLongestValue = longestValues[cellField.getID()];

        if (stringValueLength > currLongestValue)
        {
          longestValues[cellField.getID()] = stringValueLength;
        }

        var cellValue;

        if ((dataType == "double") || (dataType == "int")
            || (dataType == "long") || (dataType == "float")
            || (dataType == "short"))
        {
          var num;

          if (!stringValue || ($.trim(stringValue) == ""))
          {
            num = "";
          }
          else if ((dataType == "double") || (dataType == "float"))
          {
            num = parseFloat(stringValue);
            num.toFixed(2);
          }
          else
          {
            num = parseInt(stringValue);
          }

          cellValue = num;
        }
        else
        {
          cellValue = stringValue;
        }

        rowCells.push(new cadc.vot.VOTable.Cell(cellValue, cellField));
      }

      var rowID = rowDataDOM.getAttribute("id");

      if (!rowID)
      {
        rowID = "vov_" + rowIndex;
      }

      tableDataRows.push(new cadc.vot.VOTable.Row(rowID, rowCells));
    }

    var tableData = new cadc.vot.VOTable.TableData(tableDataRows,
                                                   longestValues);

    resourceTables.push(new cadc.vot.VOTable.Table(tableMetadata, tableData));
  }

  voTableResources.push(
      new cadc.vot.VOTable.Resource(xmlVOTableResourceDOM[0].getAttribute("type"),
                                resourceMetadata, resourceTables));

  var xmlVOTableDescription = this.evaluateXPath(xmlVOTableDOM[0],
                                                 "DESCRIPTION[1]");
  var voTableDescription = xmlVOTableDescription.length > 0
                           ? xmlVOTableDescription[0].value : "";
  var voTableMetadata = new cadc.vot.VOTable.Metadata(voTableParameters,
                                                  voTableInfos,
                                                  voTableDescription, null,
                                                  null, null);

  this.voTable = new cadc.vot.VOTable(voTableMetadata, voTableResources);
};
// End XML.

/**
 * The JSON plugin reader.
 *
 * @param jsonData    The JSON VOTable.
 * @constructor
 */
cadc.vot.JSONBuilder = function(jsonData)
{
  this.voTable = null;
  this.jsonData = jsonData;
};

cadc.vot.JSONBuilder.prototype.getVOTable = function()
{
  return this.voTable;
};

cadc.vot.JSONBuilder.prototype.getData = function()
{
  return this.jsonData;
};

cadc.vot.JSONBuilder.prototype.build = function()
{
  // Does nothing yet.
};
