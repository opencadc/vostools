package ca.nrc.cadc.gms.server.web;

import org.restlet.service.MetadataService;
import org.restlet.data.MediaType;


/**
 * Metadata service to add more extensions.
 */
public class CADCGMSMetadataService extends MetadataService
{
    public CADCGMSMetadataService()
    {
        super();
        addExtension("img", MediaType.IMAGE_ALL, true);
        addExtension("all", MediaType.ALL);
        addExtension(MediaType.TEXT_XML.getName(), MediaType.TEXT_XML, true);
    }
}