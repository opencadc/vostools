/**
 * @author dhinshaw
 */

/**
 * Creates a new voview object for displaying a VOTABLE.
 * 
 * @name voview
 * @constructor
 * 
 * @param {Object} input Parameters to be passed to the votable object. One of
 *            the properties: tree, string, url, form must be specified.
 * @param {XML DOM Object} [input.tree] XML DOM Object of a VOTABLE.
 * @param {string} [input.string] String containing VOTABLE.
 * @param {string} [input.url] URL pointing to a VOTABLE.
 * @param {string} [input.form] Name of a form used for specifying the VOTABLE.
 * @param {HTML DOM Element} [input.searchparam] Name of input element in the
 *            form containing the VOTABLE. Required if input.form is specified.
 * 
 * @param {string} xsltdir Directory on the server to look for XSLT files.
 * 
 * @param {string} objectName The name of the global variable pointing to this
 *            instance of the voview class. This will be used for referencing
 *            the proper JavaScript calls when rendering the HTML table.
 * 
 * @param {string} widgetIDprefix Prefix for the HTML attribute IDs where the
 *            various sub-widgets of the HTML table display will be placed. For
 *            example, when displaying the main table containing the data, the
 *            HTML element with an ID of {prefix}.table would be searched for,
 *            and the table placed at this location in the HTML document. See
 *            the documentation on voview.renderer.render for the list of
 *            sub-widgets.
 */

/**
 * Find the index number for a column in the VOTABLE, based on the column (i.e.
 * FIELD) name.
 * 
 * @name findColumnByName
 * @function
 * @memberOf voview.prototype
 * 
 * @param {Regex} name A regular expression to match against the column name.
 * 
 * @returns {integer} Number corresponding to the position of the column in the
 *          original VOTABLE.
 */

/**
 * Find the index number for a column in the VOTABLE, based on the column (i.e.
 * FIELD) UCD.
 * 
 * @name findColumnByUCD
 * @function
 * @memberOf voview.prototype
 * 
 * @param {Regex} ucd A regular expression to match against the column UCD.
 * 
 * @returns {integer} Number corresponding to the position of the column in the
 *          original VOTABLE.
 */

/**
 * Find the indexes of the rows in the original VOTABLE which match the
 * selection criteria.
 * 
 * @name findRows
 * @function
 * @memberOf voview.prototype
 * 
 * @param {string|function} selectCriteria If a string, use it to match against
 *            the contents of the VOTABLE row. The contents of the VOTABLE XML
 *            row will be searched and the row will be selected if it contains
 *            the input string. If a function, then a function which will be
 *            called for each row in the XML VOTABLE, with an XML DOM object of
 *            the row as its only argument. The function should return a boolean
 *            indicating whether the row should be selected or not.
 * 
 * @returns {integer[]} An array of indexes which correspond to the positions of
 *          the rows in the original VOTABLE.
 */

/**
 * Return XML DOM objects for the specified VOTABLE rows.
 * 
 * @name getRowNodes
 * @function
 * @memberOf voview.prototype
 * 
 * @param {integer[]} rows An array of indexes which correspond to the positions
 *            of the rows in the original VOTABLE.
 * 
 * @returns {XML DOM Object[]} An array of XML DOM objects, each of which
 *          corresponds to a row of the original VOTABLE.
 */

/**
 * Get the total number of rows in the original VOTABLE.
 * 
 * @name getTotalRows
 * @function
 * @memberOf voview.prototype
 * 
 * @returns {integer} The total number of rows in the original VOTABLE.
 */

// *******************************************************************************
/**
 * Takes an XML VOTABLE, and renders it as a single page HTML table.
 * 
 * @name renderer
 * @constructor
 * @memberOf voview
 * 
 * @param {voview.filter} filter The filter object to be used with the renderer.
 */

/**
 * Generate the HTML for the entire VOView display, or for some part
 * (sub-widget) of the display.
 * 
 * @name render
 * @function
 * @memberOf voview.renderer.prototype
 * 
 * @param {string} subwidget The part of the VOView display to generate. If
 *            undefined or set to "all", the entire VOView display is generated.
 *            Available sub-widget arguments are: "title", "paging",
 *            "rowSelection", "table", "columnArranging" and "Parameters".
 * 
 * @returns {HTML DOM Object} The DOM document fragment for the sub-widget.
 */

