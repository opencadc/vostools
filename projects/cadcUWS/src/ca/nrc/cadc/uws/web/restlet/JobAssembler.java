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

package ca.nrc.cadc.uws.web.restlet;

import org.restlet.data.Form;
import ca.nrc.cadc.uws.*;
import ca.nrc.cadc.uws.util.StringUtil;
import ca.nrc.cadc.date.DateUtil;

import java.net.MalformedURLException;
import java.text.DateFormat;
import java.text.ParseException;
import java.util.Date;
import java.util.Map;
import java.util.HashMap;
import java.net.URL;

import javax.security.auth.Subject;


/**
 * Simple class to assemble items from a Request into a job.
 */
public class JobAssembler
{
    private Form form;
    private Subject subject;

    public JobAssembler(final Form form, final Subject subject)
    {
        this.form = form;
        this.subject = subject;
    }


    /**
     * Assemble the Job from this assembler's Form.
     *
     * @return      A new Job instance.
     *
     * @throws MalformedURLException   If a URL cannot be created from the given
     *                              String.
     * @throws ParseException       If the given Dates cannot be parsed.
     */
    public Job assemble() throws MalformedURLException, ParseException
    {
        final Job job;
        final Map<String, String> valuesMap =
                new HashMap<String, String>(getForm().getValuesMap());
        final String phase = getForm().getFirstValue(
                JobAttribute.EXECUTION_PHASE.getAttributeName().toUpperCase());
        final ExecutionPhase executionPhase;

        if (StringUtil.hasText(phase))
        {
            executionPhase = ExecutionPhase.valueOf(phase.toUpperCase());
        }
        else
        {
            executionPhase = null;
        }

        final String duration = getForm().getFirstValue(
                JobAttribute.EXECUTION_DURATION.getAttributeName().
                        toUpperCase());
        final long durationTime;

        if (StringUtil.hasText(duration))
        {
            durationTime = Long.parseLong(duration);
        }
        else
        {
            durationTime = 0l;
        }

        final String owner = getForm().getFirstValue(
                JobAttribute.OWNER_ID.getAttributeName().toUpperCase());
        final String runID = getForm().getFirstValue(
                JobAttribute.RUN_ID.getAttributeName().toUpperCase());

        final DateFormat df =
                DateUtil.getDateFormat(DateUtil.IVOA_DATE_FORMAT,
                                       DateUtil.UTC);

        final String destruction = getForm().getFirstValue(
                JobAttribute.DESTRUCTION_TIME.getAttributeName().
                        toUpperCase());
        final Date destructionDate;

        if (StringUtil.hasText(destruction))
        {
            destructionDate = df.parse(destruction);
        }
        else
        {
            destructionDate = null;
        }

        final String quote = getForm().getFirstValue(
                JobAttribute.QUOTE.getAttributeName().toUpperCase());
        final Date quoteDate;

        if (StringUtil.hasText(quote))
        {
            quoteDate = df.parse(quote);
        }
        else
        {
            quoteDate = null;
        }

        final String start = getForm().getFirstValue(
                JobAttribute.START_TIME.getAttributeName().toUpperCase());
        final Date startDate;

        if (StringUtil.hasText(start))
        {
            startDate = df.parse(start);
        }
        else
        {
            startDate = null;
        }

        final String errorMessage = getForm().getFirstValue(
                JobAttribute.ERROR_SUMMARY_MESSAGE.getAttributeName().
                        toUpperCase());
        final String errorDocumentURL = getForm().getFirstValue(
                JobAttribute.ERROR_SUMMARY_DETAIL_LINK.getAttributeName().
                        toUpperCase());

        final ErrorSummary errorSummary;

        if (!StringUtil.hasText(errorMessage)
            && !StringUtil.hasText(errorDocumentURL))
        {
            errorSummary =
                    new ErrorSummary(errorMessage,
                                     StringUtil.hasText(errorDocumentURL)
                                     ? new URL(errorDocumentURL)
                                     : null);
        }
        else
        {
            errorSummary = null;
        }
        
        job = new Job(null, executionPhase, durationTime, destructionDate,
                      quoteDate, startDate, null, errorSummary, owner,
                      runID, null, null, subject);

        // Clear out those Request parameters that are pre-defined.
        for (final JobAttribute jobAttribute : JobAttribute.values())
        {
            valuesMap.remove(jobAttribute.getAttributeName().toUpperCase());
        }

        // The remaining values are Parameters to the Job.
        for (final Map.Entry<String, String> entry : valuesMap.entrySet())
        {
            job.addParameter(new Parameter(entry.getKey(),
                                           entry.getValue()));
        }

        return job;
    }

    public Form getForm()
    {
        return form;
    }
}
