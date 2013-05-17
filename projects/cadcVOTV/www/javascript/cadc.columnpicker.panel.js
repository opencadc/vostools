(function ($)
{
  /**
   * New Panel column picker.
   *
   * @param columns   The columns to put.
   * @param grid      The underlying Grid.
   * @param panel     The panel to put the link in.
   * @param tooltipOptions    The options to be passed to the tooltip.
   * @param options   Optional items.
   * @constructor
   */
  function PanelTooltipColumnPicker(columns, grid, panel, tooltipOptions, options)
  {
    var $link;
    var $menu;
    var columnCheckboxes;
    var self = this;

    if (!jQuery.fn.tooltip)
    {
      throw "CADC Panel Tooltip Column Picker requires a tooltip library "
          + "(jQuery.tools or jQuery.ui) module to be loaded";
    }

    var defaults =
    {
      fadeSpeed: 250,
      linkText: "More columns..."
    };

    var defaultTooltipOptions =
    {
      appendTooltipContent: false,
      targetSelector: ".tooltip_content"
    };

    function init()
    {
      options = $.extend({}, defaults, options);
      tooltipOptions = $.extend({}, defaultTooltipOptions, tooltipOptions);

      $link = $("<a name='slick-columnpicker-panel-link' class='slick-columnpicker-panel-link-label'></a>").appendTo(panel);
      $link.text(options.linkText);
      $link.mouseover(function (e)
                      {
                        buildTooltipPicker(e);
                      });

      // Used to support the outdated jQuery.tools tooltip.
      if (tooltipOptions.appendTooltipContent)
      {
        $(tooltipOptions.tooltipContent).remove();
        $link.after(tooltipOptions.tooltipContent);
      }

      $link.tooltip(tooltipOptions);

      $menu = $("<ul class='slick-columnpicker slick-columnpicker-tooltip' />").appendTo(tooltipOptions.targetSelector);

      $menu.sortable({
                       stop: function(e, ui)
                       {
                         var reorderedListItems =
                             $menu.find("li label[id^='LABEL_']");
                         var reorderedColumns = [];

                         $.each(reorderedListItems, function(index, listItem)
                         {
                           var nextColumnID =
                               $(listItem).attr("id").replace("LABEL_", "");
                           var nextColumnIndex =
                               grid.getColumnIndex(nextColumnID);

                           if (nextColumnIndex != undefined)
                           {
                             reorderedColumns.push(columns[nextColumnIndex]);
                           }
                         });

                         grid.setColumns(reorderedColumns);

                         e.stopPropagation();
                       }
                     });
      $menu.disableSelection();
    }

//    function buildTooltipPicker(e, args)
    function buildTooltipPicker(e)
    {
      $menu.empty();
      columnCheckboxes = [];

      var $li, $input;
      for (var i = 0; i < columns.length; i++)
      {
        $li = $("<li />").appendTo($menu);
        $input = $("<input type='checkbox' />").data("column-id", columns[i].id);
        $input.click(function(e)
                     {
                       updateColumn(e);
                     });
        columnCheckboxes.push($input);

        if (grid.getColumnIndex(columns[i].id) != null)
        {
          $input.attr("checked", "checked");
        }

        var labelText =
            $("<div class='slick-column-picker-label-text'></div>").text(
                columns[i].name);
        var label = $("<label />");
        label.attr("id", "LABEL_" + columns[i].id);

        label.append(labelText);
        label.prepend($input);
        label.appendTo($li);
      }

      $menu.parent().css("top", e.pageY).css("left", e.pageX);
    }

    /**
     * Fire an event.  Taken from the slick.grid Object.
     *
     * @param evt     The Event to fire.
     * @param args    Arguments to the event.
     * @param e       Event data.
     * @returns {*}   The event notification result.
     */
    function trigger(evt, args, e)
    {
      e = e || new Slick.EventData();
      args = args || {};
      args.grid = grid;
      return evt.notify(args, e, self);
    }

    function updateColumn(e)
    {
      if ($(e.target).is(":checkbox"))
      {
        var visibleColumns = [];
        $.each(columnCheckboxes, function (i, e)
        {
          if ($(this).is(":checked"))
          {
            visibleColumns.push(columns[i]);
          }
        });

        if (!visibleColumns.length)
        {
          $(e.target).attr("checked", "checked");
        }
        else
        {
          grid.setColumns(visibleColumns);
          var columnID = $(e.target).data("column-id");
          var column;

          for (var c in columns)
          {
            var col = columns[c];
            if (col.id == columnID)
            {
              column = col;
              break;
            }
          }

          // Should always be true!
          if (column)
          {
            var addAction = $.grep(visibleColumns,function (col, colIndex)
            {
              return (col.id
                  == column.id);
            }).length > 0;

            trigger(self.onColumnAddOrRemove,
                    {
                      "action": addAction ? "add" : "remove",
                      "column": column
                    }, null);
          }
        }
      }
    }

    $.extend(this, {
      "onColumnAddOrRemove": new Slick.Event()
    });

    init();
  }

  // Slick.Controls.PanelTooltipColumnPicker
  $.extend(true, window,
           {
             Slick: {
               Controls: {
                 PanelTooltipColumnPicker: PanelTooltipColumnPicker
               }
             }
           });
})(jQuery);
