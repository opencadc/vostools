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

package ca.nrc.cadc.vos.client;

import java.util.List;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import ca.nrc.cadc.util.ArgumentMap;
import ca.nrc.cadc.util.Log4jInit;
import ca.nrc.cadc.vos.NodeProperty;
import ca.nrc.cadc.vos.VOSURI;

/**
 * @author zhangsa
 *
 */
public class Main
{
    public static final String CR = System.getProperty("line.separator"); // OS independant new line
    public static final String EL = " "; // empty line

    public static final String ARG_HELP = "help";
    public static final String ARG_VERBOSE = "verbose";
    public static final String ARG_DEBUG = "debig";
    public static final String ARG_VIEW = "view";
    public static final String ARG_CREATE = "create";
    public static final String ARG_DELETE = "delete";
    public static final String ARG_SET = "set";
    public static final String ARG_COPY = "copy";

    public static String usage = Main.usageMessage();
    private static Logger log = Logger.getLogger(Main.class);

    /**
     * Operations of VoSpace Client.
     * 
     * @author zhangsa
     *
     */
    public enum Operation {
        VIEW, CREATE, DELETE, SET, COPY
    };

    boolean verbose = false;
    boolean debug = false;
    Operation operation;
    VOSURI target;
    List<NodeProperty> properties;
    VOSURI source;
    VOSURI destination;

    /**
     * @param args
     */
    public static void main(String[] args)
    {
        ArgumentMap argMap = new ArgumentMap(args);

        if (argMap.isSet(ARG_HELP))
            System.out.println(Main.usage);
        else
        {
            Main command = new Main();
            if (argMap.isSet(ARG_DEBUG))
            {
                command.setDebug(true);
                Log4jInit.setLevel("ca.nrc.cadc.vos.client", Level.DEBUG);
            }
            else if (argMap.isSet(ARG_VERBOSE))
            {
                command.setVerbose(true);
                Log4jInit.setLevel("ca.nrc.cadc.vos.client", Level.INFO);
            }
            else
                Log4jInit.setLevel("ca.nrc.cadc.vos.client", Level.WARN);

            int numOp = command.validateOperationArgument(argMap);
            if (numOp == 0)
            {
                System.out.println("One operation should be defined.");
                System.out.println();
                System.out.println(Main.usage);
            }
            else if (numOp > 1)
            {
                System.out.println("Only one operation can be defined.");
                System.out.println();
                System.out.println(Main.usage);
            }
            else
            {
                command.init(argMap);
                command.run();
            }
        }
        return;
    }

    private void run()
    {
        // TODO implementation
    }

    /**
     * Initialize command member variables based on arguments passed in.
     * 
     * @param argMap
     */
    private void init(ArgumentMap argMap)
    {
        // TODO implementation
    }

    /**
     * @param argMap
     */
    private int validateOperationArgument(ArgumentMap argMap)
    {
        int numOp = 0;
        if (argMap.isSet(ARG_VIEW))
        {
            numOp++;
            this.operation = Operation.VIEW;
        }
        if (argMap.isSet(ARG_CREATE))
        {
            numOp++;
            this.operation = Operation.CREATE;
        }
        if (argMap.isSet(ARG_DELETE))
        {
            numOp++;
            this.operation = Operation.DELETE;
        }
        if (argMap.isSet(ARG_SET))
        {
            numOp++;
            this.operation = Operation.SET;
        }
        if (argMap.isSet(ARG_COPY))
        {
            numOp++;
            this.operation = Operation.COPY;
        }

        if (numOp > 1) this.operation = null;

        return numOp;
    }

    /**
     * @return The usage string
     */
    public static String usageMessage()
    {
        String[] um = {
                "Usage:                                                                                                                             ",
                EL,
                "<comm> is an environment-specific command to run this VOSpace Client program,                                                                ",
                "  e.g. \"java Command\", or \"java -jar VoSpaceClient.jar \"                                                                                 ",
                EL,
                "To display usage:                                                                                                                             ",
                "<comm> --help                                                                                                                             ",
                EL,
                "General syntax:                                                                                                                             ",
                EL,
                "<comm> <outputOption> <operation> <operationParameters>                                                                                    ",
                "i.e.                                                                                                                   ",
                "<comm> [-v|-d] --view|create|delete|set|copy <operationParameters>                                                                          ",
                EL,
                "<operationParameters> for create|delete|set:                                                                                                 ",
                "   --target=<uri>                                                                                                                   ",
                "   --content-type=<string>                                                                                                                ",
                "   --content-encoding=<string>                                                                                                               ",
                "   --group-read=<localFilePath>                                                                                                              ",
                "   --group-write=<localFilePath>                                                                                                             ",
                "   --prop=<localFilePath>                                                                                                                    ",
                EL,
                "<operationParameters> for copy:                                                                                                              ",
                "   --src=<sourceUri>                                                                                                                ",
                "   --dest=<destinationUri>                                                                                                                ",
                EL,
                "<operationParameters> for view:                                                                                                              ",
                "   --target=<uri>                                                                                                                   ",
                "                                                                                                                   ",
                "<outputOption>:                                                                                                                   ",
                "   -v, --verbose /t Verbose mode. optional.                                                                                                   ",
                "   -d, --debug   /t Debug mode. optional.                                                                                                     ",
                EL,
                "format of <uri>, e.g.:",
                "   vos://cadc.nrc.ca!vospace/dirNameA                                                                                                         ",
                "                                                                                                                   ",
                "                                                   " };
        StringBuilder sb = new StringBuilder();
        for (String line : um)
        {
            sb.append(line).append(CR);
        }
        return sb.toString();
    }

    public boolean isVerbose()
    {
        return verbose;
    }

    public void setVerbose(boolean verbose)
    {
        this.verbose = verbose;
    }

    public boolean isDebug()
    {
        return debug;
    }

    public void setDebug(boolean debug)
    {
        this.debug = debug;
    }

}
