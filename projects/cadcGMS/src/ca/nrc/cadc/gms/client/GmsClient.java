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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.security.AccessControlException;

import org.apache.log4j.Logger;

import ca.nrc.cadc.gms.Group;
import ca.nrc.cadc.gms.GroupReader;
import ca.nrc.cadc.gms.GroupWriter;
import ca.nrc.cadc.gms.UserMembershipReader;
import ca.nrc.cadc.gms.ReaderException;
import ca.nrc.cadc.gms.User;
import ca.nrc.cadc.gms.UserReader;

public class GmsClient
{
    private static Logger logger = Logger.getLogger(GmsClient.class);
    private URL baseServiceURL;

    /**
     * Default, and only available constructor.
     * 
     * @param baseServiceURL
     *            The
     */
    public GmsClient(final URL baseServiceURL)
    {
        this.baseServiceURL = baseServiceURL;
    }

    /**
     * Obtain the Member for the given Group and Member IDs.
     * 
     * @param groupID
     *            The Group ID to check.
     * @param memberID
     *            The Member ID to check.
     * @return The User member instance for the given Member's ID.
     * @throws IllegalArgumentException
     *             If the Group ID, Member ID, or accepted baseServiceURL,
     *             or any combination of them produces an error.
     * @throws AccessControlException 
     *             If user not allow to access the resource
     * @throws MalformedURLException 
     *             If the arguments generate a bad URL
     * @throws ReaderException
     *             If the URL's response could not be read.
     * @throws IOException
     *             For any unforeseen I/O errors.
     * 
     */
    public User getMember(final String groupID, final String memberID)
            throws IllegalArgumentException
    {
        final StringBuilder resourcePath = new StringBuilder(64);

        try
        {
            resourcePath.append("/members/");
            resourcePath.append(URLEncoder.encode(memberID, "UTF-8"));
            resourcePath.append("/");
            resourcePath.append(URLEncoder.encode(groupID, "UTF-8"));

            final URL resourceURL = new URL(getBaseServiceURL()
                    + resourcePath.toString());
            logger.debug("getMember(), URL=" + resourceURL);
            HttpURLConnection connection = (HttpURLConnection) openConnection(resourceURL);
            connection.setRequestMethod("GET");
            connection.setUseCaches(false);
            connection.setDoInput(true);
            connection.setDoOutput(false);
            connection.connect();

            String responseMessage = connection.getResponseMessage();
            int responseCode = connection.getResponseCode();
            logger.debug("getMember(), response code: "
                    + responseCode);
            logger.debug("getMember(), response message: "
                    + responseMessage);

            switch (responseCode)
            {
                case HttpURLConnection.HTTP_OK:
                    return constructUser(connection);
                case HttpURLConnection.HTTP_NOT_FOUND:
                    return null;
                case HttpURLConnection.HTTP_FORBIDDEN:
                    throw new AccessControlException(responseMessage);
                default:
                    throw new RuntimeException(
                            "Unexpected failure mode: "
                                    + responseMessage + "("
                                    + responseCode + ")");
            }

        }
        catch (ReaderException e)
        {
            final String message = String.format(
                    "The supplied URL (%s) cannot be read from.",
                    getBaseServiceURL().toExternalForm()
                            + resourcePath.toString());
            logger.debug("Failed to get member", e);
            throw new IllegalArgumentException(message, e);
        }
        catch (IOException e)
        {
            final String message = String.format(
                    "Client BUG: The supplied URL (%s) cannot "
                            + "be hit.", getBaseServiceURL()
                            .toExternalForm()
                            + resourcePath.toString());
            logger.debug("Failed to get member", e);
            throw new IllegalArgumentException(message, e);
        }
    }

