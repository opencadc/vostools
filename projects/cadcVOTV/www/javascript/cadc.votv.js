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

var sortAsc;
var sortcol;
var viewer;

/**
 * Create a VOView object.  This is here to package everything together.
 *
 * @param targetNodeSelector  The target node selector to place the viewer.
 * @param options             The options object.
 * editable: true/false,
 * enableAddRow: true/false,
 * showHeaderRow: true/false,
 * enableCellNavigation: true/false,
 * asyncEditorLoading: true/false,
 * forceFitColumns: true/false,
 * explicitInitialization: true/false,
 * topPanelHeight: Number,
 * headerRowHeight: Number,
 * showTopPanel: true,
 * sortColumn: Start Date
 * sortDir: asc/desc
 * @constructor
 */
cadc.vot.Viewer = function (targetNodeSelector, options)
{
  this.dataView = null;
  this.grid = null;
  this.columnManager = options.columnManager ? options.columnManager : {};
  this.data = [];
  this.columns = [];
  this.displayColumns = [];  // Columns that are actually in the Grid.
  this.columnFilters = {};
  this.targetNodeSelector = targetNodeSelector;
  this.columnOptions = options.columnOptions ? options.columnOptions : {};
  this.options = options;
  this.options.forceFitColumns = options.columnManager
      ? options.columnManager.forceFitColumns
      : false;

  // This is the TableData for a VOTable.  Will be set on load.
  this.voTableData = null;

  sortcol = options.sortColumn;
  sortAsc = options.sortDir == "asc";

  viewer = this;
};

/**
 * @param input  Object representing the input.
 *
 * One of xmlDOM or json or url is required.
 *
 * input.xmlDOM = The XML DOM Object
 * input.json = The JSON Object
 * input.url = The URL of the input.  The Content-Type will dictate how to
 *             build it.
 * @param completeCallback  Callback function when complete.
 * @param errorCallBack     Callback function with jqXHR, status, message
 *                    (Conforms to jQuery error callback for $.ajax calls).
 */
cadc.vot.Viewer.prototype.build = function (input, completeCallback, errorCallBack)
{
  new cadc.vot.Builder(input,
                       function (voTableBuilder)
                       {
                         voTableBuilder.build();

                         var voTable = voTableBuilder.getVOTable();
                         var hasDisplayColumns =
                             (viewer.displayColumns
                                 && (viewer.displayColumns.length > 0));

                         viewer.load(voTable, !hasDisplayColumns, true);
                         viewer.init();

                         if (completeCallback)
                         {
                           completeCallback();
                         }
                       }, errorCallBack);
};

cadc.vot.Viewer.prototype.getTargetNodeSelector = function ()
{
  return this.targetNodeSelector;
};

cadc.vot.Viewer.prototype.getPagerNodeSelector = function ()
{
  return "#pager";
};

cadc.vot.Viewer.prototype.getHeaderNodeSelector = function ()
{
  return "div.grid-header";
};

cadc.vot.Viewer.prototype.getColumnManager = function ()
{
  return this.columnManager;
};

cadc.vot.Viewer.prototype.getColumns = function ()
{
  return this.columns;
};

cadc.vot.Viewer.prototype.getColumnOptions = function ()
{
  return this.columnOptions;
};

cadc.vot.Viewer.prototype.getOptionsForColumn = function (columnLabel)
{
  return this.getColumnOptions()[columnLabel]
      ? this.getColumnOptions()[columnLabel] : {};
};

cadc.vot.Viewer.prototype.getColumnFilters = function ()
{
  return this.columnFilters;
};

cadc.vot.Viewer.prototype.addColumn = function (columnObject)
{
  this.columns.push(columnObject);
};

cadc.vot.Viewer.prototype.setColumns = function (cols)
{
  this.columns = cols.slice(0);
};

cadc.vot.Viewer.prototype.clearColumns = function ()
{
  this.columns.length = 0;
};

