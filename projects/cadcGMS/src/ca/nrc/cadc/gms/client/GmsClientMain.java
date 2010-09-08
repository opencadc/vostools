/**
 ************************************************************************
 *******************  CANADIAN ASTRONOMY DATA CENTRE  *******************
 **************  CENTRE CANADIEN DE DONNÉES ASTRONOMIQUES  **************
 *
 *  (c) 2010.                            (c) 2010.
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
 ************************************************************************
 */

package ca.nrc.cadc.gms.client;

import java.io.File;
import java.net.URI;
import java.net.URL;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import ca.nrc.cadc.auth.SSLUtil;
import ca.nrc.cadc.gms.Group;
import ca.nrc.cadc.gms.GroupImpl;
import ca.nrc.cadc.gms.InvalidMemberException;
import ca.nrc.cadc.gms.User;
import ca.nrc.cadc.gms.UserImpl;
import ca.nrc.cadc.reg.client.RegistryClient;
import ca.nrc.cadc.util.ArgumentMap;
import ca.nrc.cadc.util.Log4jInit;

/**
 * Main class for the GmsClient
 */
public class GmsClientMain
{
    private static Logger logger = Logger.getLogger(GmsClientMain.class);

    public static final String ARG_HELP = "help";
    public static final String ARG_VERBOSE = "verbose";
    public static final String ARG_DEBUG = "debug";
    public static final String ARG_H = "h";
    public static final String ARG_V = "v";
    public static final String ARG_D = "d";
    public static final String ARG_VIEW = "view";
    public static final String ARG_CREATE = "create";
    public static final String ARG_DELETE = "delete";
    public static final String ARG_TARGET = "target";
    public static final String ARG_ADD_MEMBER = "add";
    public static final String ARG_MEMBER_NAME = "name";
    public static final String ARG_REMOVE_MEMBER = "remove";
    public static final String ARG_LIST_MEMBER_GROUPS = "listMember";
    public static final String ARG_LIST_OWNER_GROUPS = "listOwner";
    public static final String ARG_CERT = "cert";
    public static final String ARG_KEY = "key";

    // Operations on GMS client
    public enum Operation {
        VIEW, CREATE, DELETE, ADD_MEMBER, REMOVE_MEMBER, LIST_MEMBER_GR, LIST_OWNER_GR
    };

    public static final String VOS_PREFIX = "vos://";

    private static final int INIT_STATUS = 1; // exit code for
    // initialisation failure
    private static final int NET_STATUS = 2; // exit code for
    // client-server failures

    private String baseURL;
    private RegistryClient registryClient = new RegistryClient();
    private GmsClient client;

    private Operation operation; // current operation on GMS client
    private String target; // group/user ID the operation is executed on
    private String memberID; // ID of a member to be added or removed
    // from a group
    private String memberName; // the name of the member to be added
    // to a group

    File certFile = null;
    File keyFile = null;

    private static final String SERVICE_ID = "ivo://cadc.nrc.ca/gms";

    /**
     * @param args
     */
    public static void main(String[] args)
    {
        ArgumentMap argMap = new ArgumentMap(args);
        GmsClientMain command = new GmsClientMain();

        if (argMap.isSet(ARG_HELP) || argMap.isSet(ARG_H))
        {
            usage();
            System.exit(0);
        }

        // Set debug mode
        if (argMap.isSet(ARG_DEBUG) || argMap.isSet(ARG_D))
        {
            Log4jInit.setLevel("ca.nrc.cadc.gms.client", Level.DEBUG);
        }
        else if (argMap.isSet(ARG_VERBOSE) || argMap.isSet(ARG_V))
        {
            Log4jInit.setLevel("ca.nrc.cadc.gms.client", Level.INFO);
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
            msg("illegal argument(s): " + ex.getMessage());
            msg("");
            usage();
            System.exit(INIT_STATUS);
        }

        try
        {
            command.init(argMap);
            command.run();
        }
        catch (Throwable t)
        {
            logger.error("unexpected failure", t);
        }
        System.exit(0);

    }

    /**
     * Initializes of the base URL for the service
     * 
     * @param argMap
     */
    private void init(ArgumentMap argMap)
    {
        try
        {
            validateInitSSL(argMap);
        }
        catch (Exception ex)
        {
            logger.error("failed to initialise SSL from certificates: "
                    + ex.getMessage());
            System.exit(INIT_STATUS);
        }

        try
        {
            // TODO pass the https protocol to this method when ready for
            // use
            URL baseURL = registryClient
                    .getServiceURL(new URI(SERVICE_ID), "https");
            if (baseURL == null)
            {
                logger.error("failed to find service URL for "
                        + SERVICE_ID);
                System.exit(INIT_STATUS);
            }
            this.baseURL = baseURL.toString();
            this.client = new GmsClient(new URL(this.baseURL));
        }
        catch (Exception e)
        {
            logger.error("failed to find service URL for " + SERVICE_ID);
            logger.error("reason: " + e.getMessage());
            System.exit(INIT_STATUS);
        }

        logger.info("server uri: " + SERVICE_ID);
        logger.info("base url: " + this.baseURL);
    }

