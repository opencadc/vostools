package ca.nrc.cadc.vos;

import java.security.AccessControlException;

public interface NodeAuthorizer
{
    
    int checkReadAccess(Node node) throws AccessControlException;
    
    int checkWriteAccess(Node node) throws AccessControlException;
    
    int checkDeleteAccess(Node node) throws AccessControlException;
    
    int checkUpdateAccess(Node node) throws AccessControlException;

}