cadc.vot.Viewer.prototype.comparer = function (a, b)
{
  var x = a[sortcol], y = b[sortcol];
  return (x == y ? 0 : (x > y ? 1 : -1));
};

cadc.vot.Viewer.prototype.addRow = function (rowData, rowIndex)
{
  this.getGridData()[rowIndex] = rowData;
};

cadc.vot.Viewer.prototype.clearRows = function ()
{
  this.data.length = 0;
};

cadc.vot.Viewer.prototype.setDataView = function (dataViewObject)
{
  this.dataView = dataViewObject;
};

cadc.vot.Viewer.prototype.getDataView = function ()
{
  return this.dataView;
};

cadc.vot.Viewer.prototype.setGrid = function (gridObject)
{
  this.grid = gridObject;
};

cadc.vot.Viewer.prototype.getSelectedRows = function ()
{
  return this.getGrid().getSelectedRows();
};

cadc.vot.Viewer.prototype.getGrid = function ()
{
  return this.grid;
};

cadc.vot.Viewer.prototype.getColumn = function (columnID)
{
  return this.getGrid().getColumns()[
      this.getGrid().getColumnIndex(columnID)];
};

cadc.vot.Viewer.prototype.sort = function ()
{
  if (sortcol)
  {
//    this.getGrid().setSortColumn(sanitizeString(sortcol),
//                                 (sortAsc || (sortAsc == 1)));
    this.getGrid().setSortColumn(sortcol,
                                 (sortAsc || (sortAsc == 1)));
  }
};

cadc.vot.Viewer.prototype.getGridData = function ()
{
  return this.data;
};

cadc.vot.Viewer.prototype.getOptions = function ()
{
  return this.options;
};

cadc.vot.Viewer.prototype.setOptions = function (optionsDef)
{
  this.options = optionsDef;
};

cadc.vot.Viewer.prototype.usePager = function ()
{
  return viewer.getOptions() && viewer.getOptions().pager;
};

/**
 * Obtain the TableData instance for this VOTable representation.
 *
 * @returns {*}   TableData instance.
 */
cadc.vot.Viewer.prototype.getVOTableData = function ()
{
  return this.voTableData;
};

cadc.vot.Viewer.prototype.setVOTableData = function (__voTableData)
{
  this.voTableData = __voTableData;
};

/**
 * Get the columns that are to BE displayed.
 * @return {Array}    Array of Column objects.
 */
cadc.vot.Viewer.prototype.getDisplayColumns = function ()
{
  if (!this.displayColumns || (this.displayColumns.length == 0))
  {
    this.setDisplayColumns(this.getDefaultColumns().slice(0));
  }
  return this.displayColumns;
};

/**
 * Get the columns that are currently displayed.
 * @return {Array}    Array of Column objects.
 */
cadc.vot.Viewer.prototype.getDisplayedColumns = function ()
{
  var cols = [];

  if (this.getGrid())
  {
    cols = this.getGrid().getColumns();
  }
  else
  {
    cols = [];
  }

  return cols;
};

cadc.vot.Viewer.prototype.setDisplayColumns = function (dispCols)
{
  this.displayColumns = dispCols;
};

cadc.vot.Viewer.prototype.getDefaultColumns = function ()
{
  var cols = [];
  var opts = this.getOptions();
  var defaultColumnIDs = opts.defaultColumnIDs;
  if (!defaultColumnIDs || (defaultColumnIDs.length == 0))
  {
    cols = this.getColumns().slice(0);
  }
  else
  {
    for (var colID in defaultColumnIDs)
    {
      if (defaultColumnIDs[colID])
      {
        var thisCols = this.getColumns();
        for (var col in thisCols)
        {
          if (thisCols[col].id == defaultColumnIDs[colID])
          {
            cols.push(thisCols[col]);
          }
        }
      }
    }
  }

  return cols;
};

