test("Read in simple VOTable.", 6, function()
{
  var xmlData =
      "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
      + "<VOTABLE xmlns=\"http://www.ivoa.net/xml/VOTable/v1.2\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" version=\"1.2\">\n"
      + "  <RESOURCE>\n"
      + "    <TABLE>\n"
      + "      <FIELD name=\"Job ID\" datatype=\"char\" arraysize=\"*\" />\n"
      + "      <FIELD name=\"User\" datatype=\"char\" arraysize=\"*\" />\n"
      + "      <FIELD name=\"Started\" datatype=\"char\" arraysize=\"*\" />\n"
      + "      <FIELD name=\"Status\" datatype=\"char\" arraysize=\"*\" />\n"
      + "      <FIELD name=\"Command\" datatype=\"char\" arraysize=\"*\" />\n"
      + "      <FIELD name=\"VM Type\" datatype=\"char\" arraysize=\"*\" />\n"
      + "      <FIELD name=\"CPUs\" datatype=\"int\" />\n"
      + "      <FIELD name=\"Memory\" datatype=\"long\" />\n"
      + "      <FIELD name=\"Job Starts\" datatype=\"int\" />\n"
      + "      <DATA>\n"
      + "        <TABLEDATA>\n"
      + "          <TR>\n"
      + "            <TD>735.0</TD>\n"
      + "            <TD>jenkinsd</TD>\n"
      + "            <TD />\n"
      + "            <TD>Idle</TD>\n"
      + "            <TD>sleep</TD>\n"
      + "            <TD>Tomcat</TD>\n"
      + "            <TD>1</TD>\n"
      + "            <TD>3072</TD>\n"
      + "            <TD>0</TD>\n"
      + "          </TR>\n"
      + "          <TR>\n"
      + "            <TD>734.0</TD>\n"
      + "            <TD>jenkinsd</TD>\n"
      + "            <TD />\n"
      + "            <TD>Idle</TD>\n"
      + "            <TD>sleep</TD>\n"
      + "            <TD>Tomcat</TD>\n"
      + "            <TD>1</TD>\n"
      + "            <TD>3072</TD>\n"
      + "            <TD>0</TD>\n"
      + "          </TR>\n"
      + "        </TABLEDATA>\n"
      + "      </DATA>\n"
      + "    </TABLE>\n"
      + "  </RESOURCE>\n"
      + "</VOTABLE>";

   // Create a DOM to pass in.
  var xmlDOM = new DOMParser().parseFromString(xmlData, "text/xml");
  var voTableBuilder = new cadc.vot.XMLBuilder(xmlDOM);
  voTableBuilder.build();

  equal(voTableBuilder.getVOTable().getResources().length, 1,
        "Should be one resource.");

  var firstTableObject = voTableBuilder.getVOTable().getResources()[0].getTables()[0];
  equal(firstTableObject.getFields().length, 9, "Should have nine fields.");
  equal(firstTableObject.getTableData().getRows().length, 2,
        "Should have two rows.");

  var firstRow = firstTableObject.getTableData().getRows()[0];
  equal(firstRow.getCells()[1].getValue(), "jenkinsd",
        "Should be 'jenkinsd' in second cell of first row.");
  ok(!isNaN(firstRow.getCells()[6].getValue()) && (firstRow.getCells()[6].getValue() == Number(1)),
     "Should be numeric value in seventh cell of first row.");
  ok(!isNaN(firstRow.getCells()[7].getValue()) && (firstRow.getCells()[7].getValue() == Number(3072)),
     "Should be numeric value in eighth cell of first row.");
});
