/******************************************************************************
 *
 *  Copyright (C) 2009                          Copyright (C) 2009
 *  National Research Council           Conseil national de recherches
 *  Ottawa, Canada, K1A 0R6                     Ottawa, Canada, K1A 0R6
 *  All rights reserved                         Tous droits reserves
 *
 *  NRC disclaims any warranties,       Le CNRC denie toute garantie
 *  expressed, implied, or statu-       enoncee, implicite ou legale,
 *  tory, of any kind with respect      de quelque nature que se soit,
 *  to the software, including          concernant le logiciel, y com-
 *  without limitation any war-         pris sans restriction toute
 *  ranty of merchantability or         garantie de valeur marchande
 *  fitness for a particular pur-       ou de pertinence pour un usage
 *  pose.  NRC shall not be liable      particulier.  Le CNRC ne
 *  in any event for any damages,       pourra en aucun cas etre tenu
 *  whether direct or indirect,         responsable de tout dommage,
 *  special or general, consequen-      direct ou indirect, particul-
 *  tial or incidental, arising         ier ou general, accessoire ou
 *  from the use of the software.       fortuit, resultant de l'utili-
 *                                                              sation du logiciel.
 *
 *
 *  This file is part of cadcUWS.
 *
 *  cadcUWS is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  cadcUWS is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public License
 *  along with cadcUWS.  If not, see <http://www.gnu.org/licenses/>.
 *
 ******************************************************************************/

package ca.nrc.cadc.uws;


/**
 * Job Attribute Enumeration to build an XML document.
 */
public enum JobAttribute
{
    JOB("job"),
    JOBS("jobs"),
    JOB_REF("jobref"),
    JOB_ID("jobId"),
    EXECUTION_PHASE("phase"),
    START_TIME("startTime"),
    END_TIME("endTime"),
    EXECUTION_DURATION("executionDuration"),
    DESTRUCTION_TIME("destruction"),
    QUOTE("quote"),
    OWNER_ID("ownerId"),
    RUN_ID("runId"),
    PARAMETERS("parameters"),
    PARAMETER("parameter"),
    RESULTS("results"),
    RESULT("result"),
    ERROR_SUMMARY("errorSummary"),
    ERROR_SUMMARY_MESSAGE("message"),
    ERROR_SUMMARY_DETAIL_LINK("detail"),
    MESSAGE("message"),
    DETAIL("detail"),
    JOB_INFO("jobInfo");


    private String attributeName;


    JobAttribute(final String attributeName)
    {
        this.attributeName = attributeName;
    }


    public String getAttributeName()
    {
        return attributeName;
    }
}
