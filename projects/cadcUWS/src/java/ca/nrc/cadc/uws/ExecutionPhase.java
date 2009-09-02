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
 * The job is treated as a state machine with the Execution Phase naming the
 * state. The phases are;
 *
 * PENDING: the job is accepted by the service but not yet committed for
 *          execution by the client. In this state, the job quote can be read
 *          and evaluated. This is the state into which a job enters when it
 *          is first created.
 *
 * QUEUED: the job is committed for execution by the client but the service
 *         has not yet assigned it to a processor. No Results are produced in
 *         this phase.
 *
 * EXECUTING: the job has been assigned to a processor. Results may be produced
 *            at any time during this phase.
 *
 * COMPLETED: the execution of the job is over. The Results may be collected.
 *
 * ERROR: the job failed to complete. No further work will be done nor Results
 *        produced. Results may be unavailable or available but invalid; either
 *        way the Results should not be trusted.
 *
 * UNKNOWN: the job is in an unknown state.
 *
 * HELD: The job is HELD pending execution and will not automatically be
 *       executed (cf pending). 
 *
 * ABORTED: the job has been manually aborted by the user, or the system has
 *          aborted the job due to lack of or overuse of resources.
 * 
 */
public enum ExecutionPhase
{
    PENDING, QUEUED, EXECUTING, COMPLETED, ERROR, UNKNOWN, HELD, SUSPENDED,
    ABORTED
}
