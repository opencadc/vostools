/*
 ************************************************************************
 ****  C A N A D I A N   A S T R O N O M Y   D A T A   C E N T R E  *****
 *
 * (c) 2009.                            (c) 2009.
 * National Research Council            Conseil national de recherches
 * Ottawa, Canada, K1A 0R6              Ottawa, Canada, K1A 0R6
 * All rights reserved                  Tous droits reserves
 *
 * NRC disclaims any warranties         Le CNRC denie toute garantie
 * expressed, implied, or statu-        enoncee, implicite ou legale,
 * tory, of any kind with respect       de quelque nature que se soit,
 * to the software, including           concernant le logiciel, y com-
 * without limitation any war-          pris sans restriction toute
 * ranty of merchantability or          garantie de valeur marchande
 * fitness for a particular pur-        ou de pertinence pour un usage
 * pose.  NRC shall not be liable       particulier.  Le CNRC ne
 * in any event for any damages,        pourra en aucun cas etre tenu
 * whether direct or indirect,          responsable de tout dommage,
 * special or general, consequen-       direct ou indirect, particul-
 * tial or incidental, arising          ier ou general, accessoire ou
 * from the use of the software.        fortuit, resultant de l'utili-
 *                                      sation du logiciel.
 *
 *
 * @author jenkinsd
 * Aug 4, 2009 - 9:01:03 AM
 *
 *
 *
 ****  C A N A D I A N   A S T R O N O M Y   D A T A   C E N T R E  *****
 ************************************************************************
 */
package ca.nrc.cadc.uws;

import java.util.Collection;

/**
 * Service interface for job persistence. The implementation class name used 
 * must be configured as a context-param with key <code>ca.nrc.cadc.uws.JobPersistence</code>.
 */
public interface JobPersistence
{
    /**
     * Obtain a Job from the persistence layer.
     *
     * @param jobID     The job identifier.
     * @return          Job instance, or null if none found.
     */
    Job getJob(final long jobID);

    /**
     * Obtain a listing of Job instances.
     *
     * @return  Collection of Job instances, or empty Collection.  Never null.
     */
    Collection<Job> getJobs();

    /**
     * Persist the given Job.
     *
     * @param job       Job to persist.
     * @return          The persisted Job, complete with a surrogate key, if
     *                  necessary.
     */
    Job persist(final Job job);
}
