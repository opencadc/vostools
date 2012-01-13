
package ca.nrc.cadc.tap.parser.converter;

import ca.nrc.cadc.tap.parser.navigator.FromItemNavigator;
import ca.nrc.cadc.util.CaseInsensitiveStringComparator;
import java.util.Map;
import java.util.TreeMap;
import net.sf.jsqlparser.schema.Table;
import org.apache.log4j.Logger;

/**
 * Simple class to map table name(s) used in the query to table name(s) used
 * in the database. You need to use both this and the TableNameReferenceConverter
 * with the same underlying map to convert all places a table name can be used.
 * 
 * @author pdowler
 */
public class TableNameConverter extends FromItemNavigator
{
    protected static Logger log = Logger.getLogger(TableNameConverter.class);

    public Map<String, Table> map;

    public TableNameConverter(boolean ignoreCase)
    {
        if (ignoreCase)
            this.map = new TreeMap<String,Table>(new CaseInsensitiveStringComparator());
        else
            this.map = new TreeMap<String,Table>();
    }

    public TableNameConverter(Map<String, Table> map)
    {
        this.map = map;
    }

    /**
     * Add new entries to the table name map.
     *
     * @param originalName a table name that should be replaced
     * @param newName the value that originalName should be replaced with
     */
    public void put(String originalName, String newName)
    {
        Table t = new Table();
        String[] parts = newName.split("[.]");
        if (parts.length == 1)
            t.setName(parts[0]);
        else if (parts.length == 2)
        {
            t.setSchemaName(parts[0]);
            t.setName(parts[1]);
        }
        else
            throw new IllegalArgumentException("expected new table name to have 1-2 parts, found " + parts.length);
        
        map.put(originalName, t);
    }

    @Override
    public void visit(Table table)
    {
        log.debug("visit(table)" + table);
        String tabName = table.getWholeTableName();
        log.debug("looking for " + tabName + " in conversion map...");
        Table ntab = map.get(tabName);
        log.debug("found: " + ntab);
        if (ntab != null)
        {
            log.debug("convert: " + table.getSchemaName() + "." + table.getName()
                    + " -> " + ntab.getSchemaName() + "." + ntab.getName());
            table.setName(ntab.getName());
            table.setSchemaName(ntab.getSchemaName());
            // leave alias intact
        }
    }
}
