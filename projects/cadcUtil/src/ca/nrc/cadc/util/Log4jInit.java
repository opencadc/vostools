/*
************************************************************************
*******************  CANADIAN ASTRONOMY DATA CENTRE  *******************
**************  CENTRE CANADIEN DE DONNÉES ASTRONOMIQUES  **************
*
*  (c) 2009.                            (c) 2009.
*  Government of Canada                 Gouvernement du Canada
*  National Research Council            Conseil national de recherches
*  Ottawa, Canada, K1A 0R6              Ottawa, Canada, K1A 0R6
*  All rights reserved                  Tous droits réservés
*
*  NRC disclaims any warranties,        Le CNRC dénie toute garantie
*  expressed, implied, or               énoncée, implicite ou légale,
*  statutory, of any kind with          de quelque nature que ce
*  respect to the software,             soit, concernant le logiciel,
*  including without limitation         y compris sans restriction
*  any warranty of merchantability      toute garantie de valeur
*  or fitness for a particular          marchande ou de pertinence
*  purpose. NRC shall not be            pour un usage particulier.
*  liable in any event for any          Le CNRC ne pourra en aucun cas
*  damages, whether direct or           être tenu responsable de tout
*  indirect, special or general,        dommage, direct ou indirect,
*  consequential or incidental,         particulier ou général,
*  arising from the use of the          accessoire ou fortuit, résultant
*  software.  Neither the name          de l'utilisation du logiciel. Ni
*  of the National Research             le nom du Conseil National de
*  Council of Canada nor the            Recherches du Canada ni les noms
*  names of its contributors may        de ses  participants ne peuvent
*  be used to endorse or promote        être utilisés pour approuver ou
*  products derived from this           promouvoir les produits dérivés
*  software without specific prior      de ce logiciel sans autorisation
*  written permission.                  préalable et particulière
*                                       par écrit.
*
*  This file is part of the             Ce fichier fait partie du projet
*  OpenCADC project.                    OpenCADC.
*
*  OpenCADC is free software:           OpenCADC est un logiciel libre ;
*  you can redistribute it and/or       vous pouvez le redistribuer ou le
*  modify it under the terms of         modifier suivant les termes de
*  the GNU Affero General Public        la “GNU Affero General Public
*  License as published by the          License” telle que publiée
*  Free Software Foundation,            par la Free Software Foundation
*  either version 3 of the              : soit la version 3 de cette
*  License, or (at your option)         licence, soit (à votre gré)
*  any later version.                   toute version ultérieure.
*
*  OpenCADC is distributed in the       OpenCADC est distribué
*  hope that it will be useful,         dans l’espoir qu’il vous
*  but WITHOUT ANY WARRANTY;            sera utile, mais SANS AUCUNE
*  without even the implied             GARANTIE : sans même la garantie
*  warranty of MERCHANTABILITY          implicite de COMMERCIALISABILITÉ
*  or FITNESS FOR A PARTICULAR          ni d’ADÉQUATION À UN OBJECTIF
*  PURPOSE.  See the GNU Affero         PARTICULIER. Consultez la Licence
*  General Public License for           Générale Publique GNU Affero
*  more details.                        pour plus de détails.
*
*  You should have received             Vous devriez avoir reçu une
*  a copy of the GNU Affero             copie de la Licence Générale
*  General Public License along         Publique GNU Affero avec
*  with OpenCADC.  If not, see          OpenCADC ; si ce n’est
*  <http://www.gnu.org/licenses/>.      pas le cas, consultez :
*                                       <http://www.gnu.org/licenses/>.
*
*  $Revision: 4 $
*
************************************************************************
*/

package ca.nrc.cadc.util;

import java.io.Writer;
import java.util.ArrayList;
import java.util.List;

import ca.nrc.cadc.date.DateUtil;
import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;
import org.apache.log4j.WriterAppender;
import org.apache.log4j.varia.LevelRangeFilter;

/**
 * Initialize log4j for the specified package and level. Logging is only
 * to the console.
 *
 */
public class Log4jInit
{
    private static boolean consoleAppendersCreated = false;

    // SHORT_FORMAT applies to DEBUG and TRACE logging levels
    private static final String SHORT_FORMAT = "%-4r [%t] %-5p %c{1} %x - %m\n";

    // LONG_FORMAT applies to INFO, WARN, ERROR and FATAL logging levels
    private static final String LONG_FORMAT = "%d{" + DateUtil.ISO_DATE_FORMAT
                                              + "} [%t] %-5p %c{1} %x - %m\n";

    private static List<Writer> logWriters = new ArrayList<Writer>();
    
    static
    {
        Logger.getRootLogger().setLevel(Level.ERROR);
    }
    
