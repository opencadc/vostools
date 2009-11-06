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

package ca.nrc.cadc.tap.parser.adql;

import java.io.StringReader;
import java.util.List;

import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParserManager;
import net.sf.jsqlparser.statement.Statement;

import org.apache.log4j.Logger;

import ca.nrc.cadc.tap.parser.adql.config.AdqlConfig;
import ca.nrc.cadc.tap.parser.adql.converter.Converter;
import ca.nrc.cadc.tap.parser.adql.exception.AdqlException;
import ca.nrc.cadc.tap.parser.adql.validator.PlainSelectInfo;
import ca.nrc.cadc.tap.parser.adql.validator.SelectValidator;
import ca.nrc.cadc.tap.parser.adql.validator.Validator;

public class AdqlParser
{
    private static Logger log = Logger.getLogger(AdqlParser.class);

    private AdqlManager _manager;
    private List<TapSelectItem> _tapSelectItems = null;

    public AdqlParser(AdqlManager manager)
    {
        this._manager = manager;
    }

    public String parse(String adqlQueryStr) throws AdqlException
    {
        Statement validatedStatement = validate(adqlQueryStr);
        Statement sqlStatement = convert(validatedStatement);
        return sqlStatement.toString();
    }

    public Statement validate(String adqlQueryStr) throws AdqlException
    {
        Statement statement = null;

        StringReader sr = new StringReader(adqlQueryStr);
        CCJSqlParserManager sqlParser = new CCJSqlParserManager();
        try
        {
            statement = sqlParser.parse(sr);
            
            AdqlStatementVisitor statementVisitor = null;
            
            // If ExtraTables (e.g. Uploaded tables) exist, make conversion
            AdqlConfig config = _manager.getConfig();
            if (config.hasExtraTableMap())
            {
                ExtraTableConverter extraTableConverter = new ExtraTableConverter(config);
                statementVisitor = new AdqlStatementVisitor(extraTableConverter);
                statement.accept(statementVisitor);
            }
            
            Validator validator = _manager.getValidator();
            SelectValidator selectValidator = validator.getSelectValidator();
            statementVisitor = new AdqlStatementVisitor(selectValidator);
            statement.accept(statementVisitor);
            
            if (validator.hasException())
            {
                int num = validator.getExceptions().size();
                String cr = "\r\n";
                StringBuffer sb = new StringBuffer();
                sb.append("Validation failed. ").append(cr);
                sb.append("Total number of errors:").append(num).append(". ").append(cr);
                int i = 0;
                for (AdqlException ex : validator.getExceptions())
                {
                    i++;
                    sb.append("Error #").append(i).append(": ");
                    sb.append(ex.getMessage()).append(cr);
                }
                throw new AdqlException(sb.toString());
            } else
            {
                PlainSelectInfo plainSelectInfo = selectValidator.getPlainSelectInfo();
                _tapSelectItems = plainSelectInfo.getTapSelectItems();
                log.debug(_tapSelectItems);
            }

        } catch (JSQLParserException pe)
        {
            throw new AdqlException("Invalid query syntax.", pe);
        }
        return statement;
    }

    public Statement convert(Statement adqlStatement) throws AdqlException
    {
        Converter converter = _manager.getConverter();
        AdqlStatementVisitor statementVisitor = new AdqlStatementVisitor(converter);
        adqlStatement.accept(statementVisitor);
        return adqlStatement;
    }

    /**
     * This method can only be called after the validate() is done.
     * 
     * @param queryStr
     * @return
     * @throws AdqlException
     */
    public List<TapSelectItem> getTapSelectItems() throws AdqlException
    {
        return _tapSelectItems;
    }

}
