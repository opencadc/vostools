(function ($)
{
  function SlickColumnPicker(columns, grid, options)
  {
    var $menu;
    var columnCheckboxes;
    var self = this;

    var defaults = {
      fadeSpeed: 250
    };

    function init()
    {
      grid.onHeaderContextMenu.subscribe(handleHeaderContextMenu);
      options = $.extend({}, defaults, options);

      $menu = $("<span class='slick-columnpicker' style='display:none;position:absolute;z-index:20;' />").appendTo(document.body);

      $menu.bind("mouseleave", function (e)
      {
        $(this).fadeOut(options.fadeSpeed)
      });
      $menu.bind("click", updateColumn);

    }

    function handleHeaderContextMenu(e, args)
    {
      e.preventDefault();
      $menu.empty();
      columnCheckboxes = [];

      var $li, $input;
      for (var i = 0; i < columns.length; i++)
      {
        $li = $("<li />").appendTo($menu);
        $input = $("<input type='checkbox' />").data("column-id", columns[i].id);
        columnCheckboxes.push($input);

        if (grid.getColumnIndex(columns[i].id) != null)
        {
          $input.attr("checked", "checked");
        }

        $("<label />")
            .text(columns[i].name)
            .prepend($input)
            .appendTo($li);
      }

      $("<hr/>").appendTo($menu);
      $li = $("<li />").appendTo($menu);
      $input = $("<input type='checkbox' />").data("option", "autoresize");
      $("<label />")
          .text("Force fit columns")
          .prepend($input)
          .appendTo($li);
      if (grid.getOptions().forceFitColumns)
      {
        $input.attr("checked", "checked");
      }

      $li = $("<li />").appendTo($menu);
      $input = $("<input type='checkbox' />").data("option", "syncresize");
      $("<label />")
          .text("Synchronous resize")
          .prepend($input)
          .appendTo($li);
      if (grid.getOptions().syncColumnCellResize)
      {
        $input.attr("checked", "checked");
      }

      $menu
          .css("top", e.pageY - 10)
          .css("left", e.pageX - 10)
          .fadeIn(options.fadeSpeed);
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
      var currentVisibleColumns = grid.getColumns();

      if ($(e.target).data("option") == "autoresize")
      {
        if (e.target.checked)
        {
          grid.setOptions({forceFitColumns: true});
          grid.autosizeColumns();
        }
        else
        {
          grid.setOptions({forceFitColumns: false});
        }
      }
      else if ($(e.target).data("option") == "syncresize")
      {
        if (e.target.checked)
        {
          grid.setOptions({syncColumnCellResize: true});
        }
        else
        {
          grid.setOptions({syncColumnCellResize: false});
        }
      }
      else if ($(e.target).is(":checkbox"))
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
            var addAction = $.grep(visibleColumns, function(col, colIndex)
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

  // Slick.Controls.ColumnPicker
  $.extend(true, window,
           {
             Slick:
             {
               Controls:
               {
                 ColumnPicker: SlickColumnPicker
               }
             }
           });
})(jQuery);
