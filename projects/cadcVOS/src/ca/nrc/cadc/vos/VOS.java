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

package ca.nrc.cadc.vos;


/**
 * Holder of commonly used constants.
 * 
 * @author zhangsa
 *
 */
public class VOS
{
    //public static String XSD_FILE_NAME = "VOSpace-2.0.xsd"; // local xsd file name
    //public static String XSD_KEY = "http://www.ivoa.net/xml/VOSpace/v2.0";

    public static final String GMS_PROTOCOL = "https";
    
    // Enumeration for Node busy states
    public static enum NodeBusyState
    {
        notBusy ("N"),
        busyWithWrite ("W");
        
        private String value;
        
        private NodeBusyState(String value)
        {
            this.value = value;
        }
        
        public String getValue()
        {
            return value;
        }
        
        public static NodeBusyState getStateFromValue(String value)
        {
            if (value.equals(notBusy.getValue()))
            {
                return notBusy;
            }
            else if (value.equals(busyWithWrite.getValue()))
            {
                return busyWithWrite;
            }
            return null;
        }
    }

    public static enum Detail
    {
        min("min"),
        max("max"),
        properties("properties");

        private String value;

        private Detail(String value) { this.value = value; }

        public String getValue() { return value; }

        public static Detail getValue(String value)
        {
            if (value.equals(min.getValue()))
            {
                return min;
            }
            else if (value.equals(max.getValue()))
            {
                return max;
            }
            else if (value.equals(properties.getValue()))
            {
                return properties;
            }
            return null;
        }
    }
    
    /*
     * Default property delimiter for multi-valued properties
     */
    public static final String DEFAULT_PROPERTY_VALUE_DELIM = ",";
    
    /*
     * Standard Node Properties defined by the IVOA
     */
    
    // Denotes a name given to the resource
    public static final String PROPERTY_URI_TITLE = "ivo://ivoa.net/vospace/core#title"; 
    
    // Denotes an entity primarily responsible for making the resource
    public static final String PROPERTY_URI_CREATOR = "ivo://ivoa.net/vospace/core#creator";
    
    // Denotes the topic of the resource
    public static final String PROPERTY_URI_SUBJECT = "ivo://ivoa.net/vospace/core#subject";
    
    // Denotes an account of the resource
    public static final String PROPERTY_URI_DESCRIPTION = "ivo://ivoa.net/vospace/core#description";
    
    // Denotes an entity responsible for making the resource available
    public static final String PROPERTY_URI_PUBLISHER = "ivo://ivoa.net/vospace/core#publisher";
    
    // Denotes an entity responsible for making contributions to this resource
    public static final String PROPERTY_URI_CONTRIBUTOR = "ivo://ivoa.net/vospace/core#contributor";
    
    // timestamp of the last modification to the node metadata or stored bytes
    public static final String PROPERTY_URI_DATE = "ivo://ivoa.net/vospace/core#date";
    
    // Denotes the nature or genre of the resource
    public static final String PROPERTY_URI_TYPE = "ivo://ivoa.net/vospace/core#type";
    
    // Denotes the file format, physical medium, or dimensions of the resource
    public static final String PROPERTY_URI_FORMAT = "ivo://ivoa.net/vospace/core#format";
    
    // Denotes an unambiguous reference to the resource within a given context
    public static final String PROPERTY_URI_IDENTIFIER = "ivo://ivoa.net/vospace/core#identifier";
    
    // Denotes a related resource from which the described resource is derived
    public static final String PROPERTY_URI_SOURCE = "ivo://ivoa.net/vospace/core#source"; 
    
    // Denotes a language of the resource
    public static final String PROPERTY_URI_LANGUAGE = "ivo://ivoa.net/vospace/core#language"; 
    
    // Denotes a related resource
    public static final String PROPERTY_URI_RELATION = "ivo://ivoa.net/vospace/core#relation"; 
    
