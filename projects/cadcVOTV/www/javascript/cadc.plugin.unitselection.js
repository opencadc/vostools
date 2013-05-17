(function ($) {
  // register namespace
  $.extend(true, window, {
    "Slick": {
      "Plugins": {
        "UnitSelection": UnitSelection
      }
    }
  });


  /***
   * A plugin to add unit pulldowns to a separate row of the Grid.
   *
   * USAGE:
   *
   * Add the plugin .js & .css files and register it with the grid.
   *
   * To specify a custom button in a column header, extend the column definition like so:
   *
   *   var columns = [
   *     {
   *       id: 'myColumn',
   *       name: 'My column',
   *
   *       // This is the relevant part
   *       header: {
   *          units: [
   *              {
   *                // unit options
   *              },
   *              {
   *                // unit options
   *              }
   *          ]
   *       }
   *     }
   *   ];
   *
   * Available unit options:
   *    cssClass:     CSS class to add to the pulldown.
   *    handler:      Unit select handler.
   *    items:        The available items.
   *
   * Each item will have:
   *    label:        Label to display.
   *    value:        The value to submit.
   *    default:      Set to true if it's the default item.
   *
   * The plugin exposes the following events:
   *    onUnitChange:    Fired on button click for units with 'command' specified.
   *        Event args:
   *            grid:     Reference to the grid.
   *            column:   Column definition.
   *            unit:     Unit selected.
   *
   *
   * @param options {Object} Options:
   *    unitCssClass:   a CSS class to use for buttons (default 'slick-header-unit-pulldown')
   * @class Slick.Plugins.UnitSelection
   * @constructor
   */
  function UnitSelection(options)
  {
    var _grid;
    var _self = this;
    var _handler = new Slick.EventHandler();
    var _defaults =
    {
      unitPulldownCssClass: "slick-header-unit-pulldown"
    };


    function init(grid)
    {
      options = $.extend(true, {}, _defaults, options);
      _grid = grid;
      _handler
          .subscribe(_grid.onHeaderRowCellRendered, handleHeaderRowCellRendered)
          .subscribe(_grid.onBeforeHeaderRowCellDestroy,
                     handleBeforeHeaderRowCellDestroy);

      // Force the grid to re-render the header now that the events are hooked up.
      _grid.setColumns(_grid.getColumns());
    }

    function destroy()
    {
      _handler.unsubscribeAll();
    }

    function handleHeaderRowCellRendered(e, args)
    {
      var column = args.column;
      var node = $(args.node).append("<br>");

      if (column.header && column.header.units)
      {
        // Append unit pulldowns in reverse order since they are floated to the
        // right.
        var i = column.header.units.length;
        var unitPullDown = $("<select></select>")
            .addClass(options.unitPulldownCssClass)
            .data("column", column);
        var selectedUnit = $(column).data("unitValue");

        while (i--)
        {
          var unitDefinition = column.header.units[i];
          var nextOption = $("<option></option>").appendTo(unitPullDown).text(
              unitDefinition.label).val(unitDefinition.value);

          nextOption.data("unitValue", unitDefinition.value);

          if (selectedUnit && (selectedUnit == unitDefinition.value))
          {
            nextOption.attr("selected", true);
          }
          else if (!selectedUnit && unitDefinition.default)
          {
            $(column).data("unitValue", unitDefinition.value);
            nextOption.attr("selected", true);
          }
        }

        if (unitDefinition.cssClass)
        {
          unitPullDown.addClass(unitDefinition.cssClass);
        }

//          if (unitDefinition.command)
//          {
//            unitPullDown.data("command", unitDefinition.command);
//          }

        if (unitDefinition.handler)
        {
          unitPullDown.data("unitChangeHandler", unitDefinition.handler);
        }

        unitPullDown
            .bind("change", handleUnitChange)
            .appendTo(node);
      }
    }


    function handleBeforeHeaderRowCellDestroy(e, args)
    {
      var column = args.column;

      if (column.header && column.header.units)
      {
        // Removing buttons via jQuery will also clean up any event handlers and data.
        // NOTE: If you attach event handlers directly or using a different framework,
        //       you must also clean them up here to avoid memory leaks.
        $(args.node).find("." + options.unitPulldownCssClass).remove();
      }
    }


    function handleUnitChange(e)
    {
      var columnDef = $(this).data("column");
      var unitPullDown = $(this).data("unitPullDown");
      var unitValue = $(e.target).val();
      var handler = $(this).data("unitChangeHandler");

//      if (command != null)
//      {
      _self.onUnitChange.notify({
                               "grid": _grid,
                               "column": columnDef,
                               "unitValue" : unitValue,
                               "unitPullDown": unitPullDown,
                               "handler": handler
                             }, e, _self);

      // Update the header in case the user updated the button definition in the handler.
      _grid.updateColumnHeader(columnDef.id);
//      }

      // Stop propagation so that it doesn't register as a header click event.
      e.preventDefault();
      e.stopPropagation();
    }

    $.extend(this, {
      "init": init,
      "destroy": destroy,
      "onUnitChange": new Slick.Event()
    });
  }
})(jQuery);