/**
 * This function is passed to SlickGrid, so be careful when using 'this'.
 *
 * @param item        The item to filter on.
 * @return {boolean}   True if passes the filter, false otherwise.
 */
cadc.vot.Viewer.prototype.searchFilter = function (item)
{
  var filters = viewer.getColumnFilters();
  for (var columnId in filters)
  {
    var filterValue = filters[columnId];
    if ((columnId !== undefined) && (filterValue !== ""))
    {
      var column = viewer.getColumn(columnId);
      var cellValue = item[column.field];
      var rowID = item["id"];
      var columnFormatter = column.formatter;

      // Reformatting the cell value could potentially be quite exensive!
      // This may require some re-thinking.
      // jenkinsd 2013.04.30
      if (columnFormatter)
      {
        var cell = viewer.getGrid().getColumnIndex(column.id);
        var row = viewer.getDataView().getIdxById(rowID);
        var formattedCellValue =
            columnFormatter(row, cell, cellValue, column, item);

        cellValue = formattedCellValue && $(formattedCellValue).text
            ? $(formattedCellValue).text() : formattedCellValue;
      }

      filterValue = $.trim(filterValue);
      var negate = filterValue.indexOf("!") == 0;

      if (negate)
      {
        filterValue = filterValue.substring(1);
      }

      var filterOut = viewer.valueFilters(filterValue, cellValue,
                                          column.datatype
                                              ? column.datatype : "char");

      if ((!negate && filterOut) || (!filterOut && negate))
      {
        return false;
      }
    }
  }

  return true;
};

/**
 * @param filter    The filter value as entered by the user.
 * @param value     The value to be filtered or not
 * @param datatype  The column's datatype.
 * @returns {Boolean} true if value is filtered-out by filter.
 */
cadc.vot.Viewer.prototype.valueFilters = function (filter, value, datatype)
{
  var operator = '';
  filter = $.trim(filter);

  // determine the operator and filter value
  if (filter.indexOf('= ') == 0)
  {
    filter = filter.substring(2);
  }
  else if (filter.indexOf('=') == 0)
  {
    filter = filter.substring(1);
  }
  else if (filter.indexOf('>= ') == 0)
  {
    filter = filter.substring(3);
    operator = 'ge';
  }
  else if (filter.indexOf('>=') == 0)
  {
    filter = filter.substring(2);
    operator = 'ge';
  }
  else if (filter.indexOf('<= ') == 0)
  {
    filter = filter.substring(3);
    operator = 'le';
  }
  else if (filter.indexOf('<=') == 0)
  {
    filter = filter.substring(2);
    operator = 'le';
  }
  else if (filter.indexOf('> ') == 0)
  {
    filter = filter.substring(2);
    operator = 'gt';
  }
  else if (filter.indexOf('>') == 0)
  {
    filter = filter.substring(1);
    operator = 'gt';
  }
  else if (filter.indexOf('< ') == 0)
  {
    filter = filter.substring(2);
    operator = 'lt';
  }
  else if (filter.indexOf('<') == 0)
  {
    filter = filter.substring(1);
    operator = 'lt';
  }
  else if (filter.indexOf('..') > 0)
  {
    // filter on the range and return
    var dotIndex = filter.indexOf('..');
    var left = filter.substring(0, dotIndex);
    if ((dotIndex) + 2 < filter.length)
    {
      var right = filter.substring(dotIndex + 2);

      if (viewer.areNumbers(value, left, right))
      {
        return ((parseFloat(value) < parseFloat(left))
            || (parseFloat(value) > parseFloat(right)));
      }
      else
      {
        return ((value < left) || (value > right));
      }
    }
  }

  // act on the operator and value
  value = $.trim(value);
  if (operator === 'gt')
  {
    // greater than operator
    if (viewer.areNumbers(value, filter))
    {
      return parseFloat(value) <= parseFloat(filter);
    }
    else if (viewer.areStrings(value, filter))
    {
      return value.toUpperCase() <= filter.toUpperCase();
    }
    else
    {
      return value <= filter;
    }
  }
  else if (operator == 'lt')
  {
    // less-than operator
    if (viewer.areNumbers(value, filter))
    {
      return parseFloat(value) >= parseFloat(filter);
    }
    else if (viewer.areStrings(value, filter))
    {
      return value.toUpperCase() >= filter.toUpperCase();
    }
    else
    {
      return value >= filter;
    }
  }
  else if (operator == 'ge')
  {
    // greater-than or equals operator
    if (viewer.areNumbers(value, filter))
    {
      return parseFloat(value) < parseFloat(filter);
    }
    else if (viewer.areStrings(value, filter))
    {
      return value.toUpperCase() < filter.toUpperCase();
    }
    else
    {
      return value < filter;
    }
  }
  else if (operator == 'le')
  {
    // less-than or equals operator
    if (viewer.areNumbers(value, filter))
    {
      return parseFloat(value) > parseFloat(filter);
    }
    else if (viewer.areStrings(value, filter))
    {
      return value.toUpperCase() > filter.toUpperCase();
    }
    else
    {
      return value > filter;
    }
  }
  else
  {
    // equals operator
    if (filter.indexOf('*') > -1)
    {
      // wildcard match (Replace all instances of '*' with '.*')
      filter = filter.replace(/\*/g, ".*");

      var regex = new RegExp("^" + filter + "$", "gi");
      var result = value.match(regex);

      return (!result || result.length == 0);
    }
    else
    {
      // plain equals match
      if (viewer.areNumbers(value, filter))
      {
        return (parseFloat(value) != parseFloat(filter));
      }
      else if (viewer.areStrings(value, filter))
      {
        return (value.toUpperCase() !== filter.toUpperCase());
      }
      else
      {
        return (value !== filter);
      }
    }
  }

};