/**
 * Set the title of the table display. If not set, VOView tries to determine a
 * suitable title on its own.
 * 
 * @name setTitle
 * @function
 * @memberOf voview.renderer.prototype
 * 
 * @param {string} [titletext] Text for the title.
 */

/**
 * Set a function to call when ever the table display is updated.
 * 
 * @name setUpdateCallBack
 * @function
 * @memberOf voview.renderer.prototype
 * 
 * @param {function} update Function to be called when the display is updated.
 *            The function is called with no arguments.
 */

/**
 * Set rows which are selected, i.e. they are shown as selected when the table
 * is initially displayed.
 * 
 * @name setSelectedRows
 * @function
 * @memberOf voview.renderer.prototype
 * 
 * @param {integers[]|function} rows If an array of integers, contains a list of
 *            row numbers. The numbers correspond to the row numbers of the
 *            original VOTABLE. If a function, then a function which will be
 *            called for each row in the XML VOTABLE, with an XML DOM object of
 *            the row as its only argument. The function should return a boolean
 *            indicating whether the row should be selected or not.
 */

/**
 * Return an array listing the rows of the table currently selected.
 * 
 * @name getSelectedRows
 * @function
 * @memberOf voview.renderer.prototype
 * 
 * @returns {integers[]} rows An array of integers containing a list of selected
 *          row numbers. The numbers correspond to the row numbers of the
 *          original VOTABLE.
 */

/**
 * Set functions to be called when a row is either selected or unselected.
 * 
 * @name setSelectCallBack
 * @function
 * @memberOf voview.renderer.prototype
 * 
 * @param {function} add Function to be called when a row is selected. The
 *            function is called with HTML DOM object for the row as its
 *            argument.
 * @param {function} del Function to be called when a row is unselected. The
 *            function is called with HTML DOM object for the row as its
 *            argument.
 */

/**
 * Display an additional column in the HTML table at the designated location.
 * This column can be filled in by the application user.
 * 
 * @name addColumn
 * @function
 * @memberOf voview.renderer.prototype
 * 
 * @param {integer} position Integer specifying the position of the column in
 *            the HTML table. A value of 0 indicates a column at the beginning
 *            of the table, a value of -1 indicates a column at the end of the
 *            table.
 * @param {function} formatter A function to be called to set the value to be
 *            placed in the cells of the column. The function is called once for
 *            each row of the HTML table. The function is called with two
 *            arguments. The first argument is the HTML DOM object for the row,
 *            and the second argument is the HTML DOM object for the entire
 *            table.
 */

/**
 * This activates VOView functionality for tracking and manipulating row
 * selection. It displays a column at the beginning of the table with checkboxes
 * for selecting rows.
 * 
 * @name setEnableRowSelection
 * @function
 * @memberOf voview.renderer.prototype
 * 
 * @param {boolean} display If true, enable row selection.
 */

/**
 * Add additional formatting to a column. A function can be called when
 * formatting the column, which can be used for formatting other parts of the
 * row as well.
 * 
 * @name formatColumn
 * @function
 * @memberOf voview.renderer.prototype
 * 
 * @param {string|integer|Regex} column Column to be formatted. This can be
 *            specified either as: 1) an integer (the column number in the
 *            original order); 2) a string matching a substring in the column
 *            name; 3) a regular expression matching the column name.
 * 
 * @param {string|function} format The formatting information for the column.
 *            This can be either: 1) a string, in which case it will replace the
 *            current value of each of the cells of the column. If the string
 *            contains "@@", it will be replaced by the current cell value; 2) a
 *            function, in which case the function will be called for each row
 *            in the column. The function is called with two arguments. The
 *            first argument is the HTML DOM object for the cell, and the second
 *            argument is the HTML DOM object for the entire row.
 */

/**
 * Set the order and number of columns to display.
 * 
 * @name setDisplayedColumns
 * @function
 * @memberOf voview.renderer.prototype
 * 
 * @param {integer[]|string[]|integer} columnorder If an array, contains a list
 *            of columns in the order in which they are to appear. If an integer
 *            array, the numbers correspond to the original order of the columns
 *            in the VOTABLE. If a string array, the strings must correspond to
 *            column names. If a scalar integer, then this value sets the
 *            maximum number of columns to initially display in the HTML table.
 */

