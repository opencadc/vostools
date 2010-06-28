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

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Collection;

import ca.nrc.cadc.gms.Group;
import ca.nrc.cadc.gms.GroupReader;
import ca.nrc.cadc.gms.ReaderException;
import ca.nrc.cadc.gms.User;
import ca.nrc.cadc.gms.UserReader;


public class GmsClient
{
    private URL baseServiceURL;

    /**
     * Default, and only available constructor.
     *
     * @param baseServiceURL    The
     */
    public GmsClient(final URL baseServiceURL)
    {
        this.baseServiceURL = baseServiceURL;
    }
    
    /**
     * Obtain the Member for the given Group and Member IDs.
     *
     * @param groupID       The Group ID to check.
     * @param memberID      The Member ID to check.
     * @return              The User member instance for the given Member's ID.
     * @throws IllegalArgumentException
     *                  If the Group ID, Member ID, or accepted baseServiceURL,
     *                  or any combination of them produces an error.
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
            
            final URL resourceURL = new URL(getBaseServiceURL() +
                                            resourcePath.toString());
            return constructUser(resourceURL);
        }
        catch (MalformedURLException e)
        {
            final String message =
                    String.format("The supplied URL (%s) cannot be used.",
                                  getBaseServiceURL().toExternalForm()
                                  + resourcePath.toString());
            throw new IllegalArgumentException(message, e);
        }
        catch (ReaderException e)
        {
            final String message =
                    String.format("The supplied URL (%s) cannot be read from.",
                                  getBaseServiceURL().toExternalForm()
                                  + resourcePath.toString());            
            throw new IllegalArgumentException(message, e);
        }
        catch (IOException e)
        {
            final String message =
                    String.format("Client BUG: The supplied URL (%s) cannot "
                                  + "be hit.",
                                  getBaseServiceURL().toExternalForm()
                                  + resourcePath.toString());
            throw new IllegalArgumentException(message, e);
        }
    }
    
    /**
     * Returns true of the user identified by memberID is a member of the group
     * identified by groupID.  Otherwise, false is returned.
     * 
     * @param groupID The group identifier.
     * @param memberID The member identifier.
     * @return true if the user is a member of the group.
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
            
            final URL resourceURL = new URL(getBaseServiceURL() + resourcePath.toString());            
            HttpURLConnection connection = (HttpURLConnection) resourceURL.openConnection();
            connection.setRequestMethod("HEAD");
            connection.connect();

            if (connection.getResponseCode() == 200)
                return true;
            return false;
        }
        catch (MalformedURLException e)
        {
            final String message =
                    String.format("The supplied URL (%s) cannot be used.",
                                  getBaseServiceURL().toExternalForm()
                                  + resourcePath.toString());
            throw new IllegalArgumentException(message, e);
        }
        catch (IOException e)
        {
            final String message =
                    String.format("Client BUG: The supplied URL (%s) cannot "
                                  + "be hit.",
                                  getBaseServiceURL().toExternalForm()
                                  + resourcePath.toString());
            throw new IllegalArgumentException(message, e);
        }
    }
    
    /**
     * Returns a collection of groups in which the user identified by memberID is
     * a member.
     * 
     * @param memberID Identified the user.
     * @return The list of groups in which the user is a member.
     */
    public Collection<Group> getMemberships(String memberID)
    {
        // TODO: is a new resource needed to implement this?
        throw new UnsupportedOperationException("getMemberships() not yet implemented.");
    }
    
    /**
     * Get the group identified by groupID.  Associated members will be included.
     * 
     * @param groupID Identifies the group.
     * @return The group, or null if not found.
     */
    public Group getGroup(String groupID)
    {
        final StringBuilder resourcePath = new StringBuilder(64);


        try
        {
            resourcePath.append("/groups/");
            resourcePath.append(URLEncoder.encode(groupID, "UTF-8"));
            
            final URL resourceURL = new URL(getBaseServiceURL() +
                                            resourcePath.toString());
            return constructGroup(resourceURL);
        }
        catch (MalformedURLException e)
        {
            final String message =
                    String.format("The supplied URL (%s) cannot be used.",
                                  getBaseServiceURL().toExternalForm()
                                  + resourcePath.toString());
            throw new IllegalArgumentException(message, e);
        }
        catch (ReaderException e)
        {
            final String message =
                    String.format("The supplied URL (%s) cannot be read from.",
                                  getBaseServiceURL().toExternalForm()
                                  + resourcePath.toString());
            throw new IllegalArgumentException(message, e);
        }
        catch (IOException e)
        {
            final String message =
                    String.format("Client BUG: The supplied URL (%s) cannot "
                                  + "be hit.",
                                  getBaseServiceURL().toExternalForm()
                                  + resourcePath.toString());
            throw new IllegalArgumentException(message, e);
        }
    }

    /**
     * Returns a collection of members belonging to the group identified by
     * the specified groupID.
     * 
     * @param groupID Identifies the group.
     * @return The list of members in this group.
     */
    public Collection<User> getGroupMembers(String groupID)
    {
        final StringBuilder resourcePath = new StringBuilder(64);


        try
        {
            resourcePath.append("/groups/");
            resourcePath.append(URLEncoder.encode(groupID, "UTF-8"));
            resourcePath.append("/members");
            
            final URL resourceURL = new URL(getBaseServiceURL() +
                                            resourcePath.toString());
            Group group = constructGroup(resourceURL);
            return group.getMembers();
        }
        catch (MalformedURLException e)
        {
            final String message =
                    String.format("The supplied URL (%s) cannot be used.",
                                  getBaseServiceURL().toExternalForm()
                                  + resourcePath.toString());
            throw new IllegalArgumentException(message, e);
        }
        catch (ReaderException e)
        {
            final String message =
                    String.format("The supplied URL (%s) cannot be read from.",
                                  getBaseServiceURL().toExternalForm()
                                  + resourcePath.toString());
            throw new IllegalArgumentException(message, e);
        }
        catch (IOException e)
        {
            final String message =
                    String.format("Client BUG: The supplied URL (%s) cannot "
                                  + "be hit.",
                                  getBaseServiceURL().toExternalForm()
                                  + resourcePath.toString());
            throw new IllegalArgumentException(message, e);
        }
    }

    /**
     * Build a User member from the given URL.
     *
     * @param resourceURL   The URL to submit a GET to to obtain the User.
     * @return User instance, or null if none available.
     * @throws ReaderException  If the URL's response could not be read.
     * @throws IOException      For any unforeseen I/O errors.
     */
    private User constructUser(final URL resourceURL)
            throws IOException, ReaderException
    {
        final User member;
        InputStream inputStream = null;

        try
        {
            inputStream = getInputStream(resourceURL);
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
     * Build a Group from the given URL.
     *
     * @param resourceURL   The URL to submit a GET to to obtain the User.
     * @return Group instance, or null if none available.
     * @throws ReaderException  If the URL's response could not be read.
     * @throws IOException      For any unforeseen I/O errors.
     */
    private Group constructGroup(final URL resourceURL)
            throws IOException, ReaderException
    {
        final Group group;
        InputStream inputStream = null;

        try
        {
            inputStream = getInputStream(resourceURL);
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
     * Obtain a Stream from the given URL.
     *
     * @param resourceURL       The URL to obtain an InputStream to.
     * @return                  InputStream instance.
     * @throws IOException      If the Stream cannot be read from.
     */
    protected InputStream getInputStream(final URL resourceURL)
            throws IOException
    {
        return new BufferedInputStream(resourceURL.openStream());
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
