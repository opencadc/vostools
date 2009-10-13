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


/*
 ************************************************************************
 ****  C A N A D I A N   A S T R O N O M Y   D A T A   C E N T R E  *****
 *
 * (c) 2008.                            (c) 2008.
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
 ****  C A N A D I A N   A S T R O N O M Y   D A T A   C E N T R E  *****
 ************************************************************************
 */
package ca.nrc.cadc.tap.parser.adql.converter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.select.AllColumns;
import net.sf.jsqlparser.statement.select.AllTableColumns;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.SelectItem;
import net.sf.jsqlparser.statement.select.SelectVisitor;
import net.sf.jsqlparser.statement.select.Union;

import org.apache.log4j.Logger;

import ca.nrc.cadc.tap.parser.adql.AdqlManager;
import ca.nrc.cadc.tap.parser.adql.config.AdqlConfig;

/**
 * A SelectVisitor that traverses the query to find and modify AllColumns
 * and AllTableColumns SelectItems.
 * 
 * @author Sailor Zhang
 * 
 * 
 */
public abstract class AllColumnsConverter implements SelectVisitor
{
    protected static Logger log = Logger.getLogger(AllColumnsConverter.class);
    
    protected AdqlConfig config;
    
    public void init(AdqlManager manager) {
		this.config = manager.getConfig();
	}

    /**
     * Replace * and schema.* with SelectItem
     * @param plainSelect PlainSelect
     */
    public void visit(PlainSelect plainSelect)
    {
        log.debug("visit(PlainSelect): " + plainSelect);

        List<SelectItem> selectItems = plainSelect.getSelectItems();
        if (selectItems != null ) {
        	if (selectItems.size() == 1 && selectItems.get(0) instanceof AllColumns) {
        		convertAllColumns(plainSelect);
        	} else {
        		List<AllTableColumns> atcs = new ArrayList<AllTableColumns>();  
        		for (SelectItem selectItem : selectItems) {
                    if (selectItem instanceof AllTableColumns) {
                    	atcs.add((AllTableColumns) selectItem);
                    }            
        		}
        		if (atcs.size() > 0 ) {
        			convertAllTableColumns(plainSelect, atcs);
        		}
        	}
        }	
    }
    
	// convert SELECT * FROM
    private void convertAllColumns(PlainSelect ps) {
    	Table table = (Table) ps.getFromItem();
    	List<SelectItem> newSelectItems = this.config.getAllSelectItemsForTable(table);
    	ps.setSelectItems(newSelectItems);
    	return;
    }
    
	// convert SELECT A.A.COL1, A.B.*, A.D.*, A.A.COL2 FROM A.A, A.B, A.D ....
    private void convertAllTableColumns(PlainSelect ps, List<AllTableColumns> atcs) {
    	List<SelectItem> oldSelectItems = ps.getSelectItems();
    	List<SelectItem> newSelectItems = new ArrayList<SelectItem>();
    	Table table;
    	Map<AllTableColumns, List<SelectItem>> altSelectItemsMap = new HashMap<AllTableColumns, List<SelectItem>>();
    	List<SelectItem> altSelectItems;
    	for (AllTableColumns atc : atcs) {
    		table = atc.getTable();
    		altSelectItems = this.config.getAllSelectItemsForTable(table);
    		altSelectItemsMap.put(atc, altSelectItems);
    	}
    	for (SelectItem selectItem : oldSelectItems) {
    		if (selectItem instanceof AllTableColumns) {
    			altSelectItems = altSelectItemsMap.get((AllTableColumns) selectItem);
    			for (SelectItem altSi : altSelectItems) {
    				newSelectItems.add(altSi);
    			}
    		} else {
    			newSelectItems.add(selectItem);
    		}
    	}
    	ps.setSelectItems(newSelectItems);
    	return;
    }

    @Override
	public void visit(Union union) {
		// TODO Auto-generated method stub
	}
}