cadc.vot.Viewer.prototype.isFloatDatatype = function (datatype)
{
  return (datatype
      && ((datatype == "float") || (datatype == "double")));
};

cadc.vot.Viewer.prototype.isIntegerDatatype = function (datatype)
{
  return (datatype
      && ((datatype == "int") || (datatype == "short")
      || (datatype == "long")));
};

cadc.vot.Viewer.prototype.isNumericDatatype = function (datatype)
{
  return (viewer.isFloatDatatype(datatype)
      || viewer.isIntegerDatatype(datatype));
};

cadc.vot.Viewer.prototype.areNumbers = function ()
{
  for (var i = 0; i < arguments.length; i++)
  {
    if (isNaN(arguments[i]))
    {
      return false;
    }
  }
  return true;
};

cadc.vot.Viewer.prototype.areStrings = function ()
{
  for (var i = 0; i < arguments.length; i++)
  {
    if (!(arguments[i].substring))
    {
      return false;
    }
  }
  return true;
};

/**
 * Check if this Viewer contains the given column.  Used to stop duplicate
 * checkbox columns being added.
 *
 * @return  boolean True if the viewer has the given column, false otherwise.
 */
cadc.vot.Viewer.prototype.hasColumn = function (columnDefinition)
{
  var cols = viewer.getColumns();

  for (var col in cols)
  {
    var nextCol = cols[col];

    if (nextCol.id && (nextCol.id == columnDefinition.id))
    {
      return true;
    }
  }

  return false;
};


/**
 * Initialize this VOViewer.
 */
