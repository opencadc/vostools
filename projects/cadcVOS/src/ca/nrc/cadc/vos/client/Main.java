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

import java.net.URISyntaxException;
import java.util.List;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import ca.nrc.cadc.util.ArgumentMap;
import ca.nrc.cadc.util.Log4jInit;
import ca.nrc.cadc.vos.ContainerNode;
import ca.nrc.cadc.vos.Node;
import ca.nrc.cadc.vos.NodeProperty;
import ca.nrc.cadc.vos.VOS;
import ca.nrc.cadc.vos.VOSURI;
import ca.nrc.cadc.vos.server.util.NodeUtil;

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
    public static final String ARG_DEBUG = "debug";
    public static final String ARG_H = "h";
    public static final String ARG_V = "v";
    public static final String ARG_D = "d";
    public static final String ARG_VIEW = "view";
    public static final String ARG_CREATE = "create";
    public static final String ARG_DELETE = "delete";
    public static final String ARG_SET = "set";
    public static final String ARG_COPY = "copy";
    public static final String ARG_TARGET = "target";
    public static final String ARG_GROUP_READ = "group-read";
    public static final String ARG_GROUP_WRITE = "group-write";
    public static final String ARG_PROP = "prop";
    public static final String ARG_SRC = "src";
    public static final String ARG_CONTENT_TYPE = "content-type";
    public static final String ARG_CONTENT_ENCODING = "content-encoding";

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
    RegistryClient registryClient = new RegistryClient();

    /**
     * @param args
     */
    public static void main(String[] args)
    {
        ArgumentMap argMap = new ArgumentMap(args);
        Main command = new Main();
        boolean runCommand = true;

        if (argMap.isSet(ARG_HELP) || argMap.isSet(ARG_H))
        {
            runCommand = false;
            usage();
        }
        else
        {
            // Set debug mode
            if (argMap.isSet(ARG_DEBUG) || argMap.isSet(ARG_D))
            {
                command.setDebug(true);
                Log4jInit.setLevel("ca.nrc.cadc.vos.client", Level.DEBUG);
            }
            else if (argMap.isSet(ARG_VERBOSE) || argMap.isSet(ARG_V))
            {
                command.setVerbose(true);
                Log4jInit.setLevel("ca.nrc.cadc.vos.client", Level.INFO);
            }
            else
                Log4jInit.setLevel("ca.nrc.cadc.vos.client", Level.WARN);

            try
            {
                command.validateCommand(argMap);
                command.validateCommandArguments(argMap);
            }
            catch (IllegalArgumentException ex)
            {
                runCommand = false;
                System.out.println(ex.getMessage());
                System.out.println();
                usage();
            }
        }

        if (runCommand)
        {
            command.init(argMap);
            command.run();
        }
        return;
    }

    private void run()
    {
        if (this.operation == Operation.CREATE)
        {
            VOSpaceClient client = new VOSpaceClient(registryClient.getBaseURL(this.target));
            System.out.println("Create Node of URI: " + this.target.getURIObject().toString());
            
            ContainerNode cnode = null;
            try
            {
                cnode = new ContainerNode(this.target);
            }
            catch (URISyntaxException e)
            {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
            Node nodeRtn = client.createNode(cnode);
            System.out.println("Created Node on Server:");
            System.out.println(nodeRtn);
        }
        else if (this.operation == Operation.VIEW)
        {
            VOSpaceClient client = new VOSpaceClient(registryClient.getBaseURL(this.target));
            System.out.println("View Node of URI: " + this.target.getURIObject().toString());
            
            ContainerNode cnode = null;
            try
            {
                cnode = new ContainerNode(this.target);
            }
            catch (URISyntaxException e)
            {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
            Node nodeRtn = client.getNode(cnode.getPath());
            System.out.println("Node on Server:");
            System.out.println(nodeRtn.getUri());
            if (nodeRtn instanceof ContainerNode)
            {
                for (Node node : ((ContainerNode)nodeRtn).getNodes() )
                {
                    System.out.println(node.getUri());
                }
            }
        }
        // TODO implementation
    }

    /**
     * Initialize command member variables based on arguments passed in.
     * 
     * @param argMap
     * @throws URISyntaxException 
     */
    private void init(ArgumentMap argMap) 
    {
        String strTarget = argMap.getValue(ARG_TARGET);
        try
        {
            this.target = new VOSURI(strTarget);
        }
        catch (URISyntaxException e)
        {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        

        if (this.operation == Operation.CREATE)
        {
            
        }
        else if (this.operation == Operation.CREATE)
        {
            String strSrc = argMap.getValue(ARG_SRC);
            try
            {
                this.source = new VOSURI(strSrc);
            }
            catch (URISyntaxException e)
            {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }

        }
        // TODO implementation
    }

    /**
     * @param argMap
     */
    private void validateCommand(ArgumentMap argMap) throws IllegalArgumentException
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

        if (numOp == 0)
            throw new IllegalArgumentException("One operation should be defined.");
        else if (numOp > 1) throw new IllegalArgumentException("Only one operation can be defined.");

        return;
    }

    /**
     * @param argMap
     */
    private void validateCommandArguments(ArgumentMap argMap) throws IllegalArgumentException
    {
        String strTarget = argMap.getValue(ARG_TARGET);
        if (strTarget == null) throw new IllegalArgumentException("Argument target is required for " + this.operation);

        if (this.operation.equals(Operation.COPY))
        {
            String strSrc = argMap.getValue(ARG_SRC);
            if (strSrc == null) throw new IllegalArgumentException("Argument src is required for " + this.operation);
        }

        return;
    }

    /**
         * @return The usage string
         */
    public static void usage()
    {
        //String invoke = "java -jar cadcVOSClient.jar [-v|--verbose|-d|--debug]";

        String[] um = {
        /*
         * Note: When using "Format" in Eclipse, shorter lines in this string array are squeezed into one line.
         * This makes it hard to read or edit.
         * 
         * A workaround is, lines are purposely extended with blank spaces to a certain length,
         * where the EOL is at about column 120 (including leading indenting spaces).
         * 
         * In this way, it's still easy to read and edit and the formatting operation does not change it's layout.
         * 
         */
        "Usage: java -jar VOSpaceClient.jar [-v|--verbose|-d|--debug]  ...                                 ",
                "                                                                                                  ",
                "Help:                                                                                             ",
                "java -jar VOSpaceClient.jar <-h | --help>                                                         ",
                "                                                                                                  ",
                "Create node:                                                                                      ",
                "java -jar VOSpaceClient.jar  [-v|--verbose|-d|--debug]                                            ",
                "   --create --target=<target URI>                                                                  ",
                "   [--group-read=<group URI>]                                                                      ",
                "   [--group-write=<group URI>]                                                                     ",
                "   [--prop=<properties file>]                                                                      ",
                "                                                                                                  ",
                "Note: --create defaults to creating a ContainerNode (directory). Creating                         ",
                "other types of nodes specifically is not suppoerted at this time.                                 ",
                "                                                                                                  ",
                "Copy file:                                                                                        ",
                "java -jar VOSpaceClient.jar  [-v|--verbose|-d|--debug]                                            ",
                "   --copy --src=<source URI> --target=<destination URI>                                            ",
                "   [--content-type=<mimetype of source>]                                                           ",
                "   [--content-encoding=<encoding of source>]                                                       ",
                "   [--group-read=<group URI>]                                                                      ",
                "   [--group-write=<group URI>]                                                                     ",
                "   [--prop=<properties file>]                                                                      ",
                "                                                                                                  ",
                "Note: One of --src and --target may be a \"vos\" URI and the other may be an                        ",
                "absolute or relative path to a file. If the target node does not exist, a                         ",
                "DataNode is created and data copied. If it does exist, the data (and                              ",
                "properties?) are overwritten.                                                                     ",
                "                                                                                                  ",
                "View node:                                                                                        ",
                "java -jar VOSpaceClient.jar  [-v|--verbose|-d|--debug]                                            ",
                "   --view --target=<target URI>                                                                    ",
                "   [--group-read=<group URI>]                                                                      ",
                "   [--group-write=<group URI>]                                                                     ",
                "                                                                                                  ",
                "Delete node:                                                                                      ",
                "java -jar VOSpaceClient.jar  [-v|--verbose|-d|--debug]                                            ",
                "   --delete --target=<target URI>                                                                  ",
                "   [--content-type=<mimetype of source>]                                                           ",
                "   [--content-encoding=<encoding of source>]                                                       ",
                "   [--group-read=<group URI>]                                                                      ",
                "   [--group-write=<group URI>]                                                                     ",
                "                                                                                                  ",
                "Set node:                                                                                         ",
                "java -jar VOSpaceClient.jar  [-v|--verbose|-d|--debug]                                            ",
                "   --set --target=<target URI>                                                                     ",
                "   [--content-type=<mimetype of source>]                                                           ",
                "   [--content-encoding=<encoding of source>]                                                       ",
                "   [--group-read=<group URI>]                                                                      ",
                "   [--group-write=<group URI>]                                                                     ",
                "   [--prop=<properties file>]                                                                      ",

                //                "Usage: " + invoke + " ...",
                //                "",
                //                "Help: java -jar VOSClient.jar <-h | --help>",
                //                "",
                //                "Create node: java -jar VOSpaceClient.jar  [-v|--verbose|-d|--debug]",
                //                "   --create --target=<target URI>",
                //                "   [--group-read=<group URI>]",
                //                "   [--group-write=<group URI>]",
                //                "   [--prop=<properties file>]",
                //                "",
                //                "Note: --create defaults to creating a ContainerNode (directory). Creating",
                //                "other types of nodes specifically is not supported at this time.",
                //                "",
                //                "Copy file: " + invoke,
                //                "   --copy --src=<source URI> --target=<destination URI>",
                //                "   [--content-type=<mimetype of source>]",
                //                "   [--content-encoding=<encoding of source>]",
                //                "   [--group-read=<group URI>]",
                //                "   [--group-write=<group URI>]",
                //                "   [--prop=<properties file>]",
                //                "",
                //                "Note: One of --src and --target may be a \"vos\" URI and the other may be an",
                //                "absolute or relative path to a file. If the target node does not exist, a",
                //                "DataNode is created and data copied. If it does exist, the data (and",
                //                "properties?) are overwritten.",
                //                "",
                //                "View node: " + invoke,
                //                "   --view --target=<target URI>",
                //                "   [--group-read=<group URI>]",
                //                "   [--group-write=<group URI>]",
                //                "",
                //                "Delete node: " + invoke,
                //                "   --delete --target=<target URI>",
                //                "   [--content-type=<mimetype of source>]",
                //                "   [--content-encoding=<encoding of source>]",
                //                "   [--group-read=<group URI>]",
                //                "   [--group-write=<group URI>]",
                //                "",
                //                "Set node: " + invoke,
                //                "   --set --target=<target URI>",
                //                "   [--content-type=<mimetype of source>]",
                //                "   [--content-encoding=<encoding of source>]",
                //                "   [--group-read=<group URI>]",
                //                "   [--group-write=<group URI>]",
                //                "   [--prop=<properties file>]",
                "" };
        for (String line : um)
            System.out.println(line);
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