    /**
     * Initializes of the SSL certificates
     * 
     * @param argMap
     */
    private void validateInitSSL(ArgumentMap argMap)
    {
        String strCert = argMap.getValue(ARG_CERT);
        String strKey = argMap.getValue(ARG_KEY);
        if (strCert == null || strKey == null)
            throw new IllegalArgumentException(
                    "Argument cert and key are all required.");

        this.certFile = new File(strCert);
        this.keyFile = new File(strKey);

        StringBuffer sbSslMsg = new StringBuffer();
        boolean sslError = false;
        if (!certFile.exists())
        {
            sbSslMsg.append("Certificate file " + strCert
                    + " does not exist. \n");
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
                sbSslMsg.append("Certificate file " + strCert
                        + " cannot be read. \n");
                sslError = true;
            }
            if (!keyFile.canRead())
            {
                sbSslMsg.append("Key file " + strKey
                        + " cannot be read. \n");
                sslError = true;
            }
        }
        if (sslError)
        {
            throw new IllegalArgumentException(sbSslMsg.toString());
        }
        SSLUtil.initSSL(this.certFile, this.keyFile);
    }

    private void run()
    {
        logger.debug("run - START");
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
        else if (this.operation.equals(Operation.ADD_MEMBER))
        {
            doAddMember();
        }
        else if (this.operation.equals(Operation.REMOVE_MEMBER))
        {
            doRemoveMember();
        }
        else if (this.operation.equals(Operation.LIST_MEMBER_GR))
        {
            doListMemberGroups();
        }
        else if (this.operation.equals(Operation.LIST_OWNER_GR))
        {
            doListMyGroups();
        }
        logger.debug("run - DONE");
    }

    /**
     * Validates the command line operations
     * 
     * @param argMap
     */
    private void validateCommand(ArgumentMap argMap)
            throws IllegalArgumentException
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
        if (argMap.isSet(ARG_ADD_MEMBER))
        {
            numOp++;
            this.operation = Operation.ADD_MEMBER;
        }
        if (argMap.isSet(ARG_REMOVE_MEMBER))
        {
            numOp++;
            this.operation = Operation.REMOVE_MEMBER;
        }
        if (argMap.isSet(ARG_LIST_MEMBER_GROUPS))
        {
            numOp++;
            this.operation = Operation.LIST_MEMBER_GR;
        }
        if (argMap.isSet(ARG_LIST_OWNER_GROUPS))
        {
            numOp++;
            this.operation = Operation.LIST_OWNER_GR;
        }

        if (numOp == 0)
            throw new IllegalArgumentException(
                    "One operation should be defined.");
        else if (numOp > 1)
            throw new IllegalArgumentException(
                    "Only one operation can be defined.");

        return;
    }

    /**
     * Validates the command line arguments
     * 
     * @param argMap
     */
    private void validateCommandArguments(ArgumentMap argMap)
            throws IllegalArgumentException
    {

        String strTarget = argMap.getValue(ARG_TARGET);
        if (this.operation.equals(Operation.ADD_MEMBER))
        {
            target = strTarget;
            memberName = argMap.getValue(ARG_MEMBER_NAME);
            memberID = argMap.getValue(ARG_CREATE);
            if (memberName == null || memberID == null)
            {
                throw new IllegalArgumentException(
                        "Arguments add and name are required for "
                                + this.operation);
            }
        }
        else
        {
            if (!this.operation.equals(Operation.CREATE)
                    && (strTarget == null))
                throw new IllegalArgumentException(
                        "Argument target is required for "
                                + this.operation);
            else if (this.operation.equals(Operation.LIST_MEMBER_GR)
                    && (strTarget == null))
            {
                // TODO target is the authenticated user.
                // Read target from certificates
                throw new UnsupportedOperationException(
                        "Cannot determine target");
            }

            target = strTarget;

            // check remove argument
            if (this.operation.equals(Operation.REMOVE_MEMBER))
            {
                memberID = argMap.getValue(ARG_DELETE);
                if (memberID == null)
                {
                    throw new IllegalArgumentException(
                            "Argument remove is required for "
                                    + this.operation);
                }
            }
        }
    }

    /**
     * Executes group creation command
     */
    private void doCreate()
    {
        Group group = null;
        if (target != null)
        {
            // user specifies the group ID
            group = new GroupImpl(target);
        }
        try
        {
            client.createGroup(group);
        }
        catch (Exception e)
        {
            logger.error("failed to create group " + target);
            logger.error("reason: " + e.getMessage());
            System.exit(NET_STATUS);
        }
    }

    /**
     * Executes group view command
     */
    private void doView()
    {
        try
        {
            Group group = client.getGroup(target);
            if (group == null)
            {
                msg("Group: " + group.getGMSGroupID() + " not found");
                return;
            }
            msg("   Group ID: " + group.getGMSGroupID());
            msg("        URI: " + group.getGroupURI());
            msg("       Name: " + group.getGMSGroupName());
            msg("Description: " + group.getDescription());
            User owner = group.getOwner();
            String ownerStr = "N/A";
            if (owner != null)
            {
                ownerStr = owner.getUsername() + " (" + owner.getUserID()
                        + ")";
            }
            msg("      Owner: " + ownerStr);
            msg("Members: Name (user ID)");
            for (User user : group.getMembers())
            {
                msg("\t" + user.getUsername() + " (" + user.getUserID()
                        + ") ");
            }
        }
        catch (Exception e)
        {
            logger.error("failed to view group " + target);
            logger.error("reason: " + e.getMessage());
            System.exit(NET_STATUS);
        }

    }

    /**
     * Executes group delete command
     */
    private void doDelete()
    {
        try
        {
            client.deleteGroup(target);
        }
        catch (Exception e)
        {
            logger.error("failed to delete group " + target);
            logger.error("reason: " + e.getMessage());
            System.exit(NET_STATUS);
        }
    }

    /**
     * Executes add member command
     */
    private void doAddMember()
    {
        try
        {
            if (!client.isMember(target, memberID))
            {
                Group group = client.getGroup(target);
                group.addMember(new UserImpl(memberID, memberName));
                client.setGroup(group);
            }
            else
            {
                logger.error("User " + memberID + " (" + memberName
                        + ") already a member of group " + target);
                System.exit(INIT_STATUS);
            }
        }
        catch (InvalidMemberException e)
        {
            logger.error("failed add user " + memberID + " ("
                    + memberName + ")");
            logger.error("reason: " + e.getMessage());
            System.exit(NET_STATUS);
        }
    }

    /**
     * Executes remove member command
     */
    private void doRemoveMember()
    {
        try
        {
            if (client.isMember(target, memberID))
            {
                Group group = client.getGroup(target);
                group.removeMember(memberID);
                client.setGroup(group);
            }
            else
            {
                logger.error("User " + memberID + " (" + memberName
                        + ") not a member of group " + target);
                System.exit(INIT_STATUS);
            }
        }
        catch (InvalidMemberException e)
        {
            logger.error("failed add user " + memberID + " ("
                    + memberName + ")");
            logger.error("reason: " + e.getMessage());
            System.exit(NET_STATUS);
        }
    }

    /**
     * Executes list groups user is member of command
     */
    private void doListMemberGroups()
    {
        try
        {
            User user = client.getGMSMembership(target);
            if (user == null)
            {
                msg("User: " + target + " not found");
                return;
            }
            msg("User: " + user.getUserID());
            msg("Group Membership (Group Name / Group URI / Group Descripton):");
            for (Group group : user.getGMSMemberships())
            {
                msg("     " + group.getGMSGroupName() + " / "
                        + group.getGroupURI() + " / "
                        + group.getDescription());
            }
            msg("Total groups: " + user.getGMSMemberships().size());

        }
        catch (Exception e)
        {
            logger.error("failed to list groups of member " + target);
            logger.error("reason: " + e.getMessage());
            System.exit(NET_STATUS);
        }

    }

    /**
     * Executes list groups that the user is owner of
     */
    private void doListMyGroups()
    {
        // TODO - when required
        throw new UnsupportedOperationException("Not yet implemented");
    }

    /**
     * Formats the usage message.
     */
    public static void usage()
    {
        String[] um = {
                "Usage: java -jar cadcGMSClient.jar [-v|--verbose|-d|--debug]  ...                                         ",
                "                                                                                                  ",
                "Help:                                                                                             ",
                "java -jar cadcGMSClient.jar <-h | --help>                                                         ",
                "                                                                                                  ",
                "Create a group with server generated ID:"
                        + "java -jar cadcGMSClient.jar  [-v|--verbose|-d|--debug]                                            ",
                "   --cert=<SSL certificate file> --key=<SSL key file> --create                                    ",
                "                                                                                                  ",
                "Other group operations:                                                                              ",
                "java -jar cadcGMSClient.jar  [-v|--verbose|-d|--debug]                                            ",
                "   --cert=<SSL certificate file> --key=<SSL key file>                                             ",
                "   --target=<Group ID>                                                                            ",
                "   [--create|--view|--delete]                                ",
                "                                                                                                  ",
                "Group Membership operations:                                                                              ",
                "java -jar cadcGMSClient.jar  [-v|--verbose|-d|--debug]                                            ",
                "   --cert=<SSL certificate file> --key=<SSL key file>                                             ",
                "   --target=<Group ID>                                                                            ",
                "   [--add=<User ID> --name=<User Name>|--remove=<User ID>]                                ",
                "                                                                                                  ",
                "User operations:                                                                              ",
                "java -jar cadcGMSClient.jar  [-v|--verbose|-d|--debug]                                            ",
                "   --cert=<SSL certificate file> --key=<SSL key file>                                             ",
                "   [--listMember|--listOwner] [--target=<User ID>]                                                                           ",
                "                                                                                                  ", };

        for (String line : um)
            msg(line);

    }

    // encapsulate all messages to console here
    private static void msg(String s)
    {
        System.out.println(s);
    }

}