cadc.vot.Viewer.prototype.init = function ()
{
  var dataView = new Slick.Data.DataView({ inlineFilters: true });
  var forceFitMax = (viewer.getColumnManager().forceFitColumns
                         && viewer.getColumnManager().forceFitColumnMode
      && (viewer.getColumnManager().forceFitColumnMode
      == "max"));
  var checkboxSelector;

  if (Slick.CheckboxSelectColumn)
  {
    checkboxSelector = new Slick.CheckboxSelectColumn({
                                                        cssClass: "slick-cell-checkboxsel"
                                                      });

    var checkboxColumn = checkboxSelector.getColumnDefinition();
    var colsToCheck = (viewer.getDisplayColumns().length == 0)
        ? viewer.getColumns() : viewer.getDisplayColumns();

    var checkboxColumnIndex = -1;

    $.each(colsToCheck, function (index, val)
    {
      if (checkboxColumn.id == val.id)
      {
        checkboxColumnIndex = index;
      }
    });

    if (checkboxColumnIndex < 0)
    {
      viewer.getColumns().splice(0, 0, checkboxColumn);
      viewer.getDisplayColumns().splice(0, 0, checkboxColumn);
    }
    else
    {
      viewer.getColumns()[checkboxColumnIndex] = checkboxColumn;
      viewer.getDisplayColumns()[checkboxColumnIndex] = checkboxColumn;
    }
  }
  else
  {
    checkboxSelector = null;
  }

  viewer.getOptions().defaultFormatter = function (row, cell, value, columnDef, dataContext)
  {
    var returnValue;

    if (value == null)
    {
      returnValue = "";
    }
    else
    {
      returnValue = value.toString().replace(/&/g, "&amp;").
          replace(/</g, "&lt;").replace(/>/g, "&gt;");
    }

    return "<span class='cellValue " + columnDef.id
               + "' title='" + returnValue + "'>" + returnValue + "</span>";
  };

  var grid = new Slick.Grid(viewer.getTargetNodeSelector(),
                            dataView, viewer.getDisplayColumns(),
                            viewer.getOptions());

  if (checkboxSelector && Slick.RowSelectionModel)
  {
    grid.setSelectionModel(
        new Slick.RowSelectionModel({
                                      selectActiveRow: viewer.getOptions().selectActiveRow
                                    }));

    grid.registerPlugin(checkboxSelector);
  }

  if (viewer.usePager())
  {
    var pager = new Slick.Controls.Pager(dataView, grid,
                                         $(viewer.getPagerNodeSelector()));
  }
  else
  {
    // Use the Grid header otherwise.
    var gridHeaderLabel = $("#grid-header-label");

    if (gridHeaderLabel)
    {
      dataView.onPagingInfoChanged.subscribe(function (e, pagingInfo)
                                             {
                                               gridHeaderLabel.text("Showing " + pagingInfo.totalRows
                                                                        + " rows (" + viewer.getGridData().length
                                                                        + " before filtering)");
                                             });
    }
  }

  var columnPickerConfig = viewer.getColumnManager().picker;

  if (columnPickerConfig)
  {
    var columnPicker;
    var pickerStyle = columnPickerConfig.style;

    if (pickerStyle == "header")
    {
      columnPicker = new Slick.Controls.ColumnPicker(viewer.getColumns(),
                                                     grid, viewer.getOptions());
    }
    else if (pickerStyle == "tooltip")
    {
      columnPicker = new Slick.Controls.PanelTooltipColumnPicker(viewer.getColumns(),
                                                          grid,
                                                          columnPickerConfig.panel,
                                                          columnPickerConfig.options,
                                                          viewer.getOptions());
    }
    else
    {
      columnPicker = null;
    }
  }

  if (forceFitMax)
  {
    var totalWidth = 0;
    var gridColumns = grid.getColumns();

    for (var c in gridColumns)
    {
      var nextCol = gridColumns[c];
      totalWidth += nextCol.width;
    }

    $(viewer.getTargetNodeSelector()).css("width", totalWidth + "px");

    if (viewer.usePager())
    {
      $(viewer.getPagerNodeSelector()).css("width", totalWidth + "px");
    }

    $(viewer.getHeaderNodeSelector()).css("width", totalWidth + "px");
    grid.resizeCanvas();

    if (columnPicker)
    {
      // For when the column picker hides or shows columns.
      columnPicker.onColumnAddOrRemove.subscribe(function (e, args)
                                                 {
                                                   var g = args.grid;
                                                   var gridColumns = g.getColumns();
                                                   var totalWidth = 0;
                                                   var tabData =
                                                       viewer.getVOTableData();

                                                   for (var c in gridColumns)
                                                   {
                                                     var col = gridColumns[c];
                                                     var colWidth;

                                                     // Do not calculate with checkbox column.
                                                     if (!checkboxSelector
                                                         || (col.id != checkboxSelector.getColumnDefinition().id))
                                                     {
                                                       var colOpts = viewer.getOptionsForColumn(col.name);
                                                       var minWidth = col.name.length + 3;
                                                       var longestCalculatedWidth = tabData.getLongestValueLength(col.id);
                                                       var textWidthToUse = (longestCalculatedWidth > minWidth)
                                                           ? longestCalculatedWidth : minWidth;

                                                       var lengthDiv = $("<div></div>");
                                                       var lengthStr = "";
                                                       var userColumnWidth = colOpts.width;

                                                       for (var v = 0; v < textWidthToUse; v++)
                                                       {
                                                         lengthStr += "a";
                                                       }

                                                       lengthDiv.attr("style", "position: absolute;visibility: hidden;height: auto;width: auto;");
                                                       lengthDiv.text(lengthStr);
                                                       $(document.body).append(lengthDiv);

                                                       colWidth = (userColumnWidth || lengthDiv.innerWidth());
                                                     }
                                                     else
                                                     {
                                                       // Buffer the checkbox.
                                                       colWidth = col.width + 15;
                                                     }

                                                     totalWidth += colWidth;
                                                   }

                                                   if (totalWidth > 0)
                                                   {
                                                     $(viewer.getTargetNodeSelector()).css("width", totalWidth + "px");

                                                     if (viewer.usePager())
                                                     {
                                                       $(viewer.getPagerNodeSelector()).css("width", totalWidth + "px");
                                                     }

                                                     $(viewer.getHeaderNodeSelector()).css("width", totalWidth + "px");
                                                     g.resizeCanvas();
                                                   }
                                                 });
    }
  }

  // move the filter panel defined in a hidden div into grid top panel
  $("#inlineFilterPanel").appendTo(grid.getTopPanel()).show();

  grid.onCellChange.subscribe(function (e, args)
                              {
                                dataView.updateItem(args.item.id, args.item);
                              });

  grid.onKeyDown.subscribe(function (e)
                           {
                             // select all rows on ctrl-a
                             if ((e.which != 65) || !e.ctrlKey)
                             {
                               return false;
                             }

                             var rows = [];
                             for (var i = 0; i < dataView.getLength(); i++)
                             {
                               rows.push(i);
                             }

                             grid.setSelectedRows(rows);
                             e.preventDefault();

                             return true;
                           });

  grid.onSort.subscribe(function (e, args)
                        {
                          sortAsc = args.sortAsc;
                          sortcol = args.sortCol.field;

                          if ($.browser.msie && ($.browser.version <= 8))
                          {
                            // use numeric sort of % and lexicographic for everything else
                            dataView.fastSort(sortcol, args.sortAsc);
                          }
                          else
                          {
                            // using native sort with comparer
                            // preferred method but can be very slow in IE with huge datasets
                            dataView.sort(viewer.comparer, args.sortAsc);
                          }
                        });

  // wire up model events to drive the grid
  dataView.onRowCountChanged.subscribe(function (e, args)
                                       {
                                         grid.updateRowCount();
                                         grid.render();
                                       });

  dataView.onRowsChanged.subscribe(function (e, args)
                                   {
                                     grid.invalidateRows(args.rows);
                                     grid.render();
                                   });

  dataView.onPagingInfoChanged.subscribe(function (e, pagingInfo)
                                         {
                                           var isLastPage =
                                               (pagingInfo.pageNum == pagingInfo.totalPages - 1);
                                           var enableAddRow =
                                               (isLastPage || pagingInfo.pageSize == 0);
                                           var options = grid.getOptions();

                                           if (options.enableAddRow != enableAddRow)
                                           {
                                             grid.setOptions({enableAddRow: enableAddRow});
                                           }
                                         });

  $(window).resize(function ()
                   {
                     grid.resizeCanvas();
                   });

  $("#btnSelectRows").click(function ()
                            {
                              if (!Slick.GlobalEditorLock.commitCurrentEdit())
                              {
                                return;
                              }

                              var rows = [];
                              for (var i = 0; (i < 10)
                                  && (i < dataView.getLength());
                                   i++)
                              {
                                rows.push(i);
                              }

                              grid.setSelectedRows(rows);
                            });


  var columnFilters = viewer.getColumnFilters();

  $(grid.getHeaderRow()).delegate(":input", "change keyup",
                                  function (e)
                                  {
                                    var columnId = $(this).data("columnId");
                                    if (columnId != null)
                                    {
                                      columnFilters[columnId] =
                                      $.trim($(this).val());
                                      dataView.refresh();
                                    }
                                  });

  grid.onHeaderRowCellRendered.subscribe(function (e, args)
                                         {
                                           $(args.node).empty();

                                           // Do not display for the checkbox column.
                                           if (!checkboxSelector
                                               || (args.column.id
                                               != checkboxSelector.getColumnDefinition().id))
                                           {
                                             var datatype =
                                                 args.column.datatype;
                                             var tooltipTitle;

                                             if (viewer.isNumericDatatype(datatype))
                                             {
                                               tooltipTitle = "Number: 10 or >=10 or 10..20 for a range , ! to negate";
                                             }
                                             else
                                             {
                                               tooltipTitle = "String: abc (exact match) or *ab*c* , ! to negate";
                                             }

                                             $("<input type='text'>")
                                                 .data("columnId", args.column.id)
                                                 .val(columnFilters[args.column.id])
                                                 .attr("title", tooltipTitle)
                                                 .appendTo(args.node);
                                           }
                                         });

  if (Slick.Plugins && Slick.Plugins.UnitSelection)
  {
    var unitSelectionPlugin = new Slick.Plugins.UnitSelection();

    // Extend the filter row to include the pulldown menu.
    $(".slick-headerrow-columns").css("height", "42px");

    unitSelectionPlugin.onUnitChange.subscribe(function (e, args)
                                               {
                                                 $(args.column).data("unitValue", args.unitValue);

                                                 // Invalidate to force column
                                                 // reformatting.
                                                 grid.invalidate();
                                               });

    grid.registerPlugin(unitSelectionPlugin);

    grid.onColumnsReordered.subscribe(function (e, args)
                                      {
                                        grid.invalidate();
                                      });
  }

  viewer.setDataView(dataView);
  viewer.setGrid(grid);
  viewer.sort();
};