// *******************************************************************************
/**
 * Takes an XML VOTABLE, and creates an object for doing sorting, filtering and
 * paging.
 * 
 * @name filter
 * @constructor
 * @memberOf voview
 * 
 * @param {XML DOM Object} votable The VOTABLE to be filtered.
 * @param {function} resultCallback Function to call when the result of the filtering
 *                   is completed.  The one argument to this function is an XML DOM object 
 *                   of the filtered VOTABLE.
 */

/**
 * Produces a single page's worth of VOTABLE data.  After filtering is complete, the
 * resultCallback function is called with the results.
 * 
 * @name doFilter
 * @function
 * @memberOf voview.filter.prototype
 * 
 * @param {function} resultCallback Function to call when the result of the filtering
 *                   is completed.  The one argument to this function is an XML DOM object 
 *                   of the filtered VOTABLE. If omitted then use function specified in
 *                   constructor.
 */

/**
 * Set range of rows to be extracted from the VOTABLE.
 * 
 * @name setRowRange
 * @function
 * @memberOf voview.filter.prototype
 * 
 * @param {integer} firstRow The first row of the range.
 * 
 * @param {integer} lastRow The last row of the range.
 */

/**
 * Set the columns to use for sorting the table, and the sorting direction for
 * each column.
 * 
 * @name setSortColumns
 * @function
 * @memberOf voview.filter.prototype
 * 
 * @param {sortColumnKey[]} sortKeys An array of type sortColumnKey. The first
 *            key in the array has the highest precedence.
 */

/**
 * Set the criteria which determines which rows will be selected when the
 * "select all" button is activated.
 * 
 * @name setSelectRows
 * @function
 * @memberOf voview.filter.prototype
 * 
 * @param {string|function} selectCriteria If a string, use it to match against
 *            the contents of the VOTABLE row. The contents of the VOTABLE XML
 *            row will be searched and the row will be selected if it contains
 *            the input string. If a function, then a function which will be
 *            called for each row in the XML VOTABLE, with an XML DOM object of
 *            the row as its only argument. The function should return a boolean
 *            indicating whether the row should be selected or not.
 */

/**
 * Clear any column value filters currently set on the VOTABLE.
 * 
 * @name clearColumnFilters
 * @function
 * @memberOf voview.filter.prototype
 */

/**
 * Set column value filters on the VOTABLE. Any filters all ready set on columns
 * not specified in the filterKeys are retained.
 * 
 * @name setColumnFilters
 * @function
 * @memberOf voview.filter.prototype
 * 
 * @param {columnFilterKey[]} filterKeys An array of type columnFilterKey.
 */

// *******************************************************************************
/**
 * An object for specify the data needed for sorting the table by a column
 * value.
 * 
 * @name sortColumnKey
 * @constructor
 * @memberOf voview
 */

/**
 * The column to use for sorting the table. If a string, specifies the name of
 * the column. If an integer, the number corresponds to the column in the
 * original order of the columns in the VOTABLE.
 * 
 * @name column
 * @field
 * @memberOf voview.sortColumnKey.prototype
 * @type {string|integer}
 */

/**
 * Sort direction. Either "ascending" or "descending".
 * 
 * @name direction
 * @field
 * @memberOf voview.sortColumnKey.prototype
 * @type {string}
 */

// *******************************************************************************
/**
 * An object for specifying the data needed for filtering the table by the
 * values in a column.
 * 
 * @name columnFilterKey
 * @constructor
 * @memberOf voview
 */

/**
 * The column to use for filtering the table. If a string, specifies the name of
 * the column. If an integer, the number corresponds to the column in the
 * original order of the columns in the VOTABLE.
 * 
 * @name column
 * @field
 * @memberOf voview.columnFilterKey.prototype
 * @type {string|integer}
 */

/**
 * The filtering expression to be applied to the column.
 * 
 * @name expression
 * @field
 * @memberOf voview.columnFilterKey.prototype
 * @type {string}
 */

/**
 * Indicates if the column is a Character type, rather than numerically valued.
 * 
 * @name isCharType
 * @field
 * @memberOf voview.columnFilterKey.prototype
 * @type {boolean}
 */

// ************************************************************************************
/**
 * A container class for storing state information of a voview object that is
 * needed by both the filter and renderer objects.
 * 
 * @constructor
 * @memberOf voview
 */

