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
import java.security.PrivilegedAction;
import java.util.Collection;

import javax.security.auth.Subject;
import javax.security.auth.x500.X500Principal;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import ca.nrc.cadc.auth.SSLUtil;
import ca.nrc.cadc.gms.ElemProperty;
import ca.nrc.cadc.gms.GmsConsts;
import ca.nrc.cadc.gms.Group;
import ca.nrc.cadc.gms.GroupImpl;
import ca.nrc.cadc.gms.User;
import ca.nrc.cadc.reg.client.RegistryClient;
import ca.nrc.cadc.util.ArgumentMap;
import ca.nrc.cadc.util.Log4jInit;

/**
 * Main class for the GmsClient
 */
public class GmsClientMain implements PrivilegedAction<Boolean>
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
    public static final String ARG_CHECK_MEMBER = "check";
    public static final String ARG_LIST_MEMBER_GROUPS = "listMember";
    public static final String ARG_LIST_OWNER_GROUPS = "listOwner";
    public static final String ARG_CERT = "cert";
    public static final String ARG_KEY = "key";

    // Operations on GMS client
    public enum Operation {
        VIEW, CREATE, DELETE, ADD_MEMBER, REMOVE_MEMBER, CHECK_MEMBER, LIST_MEMBER_GR, LIST_OWNER_GR
    };

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
    
    // authenticated subject
    private static Subject subject;

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
            Subject.doAs(subject, command);
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
            URL baseURL = registryClient.getServiceURL(
                    new URI(SERVICE_ID), "https");
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

        File certFile = new File(strCert);
        File keyFile = new File(strKey);

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
        subject = SSLUtil.createSubject(certFile, keyFile);
    }

    public Boolean run()
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
        else if (this.operation.equals(Operation.CHECK_MEMBER))
        {
            doCheckMember();
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
        return new Boolean(true);
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
        if (argMap.isSet(ARG_CHECK_MEMBER))
        {
            numOp++;
            this.operation = Operation.CHECK_MEMBER;
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
            memberID = argMap.getValue(ARG_ADD_MEMBER);
            if (memberID == null)
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
                memberID = argMap.getValue(ARG_REMOVE_MEMBER);
                if (memberID == null)
                {
                    throw new IllegalArgumentException(
                            "Argument remove is required for "
                                    + this.operation);
                }
            }
            
            // check check argument
            if (this.operation.equals(Operation.CHECK_MEMBER))
            {
                memberID = argMap.getValue(ARG_CHECK_MEMBER);
                if (memberID == null)
                {
                    throw new IllegalArgumentException(
                            "Argument check is required for "
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
        try
        {
            if (target != null)
            {
                // user specifies the group ID
                group = new GroupImpl(new URI(target));
            }
            displayGroup(client.createGroup(group));

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
            Group group = client.getGroup(new URI(target));
            displayGroup(group);
        }
        catch (Exception e)
        {
            logger.error("failed to view group " + target);
            logger.error("reason: " + e.getMessage());
            System.exit(NET_STATUS);
        }

    }
       

    private void displayGroup(Group group)
    {
        if (group == null)
        {
            msg("Group: " + group.getID() + " not found");
            return;
        }
        msg("   Group ID: " + group.getID());
        if (group.getProperties().size() > 0)
        {
            msg("   Properties:");
            for (ElemProperty prop : group.getProperties())
            {
                msg(prop.getPropertyURI() + ": "
                        + prop.getPropertyValue());
            }
        }
        msg("Members:");
        int count = 0;
        for (User user : group.getMembers())
        {
            count++;
            msg("\t" + user.getID());
            if (group.getProperties().size() > 0)
            {
                msg("\t\tProperties:");
                for (ElemProperty prop : user.getProperties())
                {
                    msg("\t\t" + prop.getPropertyURI() + ": "
                            + prop.getPropertyValue());
                }
            }
        }
        msg("Total members: " + count);
    }

    /**
     * Executes group delete command
     */
    private void doDelete()
    {
        try
        {
            client.deleteGroup(new URI(target));
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
            if (client.addMember(new URI(target), new X500Principal(
                    memberID)))
            {
                msg("User " + memberID + " added to group " + target);
            }
            else
            {
                logger.error("User " + memberID
                        + " cannot be added to the group " + target);
                System.exit(INIT_STATUS);
            }
        }
        catch (Exception e)
        {
            logger.error("failed add user " + memberID);
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
            if (client.removeMember(new URI(target), new X500Principal(
                    memberID)))
            {
                msg("User " + memberID + " removed from group " + target);
            }
            else
            {
                logger.error("User " + memberID
                        + " cannot be removed from the group " + target);
                System.exit(INIT_STATUS);
            }
        }
        catch (Exception e)
        {
            logger.error("failed remove user " + memberID);
            logger.error("reason: " + e.getMessage());
            System.exit(NET_STATUS);
        }
    }
    
    /**
     * Executes user group membership check command
     */
    private void doCheckMember()
    {
        try
        {
            User user = client.getMember(new URI(target), new X500Principal(
                    memberID));
            if (user == null)
            {
                msg("User ID (" + memberID + ") IS NOT member of group " + target);
            }
            else
            {
                msg("User ID (" + memberID + ") IS member of group " + target);
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
     * Executes list groups user is member of command
     */
    private void doListMemberGroups()
    {
        try
        {
            User user = client
                    .getGMSMembership(new X500Principal(target));
            if (user == null)
            {
                msg("User: " + target + " not found");
                return;
            }
            msg("User ID: " + user.getID());
            msg("Group Membership (Group ID / Group Descripton):");
            for (Group group : user.getGMSMemberships())
            {
                String description = "N/A";
                for (ElemProperty prop : group.getProperties())
                {
                    if (GmsConsts.PROPERTY_GROUP_DESCRIPTION.equals(prop
                            .getPropertyURI()))
                    {
                        description = prop.getPropertyValue();
                        break;
                    }
                }
                msg("     " + group.getID() + " / " + description);
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
        try
        {
            Collection<Group> groups = client
                    .getGroups(new X500Principal(target));
            if ((groups == null) || groups.isEmpty())
            {
                msg("User: " + target + " owns 0 groups");
                return;
            }
            msg("User ID: " + target);
            msg("Group Membership (Group ID / Group Descripton):");
            for (Group group : groups)
            {
                String description = "N/A";
                for (ElemProperty prop : group.getProperties())
                {
                    if (GmsConsts.PROPERTY_GROUP_DESCRIPTION.equals(prop
                            .getPropertyURI()))
                    {
                        description = prop.getPropertyValue();
                        break;
                    }
                }
                msg("     " + group.getID() + " / " + description);
            }
            msg("Total groups: " + groups.size());

        }
        catch (Exception e)
        {
            logger.error("failed to list groups of member " + target);
            logger.error("reason: " + e.getMessage());
            System.exit(NET_STATUS);
        }
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
                "   [--add=<User ID> |--remove=<User ID> | --check=<User ID>]                                ",
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