/**
 * Load a fresh copy into this viewer.  This assumes first time load.
 *
 * @param voTable         The built VOTable.
 * @param refreshColumns  Whether to refresh the columns (true/false).
 * @param refreshData     Whether to refresh the data (true/false).
 */
cadc.vot.Viewer.prototype.load = function (voTable, refreshColumns, refreshData)
{
  // Use the first Table of the first Resource only.
  var resource = voTable.getResources()[0];

  if (!resource)
  {
    throw new Error("No resource available.");
  }

  var table = resource.getTables()[0];

  if (!table)
  {
    throw new Error("No table available.");
  }

  viewer.setVOTableData(table.getTableData());

  if (refreshColumns)
  {
    this.refreshColumns(table);
  }

  if (refreshData)
  {
    this.refreshData(table);
  }
};

/**
 * Refresh this VOViewer's columns.
 *
 * @param table   A Table in the VOTable.
 */
cadc.vot.Viewer.prototype.refreshColumns = function (table)
{
  viewer.clearColumns();
  var tableData = table.getTableData();
  var columnManager = viewer.getColumnManager();
  var forceFitMax = (columnManager.forceFitColumns
                         && columnManager.forceFitColumnMode
      && (columnManager.forceFitColumnMode == "max"));

  $.each(table.getFields(), function (fieldIndex, field)
  {
    var fieldKey = field.getID();
    var colOpts = viewer.getOptionsForColumn(fieldKey);
    var cssClass = colOpts.cssClass;
    var datatype = field.getDatatype();
    var columnProperties =
    {
      id: fieldKey,
      name: field.getName(),
      field: fieldKey,
      formatter: colOpts.formatter,
      cssClass: cssClass,
      resizable: viewer.getColumnManager().resizable
    };

    if (field.getLabel() != 'Preview')
    {
      columnProperties.sortable = true;
    }

    if (datatype)
    {
      columnProperties.datatype = datatype;
    }

    columnProperties.header = colOpts.header;

    if (forceFitMax)
    {
      // Buffer the length of the column header.
      var minWidth = colOpts.width ? colOpts.width : (field.getLabel().length + 3);
      var longestCalculatedWidth = tableData.getLongestValueLength(fieldKey);
      var textWidthToUse = (longestCalculatedWidth > minWidth)
          ? longestCalculatedWidth : minWidth;
      var lengthDiv = $("<div></div>");
      var lengthStr = "";
      var userColumnWidth = colOpts.width;

      for (var v = 0; v < textWidthToUse; v++)
      {
        lengthStr += "a";
      }

      lengthDiv.attr("style", "position: absolute;visibility: hidden;height: auto;width: auto;");
      lengthDiv.text(lengthStr);
      $(document.body).append(lengthDiv);

      columnProperties.width = userColumnWidth || lengthDiv.innerWidth();
    }
    // Here to handle XTypes like the adql:timestamp xtype.
    else if (field.getXType() && field.getXType().match(/timestamp/i))
    {
      columnProperties.width = 140;
    }

    viewer.addColumn(columnProperties);
  });
};