    // Denotes the spatial or temporal topic of the resource, the spatial applicability of the resource, or the jurisdiction under which the resource is relevant
    public static final String PROPERTY_URI_COVERAGE = "ivo://ivoa.net/vospace/core#coverage";
    
    // Denotes information about rights held in and over the resource
    public static final String PROPERTY_URI_RIGHTS = "ivo://ivoa.net/vospace/core#rights"; 
    
    // Denotes the amount of space available within a container
    public static final String PROPERTY_URI_AVAILABLESPACE = "ivo://ivoa.net/vospace/core#availableSpace";

    // SHALL be used as the protocol URI for a HTTP GET transfer
    public static final String PROTOCOL_HTTP_GET = "ivo://ivoa.net/vospace/core#httpget";
            
    // SHALL be used as the protocol URI for a HTTP PUT transfer
    public static final String PROTOCOL_HTTP_PUT = "ivo://ivoa.net/vospace/core#httpput";

    // SHALL be used as the protocol URI for a HTTPS GET transfer
    public static final String PROTOCOL_HTTPS_GET = "ivo://ivoa.net/vospace/core#httpsget";

    // SHALL be used as the protocol URI for a HTTPS PUT transfer
    public static final String PROTOCOL_HTTPS_PUT = "ivo://ivoa.net/vospace/core#httpsput";

    // SHALL be used as the view URI to indicate that a service will accept any view for an import operation
    public static final String VIEW_ANY = "ivo://ivoa.net/vospace/core#anyview";

    // SHALL be used as the view URI to import or export data as a binary file
    public static final String VIEW_BINARY = "ivo://ivoa.net/vospace/core#binaryview";

    // SHALL be used by a client to indicate that the service should choose the most appropriate view for a data export
    public static final String VIEW_DEFAULT = "ivo://ivoa.net/vospace/core#defaultview";
    
    /*
     * Standard Node Properties defined by the CADC
     */
    
    // The size of the resource 
    public static final String PROPERTY_URI_CONTENTLENGTH = "ivo://ivoa.net/vospace/core#length";

    // The quota of a Container Node.
    public static final String PROPERTY_URI_QUOTA = "ivo://ivoa.net/vospace/core#quota";
    
    // The content encoding of the resource
    public static final String PROPERTY_URI_CONTENTENCODING = "ivo://ivoa.net/vospace/core#encoding";
    
    // The MD5 Checksum of the resource
    public static final String PROPERTY_URI_CONTENTMD5 = "ivo://ivoa.net/vospace/core#MD5";
    
    // The groups who can read the resource
    public static final String PROPERTY_URI_GROUPREAD = "ivo://ivoa.net/vospace/core#groupread";
    public static final String PROPERTY_DELIM_GROUPREAD = " ";
    
    // The groups who can write to the resource
    public static final String PROPERTY_URI_GROUPWRITE = "ivo://ivoa.net/vospace/core#groupwrite";
    public static final String PROPERTY_DELIM_GROUPWRITE = " ";
    
    // Flag indicating if the Node is public (true/false)
    public static final String PROPERTY_URI_ISPUBLIC = "ivo://ivoa.net/vospace/core#ispublic";

    // proposed to support vofs: timestamp of the last modification to the stored bytes (DataNode only)
    public static final String PROPERTY_URI_CREATION_DATE = "ivo://ivoa.net/vospace/core#creationDate";
    
    /*
     * CADC Node Properties
     */
    // Flag indicating if the Node locked (true/false)
    public static final String PROPERTY_URI_ISLOCKED = "ivo://cadc.nrc.ca/vospace/core#islocked";
    
    /*
     * List of properties that are read-only by the user
     */
    public static final String[] READ_ONLY_PROPERTIES = new String[]
    {
        PROPERTY_URI_DATE,
        PROPERTY_URI_CREATOR,
        PROPERTY_URI_QUOTA,
        PROPERTY_URI_CONTENTLENGTH,
        PROPERTY_URI_CONTENTMD5
    };

}
