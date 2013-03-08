/***
 * Contains basic SlickGrid formatters.
 * @module Formatters
 * @namespace Slick
 */

(function ($) {
  // register namespace
  $.extend(true, window, {
    "Slick": {
      "Formatters": {
        "PercentComplete": PercentCompleteFormatter,
        "PercentCompleteBar": PercentCompleteBarFormatter,
        "YesNo": YesNoFormatter,
        "Checkmark": CheckmarkFormatter,
        "Preview": PreviewFormatter,
        "FieldOfView": FieldOfViewPrecisionFormat,
        "CalibrationLevel": CalibrationLevelFormat
      }
    }
  });

  /**
   * Format the numeric value of the given cell.  This will truncate where
   * necessary.
   *
   * @param cell   The cell containing the value.
 * @return {string}
   */
  function FieldOfViewPrecisionFormat(row, cell, value, columnDef, dataContext)
  {
    if (value)
    {
      return formatNumericFixation(value, 4);
    }
    else
    {
      return "";
    }
  }

  function formatNumericFixation(value, fixation)
  {
    var num = new Number(value);
    return num.toFixed(fixation);
  }

  /**
   *
   *
   * @param row
   * @param cell
   * @param value
   * @param columnDef
   * @param dataContext
   * @return {*}
   */
  function CalibrationLevelFormat(row, cell, value, columnDef, dataContext)
  {
    var calLevelMap = search.Hierarchy.getCalibrationLevelMap();

//    if (cell && cell.innerHTML && (cell.innerHTML != ""))
    if (value)
    {
//      var calLevel = $(cell).text();
      return calLevelMap[value];
    }
    else
    {
      return "";
    }
  }

  function encodeParameters(url)
  {
    var returnURL;
    var query = url.substring(url.indexOf("?"));

    if (!query)
    {
      returnURL = url;
    }
    else
    {
      returnURL = url.substring(0, url.indexOf("?"));
      var queryParameters = query.split("&");

      for (var qp in queryParameters)
      {
        var nextQueryParam = queryParameters[qp].split("=");
        var queryParamKeyValue = nextQueryParam[1];

        returnURL = returnURL + nextQueryParam[0] + "="
                        + queryParamKeyValue + "&";
      }

      returnURL = returnURL.substring(0, returnURL.length - 1);
    }

    return returnURL;
  }

  function PreviewFormatter(row, cell, value, columnDef, dataContext)
  {
    // A link was already added in by VOView, which contains the preview
    // information in its href attribute.
    var previewLink = $("<a></a>");
    var query;
    var providedPreviewURL = value;

    if (providedPreviewURL)
    {
      var collectionObsIDPath;

      if (providedPreviewURL.indexOf("?") > 0)
      {
        collectionObsIDPath =
        providedPreviewURL.substring(
            providedPreviewURL.indexOf("/getPreview/observation/")
                + "/getPreview/observation/".length,
            providedPreviewURL.indexOf("?"));
        query = providedPreviewURL.substring(providedPreviewURL.indexOf("?")
                                                 + 1);
      }
      else
      {
        collectionObsIDPath =
        providedPreviewURL.substring(
            providedPreviewURL.indexOf("/getPreview/observation/")
                + "/getPreview/observation/".length);
        query = "";
      }

      var pathItems = collectionObsIDPath.split("/");
      var collection = pathItems[0];
      var collectionID = pathItems[1];
      var queryItems = query.split("&");
      var runID = "";

      $.each(queryItems, function(index, queryItem)
      {
        var keyValue = queryItem.split("=");

        if (keyValue[0] == "RUNID")
        {
          runID = keyValue[1];
        }
      });

      var previewCell = $(cell);

      previewCell.html("");
      cadc.Preview.getPreview(collection, collectionID, "", 1024, runID,
                              function(previewURL)
                              {
                                if (previewURL.previewURL)
                                {
                                  previewLink.attr("target", "_preview");
                                  previewLink.attr("href", encodeParameters(previewURL.previewURL));
                                  previewLink.text("Preview");

                                  cadc.Preview.getPreview(collection, collectionID, "", 256, runID,
                                                          function(tooltipPreviewURL)
                                                          {
                                                            if (tooltipPreviewURL.previewURL)
                                                            {
                                                              var previewImage = $("<img>");
                                                              previewImage.attr("src", tooltipPreviewURL.previewURL);
                                                              previewImage.attr("style", "width: 256px; height: 256px");
                                                              previewImage.attr("alt", "Loading...");

                                                              var tooltipDiv = $("<div></div>");
                                                              tooltipDiv.addClass("tooltip preview_tooltip");
                                                              tooltipDiv.append(previewImage);
                                                              tooltipDiv.hide();

                                                              $(previewLink).tooltip({
                                                                                       position: "bottom right",
                                                                                       offset: [-10, -10],
                                                                                       effect: "toggle",
                                                                                       delay: 0,
                                                                                       events:
                                                                                       {
                                                                                         def:     "mouseover,mouseout",
                                                                                         input:   "none,none",
                                                                                         widget:  "none,none",
                                                                                         tooltip: "mouseover,mouseout"
                                                                                       }
                                                                                     });

                                                              previewLink.after(tooltipDiv);
                                                            }

                                                            previewCell.append(previewLink);
                                                          });
                                }


                              });
    }
  }

  function PercentCompleteFormatter(row, cell, value, columnDef, dataContext) {
    if (value == null || value === "") {
      return "-";
    } else if (value < 50) {
      return "<span style='color:red;font-weight:bold;'>" + value + "%</span>";
    } else {
      return "<span style='color:green'>" + value + "%</span>";
    }
  }

  function PercentCompleteBarFormatter(row, cell, value, columnDef, dataContext) {
    if (value == null || value === "") {
      return "";
    }

    var color;

    if (value < 30) {
      color = "red";
    } else if (value < 70) {
      color = "silver";
    } else {
      color = "green";
    }

    return "<span class='percent-complete-bar' style='background:" + color + ";width:" + value + "%'></span>";
  }

  function YesNoFormatter(row, cell, value, columnDef, dataContext) {
    return value ? "Yes" : "No";
  }

  function CheckmarkFormatter(row, cell, value, columnDef, dataContext) {
    return value ? "<img src='../images/tick.png'>" : "";
  }
})(jQuery);