cadc.vot.Viewer.prototype.updateGridColumns = function ()
{
  viewer.getGrid().setColumns(viewer.getDisplayColumns().slice(0));
  new Slick.Controls.ColumnPicker(viewer.getColumns().slice(0),
                                  viewer.getGrid(),
                                  viewer.getOptions());
};

/**
 * Clean refresh of the data rows.
 *
 * @param table   A Table element from a VOTable.
 */
cadc.vot.Viewer.prototype.refreshData = function (table)
{
  viewer.clearRows();

  // Make a copy of the array so as not to disturb the original.
  var allRows = table.getTableData().getRows().slice(0);

  $.each(allRows, function (rowIndex, row)
  {
    var d = {};

    d["id"] = row.getID();
    $.each(row.getCells(), function (cellIndex, cell)
    {
      var cellFieldID = cell.getField().getID();
      d[cellFieldID] = cell.getValue();
    });

    viewer.addRow(d, rowIndex);
  });
};


cadc.vot.Viewer.prototype.render = function ()
{
  var dataView = viewer.getDataView();
  var grid = viewer.getGrid();

  grid.init();

  // initialize the model after all the events have been hooked up
  dataView.beginUpdate();

  dataView.setItems(viewer.getGridData());
//  dataView.setFilterArgs({
//                           searchString: searchString
//                         });
  dataView.setFilter(viewer.searchFilter);
  dataView.endUpdate();

  if (grid.getSelectionModel())
  {
    // If you don't want the items that are not visible (due to being filtered out
    // or being on a different page) to stay selected, pass 'false' to the second arg
    dataView.syncGridSelection(grid, true);
  }

  var gridContainer = $(viewer.getTargetNodeSelector());

  if (gridContainer.resizable && viewer.getOptions().gridResizable)
  {
    gridContainer.resizable();
  }
};
