package ca.nrc.cadc.vos;

import javax.sql.DataSource;

public interface TestPersistence
{
    public abstract DataSource getDataSource();
    
    public abstract NodeDAO getNodeDAO(DataSource dataSource);
    
    public abstract String getVOSURIPrefix();
    
    public abstract String getRootContainerName();
    
    public abstract String getNodeOwner();

}