    /**
     * Returns true of the user identified by memberID is a member of the
     * group identified by groupID. Otherwise, false is returned.
     * 
     * @param groupID
     *            The group identifier.
     * @param memberID
     *            The member identifier.
     * @return true if the user is a member of the group.
     * @throws IllegalArgumentException
     *             If the Group ID, Member ID, or accepted baseServiceURL,
     *             or any combination of them produces an error.
     * @throws AccessControlException 
     *             If user not allow to access the resource
     * @throws MalformedURLException 
     *             If the arguments generate a bad URL
     * @throws ReaderException
     *             If the URL's response could not be read.
     * @throws IOException
     *             For any unforeseen I/O errors.
     */
    public boolean isMember(String groupID, String memberID)
    {
        final StringBuilder resourcePath = new StringBuilder(64);
        try
        {
            resourcePath.append("/groups/");
            resourcePath.append(URLEncoder.encode(groupID, "UTF-8"));
            resourcePath.append("/");
            resourcePath.append(URLEncoder.encode(memberID, "UTF-8"));

            final URL resourceURL = new URL(getBaseServiceURL()
                    + resourcePath.toString());
            logger.debug("isMember(), URL=" + resourceURL);
            HttpURLConnection connection = (HttpURLConnection) resourceURL
                    .openConnection();
            connection.setRequestMethod("HEAD");
            connection.setUseCaches(false);
            connection.setDoInput(true);
            connection.setDoOutput(false);
            connection.connect();

            String responseMessage = connection.getResponseMessage();
            int responseCode = connection.getResponseCode();
            logger.debug("isMember(), response code: "
                    + responseCode);
            logger.debug("isMember(), response message: "
                    + responseMessage);

            switch (responseCode)
            {
                case HttpURLConnection.HTTP_OK:
                    return true;
                case HttpURLConnection.HTTP_NOT_FOUND:
                    return false;
                case HttpURLConnection.HTTP_FORBIDDEN:
                    throw new AccessControlException(responseMessage);
                default:
                    throw new RuntimeException(
                            "Unexpected failure mode: "
                                    + responseMessage + "("
                                    + responseCode + ")");
            }
        }
        catch (MalformedURLException e)
        {
            final String message = String.format(
                    "The supplied URL (%s) cannot be used.",
                    getBaseServiceURL().toExternalForm()
                            + resourcePath.toString());
            logger.debug("Failed to check membership", e);
            throw new IllegalArgumentException(message, e);
        }
        catch (IOException e)
        {
            final String message = String.format(
                    "Client BUG: The supplied URL (%s) cannot "
                            + "be hit.", getBaseServiceURL()
                            .toExternalForm()
                            + resourcePath.toString());
            logger.debug("Failed to check membership", e);
            throw new IllegalArgumentException(message, e);
        }
    }    

    /**
     * Get the group identified by groupID. Associated members will be
     * included.
     * 
     * @param groupID
     *            Identifies the group.
     * @return The group, or null if not found.
     *      * @throws IllegalArgumentException
     *             If the Group ID, Member ID, or accepted baseServiceURL,
     *             or any combination of them produces an error.
     * @throws AccessControlException 
     *             If user not allow to access the resource
     * @throws MalformedURLException 
     *             If the arguments generate a bad URL
     * @throws ReaderException
     *             If the URL's response could not be read.
     * @throws IOException
     *             For any unforeseen I/O errors.
     *                  
     */
    public Group getGroup(String groupID)
    {
        final StringBuilder resourcePath = new StringBuilder(64);

        try
        {
            resourcePath.append("/groups/");
            resourcePath.append(URLEncoder.encode(groupID, "UTF-8"));

            final URL resourceURL = new URL(getBaseServiceURL()
                    + resourcePath.toString());
            logger.debug("getGroup(), URL=" + resourceURL);
            HttpURLConnection connection = (HttpURLConnection) openConnection(resourceURL);
            connection.setRequestMethod("GET");
            connection.setUseCaches(false);
            connection.setDoInput(true);
            connection.setDoOutput(false);
            connection.connect();


            String responseMessage = connection.getResponseMessage();
            int responseCode = connection.getResponseCode();
            logger.debug("getGroup(), response code: "
                    + responseCode);
            logger.debug("getGroup(), response message: "
                    + responseMessage);

            switch (responseCode)
            {
                case HttpURLConnection.HTTP_OK:
                    return constructGroup(connection);
                case HttpURLConnection.HTTP_NOT_FOUND:
                    return null;
                case HttpURLConnection.HTTP_FORBIDDEN:
                    throw new AccessControlException(responseMessage);
                default:
                    throw new RuntimeException(
                            "Unexpected failure mode: "
                                    + responseMessage + "("
                                    + responseCode + ")");
            }

        }
        catch (ReaderException e)
        {
            final String message = String.format(
                    "The supplied URL (%s) cannot be read from.",
                    getBaseServiceURL().toExternalForm()
                            + resourcePath.toString());
            logger.debug("Failed to get group", e);
            throw new IllegalArgumentException(message, e);
        }
        catch (IOException e)
        {
            final String message = String.format(
                    "Client BUG: The supplied URL (%s) cannot "
                            + "be hit.", getBaseServiceURL()
                            .toExternalForm()
                            + resourcePath.toString());
            logger.debug("Failed to get group", e);
            throw new IllegalArgumentException(message, e);
        }
    }