    /**
     * Initializes logging to the console.
     * 
     * @param pkg the name of package or ancestors of package or classes. Can't be null.
     * @param level the logging level.
     */
    public static synchronized void setLevel(String pkg, Level level)
    {
        setLevel(null, pkg, level);
    }
    
    public static synchronized void setLevel(String appName, String pkg, Level level)
    {
        createLog4jConsoleAppenders(appName);

        // set specified package and level
        Logger.getLogger(pkg).setLevel(level);
    }
    

    /**
     * Configure log4j for use in a GUI. All output is done using the
     * same format. This method sets up logging to the specified writer
     * on every call, so should be called with a non-null writer only once
     * (per writer). It may be called multiple times to set logging levels for
     * different packages.
     * 
     * @param pkg package to configure logging for
     * @param level log level for pkg
     * @param dest destination writer to log to  (may be null)
     */
    public static synchronized void setLevel(String pkg, Level level, Writer dest)
    {
        createLog4jWriterAppender(dest);

        // set specified package and level
        Logger.getLogger(pkg).setLevel(level);
    }

    /**
     * Clear all existing appenders, then create default console appenders for
     * Log4j.  Any other extra file appender has to be initialized AFTER this
     * method is called, otherwise they would be cleared.
     *
     * @param appName       The name of the application that is calling this
     *                      method.
     */
    private static synchronized void createLog4jConsoleAppenders(String appName)
    {
        if (!consoleAppendersCreated)
        {
            // Clear all existing appenders, if there's any.
            BasicConfigurator.resetConfiguration();
            Logger.getRootLogger().setLevel(Level.ERROR); // must redo after reset

            String errorLogFormat = LONG_FORMAT;
            String infoLogFormat = LONG_FORMAT;
            String debugLogFormat = SHORT_FORMAT;
            
            if (appName != null)
            {
                errorLogFormat = "%d{" + DateUtil.ISO_DATE_FORMAT + "} "
                                 + appName + " [%t] %-5p %c{1} %x - %m\n";
                infoLogFormat =  "%d{" + DateUtil.ISO_DATE_FORMAT + "} "
                                 + appName + " [%t] %-5p %c{1} %x - %m\n";
                debugLogFormat = "%-4r " + appName
                                 + " [%t] %-5p %c{1} %x - %m\n";
            }
            
            // Appender for WARN, ERROR and FATAL with LONG_FORMAT message prefix
            ConsoleAppender conAppenderHigh =
                    new ConsoleAppender(new PatternLayout(errorLogFormat));
            LevelRangeFilter errorFilter = new LevelRangeFilter();
            errorFilter.setLevelMax(Level.FATAL);
            errorFilter.setLevelMin(Level.WARN);
            errorFilter.setAcceptOnMatch(true);
            conAppenderHigh.clearFilters();
            conAppenderHigh.addFilter(errorFilter);
            BasicConfigurator.configure(conAppenderHigh);

            // Appender for INFO with LONG_FORMAT message prefix
            ConsoleAppender conAppenderInfo =
                    new ConsoleAppender(new PatternLayout(infoLogFormat));
            LevelRangeFilter infoFilter = new LevelRangeFilter();
            infoFilter.setLevelMax(Level.INFO);
            infoFilter.setLevelMin(Level.INFO);
            infoFilter.setAcceptOnMatch(true);
            conAppenderInfo.clearFilters();
            conAppenderInfo.addFilter(infoFilter);
            BasicConfigurator.configure(conAppenderInfo);

            // Appender for DEBUG and TRACE with LONG_FORMAT message prefix
            ConsoleAppender conAppenderDebug =
                    new ConsoleAppender(new PatternLayout(debugLogFormat));
            LevelRangeFilter debugFilter = new LevelRangeFilter();
            debugFilter.setLevelMax(Level.DEBUG);
            debugFilter.setLevelMin(Level.TRACE);
            debugFilter.setAcceptOnMatch(true);
            conAppenderDebug.clearFilters();
            conAppenderDebug.addFilter(debugFilter);
            BasicConfigurator.configure(conAppenderDebug);
            
            consoleAppendersCreated = true;
        }
    }
    
    /**
     * Create a Log4j appender which writes logs into a writer, i.e. a
     * FileWriter.
     * 
     * @param writer        The Writer to write to for the new appenders.
     */
    private static synchronized void createLog4jWriterAppender(Writer writer)
    {
        if ( writer != null && !logWriters.contains(writer))
        {
            WriterAppender app = new WriterAppender(new PatternLayout(LONG_FORMAT), writer);
            LevelRangeFilter filter = new LevelRangeFilter();
            filter.setLevelMax(Level.FATAL);
            filter.setLevelMin(Level.DEBUG);
            filter.setAcceptOnMatch(true);
            app.clearFilters();
            app.addFilter(filter);
            BasicConfigurator.configure(app);
            
            logWriters.add(writer);  // Keep writer in the list so it's not created more than once.
        }
    }
}
