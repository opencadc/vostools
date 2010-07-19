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

import java.io.File;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import ca.nrc.cadc.auth.SSLUtil;
import ca.nrc.cadc.reg.client.RegistryClient;
import ca.nrc.cadc.util.ArgumentMap;
import ca.nrc.cadc.util.Log4jInit;
import ca.nrc.cadc.vos.ClientTransfer;
import ca.nrc.cadc.vos.ContainerNode;
import ca.nrc.cadc.vos.DataNode;
import ca.nrc.cadc.vos.DataView;
import ca.nrc.cadc.vos.Node;
import ca.nrc.cadc.vos.NodeProperty;
import ca.nrc.cadc.vos.Protocol;
import ca.nrc.cadc.vos.Transfer;
import ca.nrc.cadc.vos.VOS;
import ca.nrc.cadc.vos.VOSURI;
import ca.nrc.cadc.vos.Transfer.Direction;

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
    public static final String ARG_DEST = "dest";
    public static final String ARG_CONTENT_TYPE = "content-type";
    public static final String ARG_CONTENT_ENCODING = "content-encoding";
    public static final String ARG_CERT = "cert";
    public static final String ARG_KEY = "key";

    public static final String VOS_PREFIX = "vos://";

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
    URI source;
    URI destination;
    RegistryClient registryClient = new RegistryClient();
    Direction transferDirection = null;
    String baseUrl = null;
    VOSpaceClient client = null;
    File certFile = null;
    File keyFile = null;

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
                Log4jInit.setLevel("ca", Level.WARN);

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
        if (this.operation.equals(Operation.CREATE))
        {
            doCreate();
        }
        else if (this.operation.equals(Operation.DELETE))
        {
            doDelete();
        }
        else if (this.operation.equals(Operation.VIEW))
        {
            doView();
        }
        else if (this.operation.equals(Operation.COPY))
        {
            doCopy();
        }
    }

    private void doDelete()
    {
        log.debug("target.getPath()" + this.target.getPath());
        this.client.deleteNode(this.target.getPath());
        System.out.println("Node deleted: " + this.target.getPath());
    }

    private void doCopy()
    {
        if (this.transferDirection.equals(Transfer.Direction.pushToVoSpace))
        {
            try
            {
                DataNode dnode = new DataNode(new VOSURI(this.destination));
                dnode = (DataNode) this.client.createNode(dnode);
                DataView dview = new DataView(VOS.VIEW_DEFAULT, dnode);

                List<Protocol> protocols = new ArrayList<Protocol>();
                protocols.add(new Protocol(VOS.PROTOCOL_HTTPS_PUT, this.baseUrl, null));

                Transfer transfer = new Transfer();
                transfer.setTarget(dnode);
                transfer.setView(dview);
                transfer.setProtocols(protocols);
                transfer.setDirection(this.transferDirection);

                ClientTransfer clientTransfer = new ClientTransfer(this.client.pushToVoSpace(transfer));
                log.debug(clientTransfer.toXmlString());

                log.debug("this.source: " + this.source);
                File fileToUpload = new File(this.source);
                clientTransfer.doUpload(fileToUpload);
                Node node = clientTransfer.getTarget();
                log.debug("clientTransfer getTarget: " + node);
                Node nodeRtn = this.client.getNode(node.getPath());
                log.debug("Node returned from getNode, after doUpload: " + VOSClientUtil.xmlString(nodeRtn));
            }
            catch (URISyntaxException e)
            {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        }
        else if (this.transferDirection.equals(Transfer.Direction.pullFromVoSpace))
        {
            try
            {
                DataNode dnode = new DataNode(new VOSURI(this.source));
                DataView dview = new DataView(VOS.VIEW_DEFAULT, dnode);

                List<Protocol> protocols = new ArrayList<Protocol>();
                protocols.add(new Protocol(VOS.PROTOCOL_HTTPS_GET, this.baseUrl, null));

                Transfer transfer = new Transfer();
                transfer.setTarget(dnode);
                transfer.setView(dview);
                transfer.setProtocols(protocols);
                transfer.setDirection(this.transferDirection);

                ClientTransfer clientTransfer = new ClientTransfer(this.client.pullFromVoSpace(transfer));
                log.debug(clientTransfer.toXmlString());

                log.debug("this.source: " + this.source);
                File fileToSave = new File(this.destination);
                clientTransfer.doDownload(fileToSave);
                Node node = clientTransfer.getTarget();
                log.debug("clientTransfer getTarget: " + node);
                Node nodeRtn = this.client.getNode(node.getPath());
                log.debug("Node returned from getNode, after doDownload: " + VOSClientUtil.xmlString(nodeRtn));
            }
            catch (URISyntaxException e)
            {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        }
    }

    private void doCreate()
    {
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
        Node nodeRtn = this.client.createNode(cnode);
        System.out.println("Created Node on Server:");
        System.out.println(nodeRtn);
    }

    private void doView()
    {
        Node nodeRtn = this.client.getNode(this.target.getPath());
        System.out.println("Node: " + nodeRtn.getUri());
        if (nodeRtn instanceof ContainerNode)
        {
            for (Node node : ((ContainerNode) nodeRtn).getNodes())
            {
                System.out.println("\t" + node.getName());
            }
        }
        else if (nodeRtn instanceof DataNode)
        {
            System.out.println("Properties of Node:");
            for (NodeProperty np : nodeRtn.getProperties())
            {
                System.out.println("\t" + np.getPropertyURI() + ": " + np.getPropertyValue());
            }
        }
        else
        {
            log.debug("class of returned node: " + nodeRtn.getClass().getName());
        }

    }

    /**
     * Initialize command member variables based on arguments passed in.
     * 
     * @param argMap
     */
    private void init(ArgumentMap argMap)
    {
        URI serverUri = null;
        validateInitSSL(argMap);

        if (this.operation.equals(Operation.COPY))
        {
            String strSrc = argMap.getValue(ARG_SRC);
            String strDest = argMap.getValue(ARG_DEST);
            if (!strSrc.startsWith(VOS_PREFIX) && strDest.startsWith(VOS_PREFIX))
            {
                this.transferDirection = Direction.pushToVoSpace;
                try
                {
                    this.destination = new URI(strDest);
                    serverUri = new VOSURI(strDest).getServiceURI();
                }
                catch (URISyntaxException e)
                {
                    throw new IllegalArgumentException("Invalid VOS URI: " + strDest);
                }
                File f = new File(strSrc);
                if (!f.exists() || !f.canRead())
                    throw new IllegalArgumentException("Source file " + strSrc + " does not exist or cannot be read.");
                try
                {
                    this.source = new URI("file", f.getAbsolutePath(), null);
                }
                catch (URISyntaxException e)
                {
                    throw new IllegalArgumentException("Invalid file path: " + strSrc);
                }
            }
            else if (strSrc.startsWith(VOS_PREFIX) && !strDest.startsWith(VOS_PREFIX))
            {
                this.transferDirection = Direction.pullFromVoSpace;
                try
                {
                    serverUri = new VOSURI(strSrc).getServiceURI();
                    this.source = new URI(strSrc);
                }
                catch (URISyntaxException e)
                {
                    throw new IllegalArgumentException("Invalid VOS URI: " + strSrc);
                }
                File f = new File(strDest);
                if (f.exists())
                {
                    if (!f.canWrite()) throw new IllegalArgumentException("Destination file " + strDest + " is not writable.");
                }
                else
                {
                    File parent = f.getParentFile();
                    if (parent.isDirectory())
                    {
                        if (!parent.canWrite())
                            throw new IllegalArgumentException("The parent directory of destination file " + strDest
                                    + " is not writable.");
                    }
                    else
                        throw new IllegalArgumentException("Destination file " + strDest + " is not within a directory.");
                }
                try
                {
                    this.destination = new URI("file", f.getAbsolutePath(), null);
                }
                catch (URISyntaxException e)
                {
                    throw new IllegalArgumentException("Invalid file path: " + strDest);
                }
            }
            else
                throw new UnsupportedOperationException("The type of your copy operation is not supported yet.");
        }
        else
        {
            String strTarget = argMap.getValue(ARG_TARGET);
            try
            {
                this.target = new VOSURI(strTarget);
                serverUri = this.target.getServiceURI();
            }
            catch (URISyntaxException e)
            {
                throw new IllegalArgumentException("Invalid VOS URI: " + strTarget);
            }
        }

        try
        {
            this.baseUrl = registryClient.getServiceURL(serverUri, "https").toString();
        }
        catch (MalformedURLException e)
        {
            throw new IllegalArgumentException("Invalid VOS URI: " + serverUri);
        }
        this.client = new VOSpaceClient(baseUrl);
        log.info("server uri: " + serverUri);
        log.info("base url: " + this.baseUrl);
    }

    private void validateInitSSL(ArgumentMap argMap)
    {
        String strCert = argMap.getValue(ARG_CERT);
        String strKey = argMap.getValue(ARG_KEY);

        this.certFile = new File(strCert);
        this.keyFile = new File(strKey);

        StringBuffer sbSslMsg = new StringBuffer();
        boolean sslError = false;
        if (!certFile.exists())
        {
            sbSslMsg.append("Certificate file " + strCert + " does not exist. \n");
            sslError = true;
        }
        if (!keyFile.exists())
        {
            sbSslMsg.append("Key file " + strKey + " does not exist. \n");
            sslError = true;
        }
        if (!sslError)
        {
            if (!certFile.canRead())
            {
                sbSslMsg.append("Certificate file " + strCert + " cannot be read. \n");
                sslError = true;
            }
            if (!keyFile.canRead())
            {
                sbSslMsg.append("Key file " + strKey + " cannot be read. \n");
                sslError = true;
            }
        }
        if (sslError)
        {
            throw new IllegalArgumentException(sbSslMsg.toString());
        }
        SSLUtil.initSSL(this.certFile, this.keyFile);
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
        String strCert = argMap.getValue(ARG_CERT);
        String strKey = argMap.getValue(ARG_KEY);
        if (strCert == null || strKey == null) throw new IllegalArgumentException("Argument cert and key are all required.");

        if (this.operation.equals(Operation.COPY))
        {
            String strSrc = argMap.getValue(ARG_SRC);
            if (strSrc == null) throw new IllegalArgumentException("Argument src is required for " + this.operation);

            String strDest = argMap.getValue(ARG_DEST);
            if (strDest == null) throw new IllegalArgumentException("Argument dest is required for " + this.operation);
        }
        else
        {
            String strTarget = argMap.getValue(ARG_TARGET);
            if (strTarget == null) throw new IllegalArgumentException("Argument target is required for " + this.operation);
        }
    }

    /**
         * @return The usage string
         */
    public static void usage()
    {
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
                "   --cert=<SSL certificate file> --key=<SSL key file>                                             ",
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
                "   --cert=<SSL certificate file> --key=<SSL key file>                                             ",
                "   --copy --src=<source URI> --dest=<destination URI>                                            ",
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
                "   --cert=<SSL certificate file> --key=<SSL key file>                                             ",
                "   --view --target=<target URI>                                                                    ",
                "   [--group-read=<group URI>]                                                                      ",
                "   [--group-write=<group URI>]                                                                     ",
                "                                                                                                  ",
                "Delete node:                                                                                      ",
                "java -jar VOSpaceClient.jar  [-v|--verbose|-d|--debug]                                            ",
                "   --cert=<SSL certificate file> --key=<SSL key file>                                             ",
                "   --delete --target=<target URI>                                                                  ",
                "   [--content-type=<mimetype of source>]                                                           ",
                "   [--content-encoding=<encoding of source>]                                                       ",
                "   [--group-read=<group URI>]                                                                      ",
                "   [--group-write=<group URI>]                                                                     ",
                "                                                                                                  ",
                "Set node:                                                                                         ",
                "java -jar VOSpaceClient.jar  [-v|--verbose|-d|--debug]                                            ",
                "   --cert=<SSL certificate file> --key=<SSL key file>                                             ",
                "   --set --target=<target URI>                                                                     ",
                "   [--content-type=<mimetype of source>]                                                           ",
                "   [--content-encoding=<encoding of source>]                                                       ",
                "   [--group-read=<group URI>]                                                                      ",
                "   [--group-write=<group URI>]                                                                     ",
                "   [--prop=<properties file>]                                                                      ", "" };
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