    /**
     * Create the group identified by groupID.
     * 
     * @param group
     *            Group to create. The groupID is assigned by the service
     *            when this parameter is null.
     * @return The newly created group group
     * @throws IllegalArgument
     *             Exception If the Group ID, or accepted baseServiceURL,
     *             or any combination of them produces an error.
     * @throws AccessControlException 
     *             If user not allow to access the resource
     * @throws MalformedURLException 
     *             If the arguments generate a bad URL
     * @throws ReaderException
     *             If the URL's response could not be read.
     * @throws IOException
     *             For any unforeseen I/O errors.
     */
    public Group createGroup(Group group) throws IllegalArgumentException
    {
        final StringBuilder resourcePath = new StringBuilder(64);
        try
        {
            if (group == null)
            {
                // user does not have the group created. Through a POST.
                // the server generates one and returns it to the user
                resourcePath.append("/groups");

                final URL resourceURL = new URL(getBaseServiceURL()
                        + resourcePath.toString());
                logger.debug("createGroup(), URL=" + resourceURL);
                HttpURLConnection connection = (HttpURLConnection) openConnection(resourceURL);
                connection.setRequestMethod("POST");
                connection.setDoInput(true);
                connection.setDoOutput(true);
                connection.setUseCaches(false);
                connection.connect();
                
                String responseMessage = connection.getResponseMessage();
                int responseCode = connection.getResponseCode();
                logger.debug("createGroup(), response code: " + responseCode);
                logger.debug("createGroup(), response message: "
                        + responseMessage);

                switch (responseCode)
                {
                    case HttpURLConnection.HTTP_OK:
                        return constructGroup(connection);
                    case HttpURLConnection.HTTP_CONFLICT:
                        // break intentionally left out
                    case HttpURLConnection.HTTP_NOT_FOUND:
                        // parent node not found
                        // break intentionally left out
                    case HttpURLConnection.HTTP_BAD_REQUEST:
                        // duplicate group
                        throw new IllegalArgumentException(responseMessage);
                    case HttpURLConnection.HTTP_FORBIDDEN:
                        throw new AccessControlException(responseMessage);
                    default:
                        throw new RuntimeException(
                                "Unexpected failure mode: " + responseMessage
                                        + "(" + responseCode + ")");
                }
            }
            else
            {
                resourcePath.append("/groups/");
                resourcePath.append(URLEncoder.encode(group
                        .getGMSGroupID(), "UTF-8"));

                final URL resourceURL = new URL(getBaseServiceURL()
                        + resourcePath.toString());
                logger.debug("createGroup(), URL=" + resourceURL);
                HttpURLConnection connection = (HttpURLConnection) openConnection(resourceURL);
                connection.setRequestMethod("PUT");
                connection.setDoInput(true);
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "text/xml");
                connection.setUseCaches(false);

                OutputStreamWriter out = new OutputStreamWriter(
                        connection.getOutputStream());
                GroupWriter.write(group, out);
                out.close();

                String responseMessage = connection.getResponseMessage();
                int responseCode = connection.getResponseCode();
                logger.debug("createGroup(), response code: "
                        + responseCode);
                logger.debug("createGroup(), response message: "
                        + responseMessage);

                switch (responseCode)
                {
                    case HttpURLConnection.HTTP_CREATED:
                        return constructGroup(connection);
                    case HttpURLConnection.HTTP_CONFLICT:
                        // break intentionally left out
                    case HttpURLConnection.HTTP_NOT_FOUND:
                        // parent node not found
                        // break intentionally left out
                    case HttpURLConnection.HTTP_BAD_REQUEST:
                        // duplicate group
                        throw new IllegalArgumentException(
                                responseMessage);
                    case HttpURLConnection.HTTP_FORBIDDEN:
                        throw new AccessControlException(responseMessage);
                    default:
                        throw new RuntimeException(
                                "Unexpected failure mode: "
                                        + responseMessage + "("
                                        + responseCode + ")");
                }

            }

        }
        catch (ReaderException e)
        {
            final String message = String.format(
                    "The supplied URL (%s) cannot be read from.",
                    getBaseServiceURL().toExternalForm()
                            + resourcePath.toString());
            logger.debug("Failed to create group", e);
            throw new IllegalStateException(message, e);
        }
        catch (IOException e)
        {
            final String message = String.format(
                    "Client BUG: The supplied URL (%s) cannot "
                            + "be hit.", getBaseServiceURL()
                            .toExternalForm()
                            + resourcePath.toString());
            logger.debug("Failed to create group", e);
            throw new IllegalStateException(message, e);
        }
    }

    /**
     * Deletes the group identified by groupID.
     * 
     * @param groupID
     *            Identifies the group.
     * @throws IllegalArgument
     *             Exception If the Group ID, or accepted baseServiceURL,
     *             or any combination of them produces an error.
     * @throws AccessControlException 
     *             If user not allow to access the resource
     * @throws MalformedURLException 
     *             If the arguments generate a bad URL
     * @throws ReaderException
     *             If the URL's response could not be read.
     * @throws IOException
     *             For any unforeseen I/O errors.
     */
    public void deleteGroup(String groupID)
            throws IllegalArgumentException
    {
        final StringBuilder resourcePath = new StringBuilder(64);
        try
        {
            resourcePath.append("/groups/");
            resourcePath.append(URLEncoder.encode(groupID, "UTF-8"));

            final URL resourceURL = new URL(getBaseServiceURL()
                    + resourcePath.toString());
            logger.debug("deleteGroup(), URL=" + resourceURL);
            HttpURLConnection connection = (HttpURLConnection) openConnection(resourceURL);
            connection.setRequestMethod("DELETE");
            connection.setUseCaches(false);
            connection.setDoInput(true);
            connection.setDoOutput(false);
            connection.connect();

            String responseMessage = connection.getResponseMessage();
            int responseCode = connection.getResponseCode();
            logger.debug("deleteGroup(), response code: " + responseCode);
            logger.debug("deleteGroup(), response message: "
                    + responseMessage);

            switch (responseCode)
            {
                case HttpURLConnection.HTTP_OK:
                    return;
                case HttpURLConnection.HTTP_CONFLICT:
                    // break intentionally left out
                case HttpURLConnection.HTTP_NOT_FOUND:
                    // parent node not found
                    // break intentionally left out
                case HttpURLConnection.HTTP_BAD_REQUEST:
                    // duplicate group
                    throw new IllegalArgumentException(responseMessage);
                case HttpURLConnection.HTTP_FORBIDDEN:
                    throw new AccessControlException(responseMessage);
                default:
                    throw new RuntimeException(
                            "Unexpected failure mode: " + responseMessage
                                    + "(" + responseCode + ")");
            }
        }
        catch (MalformedURLException e)
        {
            final String message = String.format(
                    "The supplied URL (%s) cannot be used.",
                    getBaseServiceURL().toExternalForm()
                            + resourcePath.toString());
            logger.debug("Failed to delete group", e);
            throw new IllegalArgumentException(message, e);
        }
        catch (IOException e)
        {
            final String message = String.format(
                    "Client BUG: The supplied URL (%s) cannot "
                            + "be hit.", getBaseServiceURL()
                            .toExternalForm()
                            + resourcePath.toString());
            logger.debug("Failed to delete group", e);
            throw new IllegalArgumentException(message, e);
        }
    }

    /**
     * Updates the group identified by groupID.
     * 
     * @param group
     *            Group to set. Cannot be null.
     * @throws IllegalArgumentException
     *             If the Group ID, or accepted baseServiceURL, or any
     *             combination of them produces an error.
     * @throws AccessControlException 
     *             If user not allow to access the resource
     * @throws MalformedURLException 
     *             If the arguments generate a bad URL
     * @throws ReaderException
     *             If the URL's response could not be read.
     * @throws IOException
     *             For any unforeseen I/O errors.
     */
    public Group setGroup(Group group) throws IllegalArgumentException
    {
        final StringBuilder resourcePath = new StringBuilder(64);
        try
        {
            resourcePath.append("/groups/");
            resourcePath.append(URLEncoder.encode(group.getGMSGroupID(),
                    "UTF-8"));

            final URL resourceURL = new URL(getBaseServiceURL()
                    + resourcePath.toString());
            logger.debug("setGroup(), URL=" + resourceURL);
            HttpURLConnection connection = (HttpURLConnection) openConnection(resourceURL);
            connection.setRequestMethod("POST");
            connection.setDoInput(true);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "text/xml");
            connection.setUseCaches(false);

            OutputStreamWriter out = new OutputStreamWriter(connection
                    .getOutputStream());
            GroupWriter.write(group, out);
            out.close();

            String responseMessage = connection.getResponseMessage();
            int responseCode = connection.getResponseCode();
            logger.debug("setGroup(), response code: " + responseCode);
            logger.debug("setGroup(), response message: "
                    + responseMessage);

            switch (responseCode)
            {
                case HttpURLConnection.HTTP_OK:
                    return constructGroup(connection);
                case HttpURLConnection.HTTP_CONFLICT:
                    // break intentionally left out
                case HttpURLConnection.HTTP_NOT_FOUND:
                    // parent node not found
                    // break intentionally left out
                case HttpURLConnection.HTTP_BAD_REQUEST:
                    // duplicate group
                    throw new IllegalArgumentException(responseMessage);
                case HttpURLConnection.HTTP_FORBIDDEN:
                    throw new AccessControlException(responseMessage);
                default:
                    throw new RuntimeException(
                            "Unexpected failure mode: " + responseMessage
                                    + "(" + responseCode + ")");
            }

        }
        catch (ReaderException e)
        {
            final String message = String.format(
                    "The supplied URL (%s) cannot be read from.",
                    getBaseServiceURL().toExternalForm()
                            + resourcePath.toString());
            logger.debug("Failed to set group", e);
            throw new IllegalStateException(message, e);
        }
        catch (IOException e)
        {
            final String message = String.format(
                    "Client BUG: The supplied URL (%s) cannot "
                            + "be hit.", getBaseServiceURL()
                            .toExternalForm()
                            + resourcePath.toString());
            logger.debug("Failed to seet group", e);
            throw new IllegalStateException(message, e);
        }
    }

    /**
     * Get groups the the user is member of.
     * 
     * @param userID
     *            Identifies the user.
     * @return The User with all the groups he's member of, 
     * or null if not found.
     *      * @throws IllegalArgumentException
     *             If the User ID, or accepted baseServiceURL,
     *             or any combination of them produces an error.
     * @throws AccessControlException 
     *             If user not allow to access the resource
     * @throws MalformedURLException 
     *             If the arguments generate a bad URL
     * @throws ReaderException
     *             If the URL's response could not be read.
     * @throws IOException
     *             For any unforeseen I/O errors.
     *                  
     */
    public User getGMSMembership(String userID)
    {
        final StringBuilder resourcePath = new StringBuilder(64);

        try
        {
            resourcePath.append("/members/");
            resourcePath.append(URLEncoder.encode(userID, "UTF-8"));
            final URL resourceURL = new URL(getBaseServiceURL()
                    + resourcePath.toString());
            logger.debug("getGMSMembership(), URL=" + resourceURL);
            HttpURLConnection connection = (HttpURLConnection) openConnection(resourceURL);
            connection.setRequestMethod("GET");
            connection.setUseCaches(false);
            connection.setDoInput(true);
            connection.setDoOutput(false);
            connection.connect();


            String responseMessage = connection.getResponseMessage();
            int responseCode = connection.getResponseCode();
            logger.debug("getGMSMembership(), response code: "
                    + responseCode);
            logger.debug("getGMSMembership(), response message: "
                    + responseMessage);

            switch (responseCode)
            {
                case HttpURLConnection.HTTP_OK:
                    return constructMember(connection);
                case HttpURLConnection.HTTP_NOT_FOUND:
                    return null;
                case HttpURLConnection.HTTP_FORBIDDEN:
                    throw new AccessControlException(responseMessage);
                default:
                    throw new RuntimeException(
                            "Unexpected failure mode: "
                                    + responseMessage + "("
                                    + responseCode + ")");
            }

        }
        catch (ReaderException e)
        {
            final String message = String.format(
                    "The supplied URL (%s) cannot be read from.",
                    getBaseServiceURL().toExternalForm()
                            + resourcePath.toString());
            logger.debug("Failed to get group", e);
            throw new IllegalArgumentException(message, e);
        }
        catch (IOException e)
        {
            final String message = String.format(
                    "Client BUG: The supplied URL (%s) cannot "
                            + "be hit.", getBaseServiceURL()
                            .toExternalForm()
                            + resourcePath.toString());
            logger.debug("Failed to get group", e);
            throw new IllegalArgumentException(message, e);
        }
    }
    
    /**
     * Build a User member from the given URL.
     * 
     * @param connection
     *            The HttpURLConnection used to retrieve data. Caller must
     *            call and check the return code of the connection.
     * @return User instance, or null if none available.
     * @throws ReaderException
     *             If the URL's response could not be read.
     * @throws IOException
     *             For any unforeseen I/O errors.
     */
    private User constructUser(final HttpURLConnection connection)
            throws IOException, ReaderException
    {
        final User member;
        InputStream inputStream = connection.getInputStream();

        try
        {
            member = UserReader.read(inputStream);
        }
        finally
        {
            try
            {
                if (inputStream != null)
                {
                    inputStream.close();
                }
            }
            catch (IOException e)
            {
                // Don't worry about it.
            }
        }

        return member;
    }
    
    /**
     * Build a User with the groups it is member of from the given URL.
     * 
     * @param connection
     *            The HttpURLConnection used to retrieve data. Caller must
     *            call and check the return code of the connection.
     * @return User instance, or null if none available.
     * @throws ReaderException
     *             If the URL's response could not be read.
     * @throws IOException
     *             For any unforeseen I/O errors.
     */
    private User constructMember(final HttpURLConnection connection)
            throws IOException, ReaderException
    {
        final User member;
        InputStream inputStream = connection.getInputStream();

        try
        {
            member = UserMembershipReader.read(inputStream);
        }
        finally
        {
            try
            {
                if (inputStream != null)
                {
                    inputStream.close();
                }
            }
            catch (IOException e)
            {
                // Don't worry about it.
            }
        }

        return member;
    }    

    /**
     * Build a Group from the given URL.
     * 
     * @param connection
     *            The HttpURLConnection used to retrieve data. Caller must
     *            call and check the return code of the connection.
     * @return Group instance, or null if none available.
     * @throws ReaderException
     *             If the URL's response could not be read.
     * @throws IOException
     *             For any unforeseen I/O errors.
     */
    private Group constructGroup(final HttpURLConnection connection)
            throws IOException, ReaderException
    {
        final Group group;
        InputStream inputStream = connection.getInputStream();

        try
        {
            group = GroupReader.read(inputStream);
        }
        finally
        {
            try
            {
                if (inputStream != null)
                {
                    inputStream.close();
                }
            }
            catch (IOException e)
            {
                // Don't worry about it.
            }
        }

        return group;
    }

    /**
     * Open and HttpURLConnection. Used primarily as a hookup for the unit
     * testing.
     * 
     * @param url
     * @return UTLConnection returns an open connection to URL
     * @throws IOException
     */
    protected URLConnection openConnection(final URL url)
            throws IOException
    {
        return url.openConnection();
    }

    public URL getBaseServiceURL()
    {
        return baseServiceURL;
    }

    public void setBaseServiceURL(URL baseServiceURL)
    {
        this.baseServiceURL = baseServiceURL;
    }

}
