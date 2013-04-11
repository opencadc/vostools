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

package ca.nrc.cadc.wcs;

import ca.nrc.cadc.wcs.exceptions.NoSuchKeywordException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Java class to provide access to the WCSLIB 4.2 wcsp2s() and wcss2p() C methods 
 * using the Java Native Interface.
 * 
 * WCSLIB 4.2 is an implementation of the FITS WCS standard by Mark Calabretta of
 * the Australia Telescope National Facility, CSIRO. Further information on WCSLIB
 * can be found at:
 * 
 *      http://www.atnf.csiro.au/people/mcalabre/WCS/WCSLIB
 * 
 * The WCSLIB wcsp2s() method transforms pixel coordinates to world coordinates.
 * The WCSLIB wcss2p() method transforms world coordinates to pixel coordinates. 
 *  
 * The Transform methods corresponding to wcsp2s() and wcss2p() are pix2sky() and sky2pix(). 
 * 
 * The WCSLIB methods use the following WCS keywords:
 *      NAXIS   - Number of axes in WCS description.
 *      CRPIXi  - Pixel coordinate of the reference point.
 *      PCi_j   - Pixel coordinate transformation matrix.
 *      CDELTi  - Coordinate increment.
 *      CRVALi  - Coordinate value at reference point.
 *      CUNITi  - Units of CRVAL and CDELT.
 *      CTYPEi  - Axis type.
 *      LONPOLE - Native longitude of the celestial pole.
 *      LATPOLE - Native latitude of the celestial pole.
 *      RESTFRQ - Line rest frequency (Hz).
 *      RESTWAV - Line rest wavelength in vacuum (m).
 *      PVi_ma  - Axis number i (1-relative).
 *      PSi_ma  - Axis number i (1-relative).
 *      CDi_j   - Spectrum coordinate matrix.
 *      CROTAi  - Coordinate rotation.
 *  
 *  The Transform constructor takes the WCS keywords and populates common variables and 
 *  arrays used as arguments to the WCSLIB methods. The pix2sky() and sky2pix() methods 
 *  take double arrays as the pixel or world coordinates to be transformed, and return 
 *  double arrays with the transformed coordinates.
 *  
 *  
 * @author jburke
 *
 */
public class Transform
{
    private static final String LF = System.getProperty("line.separator");
    
    // Array index of spectral axis for spectral translation
    // value of -1 will let wcslib determine the spectral axis array index
    private static int spectralAxis = -1;
    
    // Retains the input keywords which are returned by the translate method.
    private WCSKeywords keywords;
    
    // NAXIS - Number of axes in WCS description.
    private int naxis;
    
    // CRPIXi - Pixel coordinate of the reference point.
    private double[] crpix;
    
    // PCi_j - Pixel coordinate transformation matrix.
    private double[] pc;
    
    // CDELTi - Coordinate increment.
    private double[] cdelt;
    
    // CRVALi - Coordinate value at reference point.
    private double[] crval;
    
    // CUNITi - Units of CRVAL and CDELT.
    private String[] cunit;

    // CTYPEi - Axis type.
    private String[] ctype;
    
    // LONPOLE - Native longitude of the celestial pole.
    private double[] lonpole;
    
    // LATPOLE - Native latitude of the celestial pole.
    private double[] latpole;
    
    // RESTFRQ - Line rest frequency (Hz).
    private double[] restfrq;
    
    // RESTWAV - Line rest wavelength in vacuum (m).
    private double[] restwav;
    
    // PVi_ma - Axis number i (1-relative).
    private int[] pvi;
    
    // PVi_ma - Parameter number m (0-relative).
    private int[] pvm;
    
    // PVi_ma - Parameter value.
    private double[] pvv;
    
    // PSi_ma - Axis number i (1-relative).
    private int[] psi;
    
    // PSi_ma - Parameter number m (0-relative).
    private int[] psm;
    
    // PSi_ma - Parameter value.
    private String[] psv;
    
    // CDi_j - Spectrum coordinate matrix.
    private double[] cd;
    
    // CROTAi - Coordinate rotation.
    private double[] crota;
    
    // Lists to hold PV & PC arrays
    private List pviList;
    private List pvmList;
    private List pvvList;
    private List psiList;
    private List psmList;
    private List psvList;

    // force WCSLib to be loaded to we can immediately fail when JNI and native
    // loadLibrary fails
    static
    {
        double d = WCSLib.UNDEFINED;
    }

    /**
     * Constructs a new Transform class initializing variable and array 
     * arguments to the native wrapper methods using the WCSKeywords.
     * 
     * @param keywords implementation of WCSKeywords.
     * @throws NoSuchKeywordException if NAXIS keyword is not found in WCSKeywords.
     */
    public Transform(WCSKeywords wcs)
        throws NoSuchKeywordException
    {
        keywords = wcs;

        // NAXIS keyword must be present, else throw NoSuchKeywordException.
        // Check/allow WCSAXES as well?
        if (!keywords.containsKey("NAXIS"))
            throw new NoSuchKeywordException("NAXIS");
        
        // NAXIS - Number of axes in WCS description
        naxis = keywords.getIntValue("NAXIS");
        
        // LONPOLE - Native longitude of the celestial pole
        lonpole = new double[] { WCSLib.UNDEFINED };
        if (keywords.containsKey("LONPOLE"))
            lonpole[0] = keywords.getDoubleValue("LONPOLE");
        
        // LATPOLE - Native latitude of the celestial pole
        latpole = new double[] { WCSLib.UNDEFINED };
        if (keywords.containsKey("LATPOLE"))
            latpole[0] = keywords.getDoubleValue("LATPOLE");
        
        // RESTFRQ - Line rest frequency (Hz)
        restfrq = new double[] { WCSLib.UNDEFINED };
        if (keywords.containsKey("RESTFRQ"))
            restfrq[0] = keywords.getDoubleValue("RESTFRQ");
        
        // RESTWAV - Line rest wavelength in vacuum (m)
        restwav = new double[] { WCSLib.UNDEFINED };
        if (keywords.containsKey("RESTWAV"))
            restwav[0] = keywords.getDoubleValue("RESTWAV");

        // PCi_j - Pixel coordinate transformation matrix
        if (keywords.containsKey("PC1_1"))
        {
            int index = 0;
            pc = new double[naxis * naxis];
            
            for (int i = 1; i <= naxis; i++)
            {
                for (int j = 1; j <= naxis; j++)
                {
                    pc[index++] = keywords.getDoubleValue("PC" + i + "_" + j);
                }
            }   
        }

        // CDi_j - Spectrum coordinate matrix
        if (keywords.containsKey("CD1_1"))
        {
            int index = 0;
            cd = new double[naxis * naxis];
            
            for (int i = 1; i <= naxis; i++)
            {
                for (int j = 1; j <= naxis; j++)
                {
                    cd[index++] = keywords.getDoubleValue("CD" + i + "_" + j);
                }
            }
        }
        
        for (int i = 0; i < naxis; i++)
        {
            int axis = i + 1;
            
            // CRPIXi - Pixel coordinate of the reference point
            if (keywords.containsKey("CRPIX" + axis))
            {
                if (crpix == null)
                    crpix = new double[naxis];
                crpix[i] = keywords.getDoubleValue("CRPIX" + axis);
            }

            // CDELTi - Coordinate increment
            if (keywords.containsKey("CDELT" + axis))
            {
                if (cdelt == null)
                    cdelt = new double[naxis];
                cdelt[i] = keywords.getDoubleValue("CDELT" + axis);
            }

            // CRVALi - Coordinate value at reference point
            if (keywords.containsKey("CRVAL" + axis))
            {
                if (crval == null)
                    crval = new double[naxis];
                crval[i] = keywords.getDoubleValue("CRVAL" + axis);
            }

            // CUNITi - Units of CRVAL and CDELT
            if (keywords.containsKey("CUNIT" + axis))
            {
                if (cunit == null)
                    cunit = new String[naxis];
                cunit[i] = keywords.getStringValue("CUNIT" + axis);
            }

            // CTYPEi - Axis type
            if (keywords.containsKey("CTYPE" + axis))
            {
                if (ctype == null)
                    ctype = new String[naxis];
                ctype[i] = keywords.getStringValue("CTYPE" + axis);
            }

            // CROTAi - Coordinate rotation
            if (keywords.containsKey("CROTA" + axis))
            {
                if (crota == null)
                    crota = new double[naxis];
                crota[i] = keywords.getDoubleValue("CROTA" + axis);
            }
                    
            for (int parameter = 0; parameter < naxis; parameter++)
            {
                // PVi_ma
                if (keywords.containsKey("PV" + axis + "_" + parameter))
                {
                    if (pviList == null)
                        pviList = new ArrayList();
                    pviList.add(Integer.valueOf(axis));
                    if (pvmList == null)
                        pvmList = new ArrayList();
                    pvmList.add(Integer.valueOf(parameter));
                    if (pvvList == null)
                        pvvList = new ArrayList();
                    pvvList.add(new Double(keywords.getDoubleValue("PV" + axis + "_" + parameter)));
                }
                
                // PSi_ma
                if (keywords.containsKey("PS" + axis + "_" + parameter))
                {
                    if (psi == null)
                        psiList = new ArrayList();
                    psiList.add(Integer.valueOf(axis));
                    if (psm == null)
                        psmList = new ArrayList();
                    psmList.add(Integer.valueOf(parameter));
                    if (psv == null)
                        psvList = new ArrayList();
                    psvList.add(keywords.getStringValue("PS" + axis + "_" + parameter));
                }
            }
            
            // Write PV & PS Lists to arrays.
            if (pviList != null && pvmList != null && pvvList != null)
            {
        	pvi = toIntArray(pviList);
        	pvm = toIntArray(pvmList);
        	pvv = toDoubleArray(pvvList);
            }
            if (psiList != null && psmList != null && psvList != null)
            {
        	psi = toIntArray(psiList);
        	psm = toIntArray(psmList);
        	psv = (String[]) psvList.toArray(new String[0]);
            }

        }
    }
    
    /**
     * Transforms pixel coordinates to world coordinates. Method takes a double array of
     * pixel coordinates, and returns a double array of the translated world coordinates.
     * 
     * @param pixcrd double array of pixel coordinates
     * @return double array of world coordinates.
     */
    public Result pix2sky(double[] pixcrd)
    {
    	// Change TNX to TAN in ctype.
    	String[] _ctype = (String[]) ctype.clone();
        changeTNXToTAN(_ctype);

        synchronized(WCSLib.class)
        {
            return WCSLib.pix2sky(naxis, crpix, pc, cdelt, crval, cunit, _ctype, lonpole, latpole,
                              restfrq, restwav, pvi, pvm, pvv, psi, psm, psv, cd, crota,
                              pixcrd);
        }
    }
    
    /**
     * Transforms world coordinates to pixel coordinates. Method takes a double array of
     * world coordinates, and returns a double array of the transformed pixel coordinates.
     * 
     * @param world double array of world coordinates.
     * @return double array of pixel coordinates.
     */
    public Result sky2pix(double[] world)
    {
    	// Change TNX to TAN in ctype.
    	String[] _ctype = (String[]) ctype.clone();
        changeTNXToTAN(_ctype);
    	
        synchronized(WCSLib.class)
        {
            return WCSLib.sky2pix(naxis, crpix, pc, cdelt, crval, cunit, _ctype, lonpole, latpole,
                              restfrq, restwav, pvi, pvm, pvv, psi, psm, psv, cd, crota,
                              world);
        }
    }
    
    /**
     * Translates the spectral axis in a WCSKeywords. Method takes a spectral ctype,
     * and returns a translated WCSKeywords.
     * For the spectral CTYPE wild carding may be used. If the final three
     * characters are specified as "???", or if just the eighth character 
     * is specified as '?', the correct algorithm code will be substituted and returned.
     * 
     * @param spectral_ctype spectral CTYPE.
     * @return the translated WCSKeywords.
     */
    public WCSKeywords translate(String spectral_ctype)
    {
        // Spectral ctype must be 8 characters or less
        if (spectral_ctype.length() > 8)
            throw new IllegalArgumentException("Spectral ctype must be 8 or less characters.");

        // Make copies of crpix, cdelt, crval, cunit, ctype, lonpole, latpole, restfrq, restwav
        // to pass as parameters.
        double[] _crpix = crpix == null ? null : (double[]) crpix.clone();
        double[] _cdelt = cdelt == null ? null : (double[]) cdelt.clone();
        double[] _crval = crval == null ? null : (double[]) crval.clone();
        String[] _cunit = cunit == null ? null : (String[]) cunit.clone();
        String[] _ctype = ctype == null ? null : (String[]) ctype.clone();
        double[] _lonpole = lonpole == null ? null : (double[]) lonpole.clone();
        double[] _latpole = latpole == null ? null : (double[]) latpole.clone();
        double[] _restfrq = restfrq == null ? null : (double[]) restfrq.clone();
        double[] _restwav = restwav == null ? null : (double[]) restwav.clone();
        
        // Change TNX to TAN in ctype.
        changeTNXToTAN(_ctype);

        synchronized(WCSLib.class)
        {
            WCSLib.translate(naxis, _crpix, pc, _cdelt, _crval, _cunit, _ctype, _lonpole, _latpole,
                         _restfrq, _restwav, pvi, pvm, pvv, psi, psm, psv, cd, crota,
                         spectralAxis, spectral_ctype);
        }

        WCSKeywords wcs = keywordsCopy();
        if (_lonpole[0] != WCSLib.UNDEFINED)
            wcs.put("LONPOLE", _lonpole[0]);
        if (_latpole[0] != WCSLib.UNDEFINED)
            wcs.put("LATPOLE", _latpole[0]);
        if (_restfrq[0] != WCSLib.UNDEFINED)
            wcs.put("RESTFRQ", _restfrq[0]);
        if (_restwav[0] != WCSLib.UNDEFINED)
            wcs.put("RESTWAV", _restwav[0]);

        int index = 0;
        for (int i = 1; i <= naxis; i++)
        {
            for (int j = 1; j <= naxis; j++)
            {
                if (pc != null)
                    wcs.put("PC" + i + "_" + j, pc[index++]);
                if (cd != null)
                    wcs.put("CD" + i + "_" + j, cd[index++]);
            }
        }

        for (int i = 0; i < naxis; i++)
        {
            int axis = i + 1;
            
            if (_crpix != null)
                wcs.put("CRPIX" + axis, _crpix[i]);

            if (_cdelt != null)
                wcs.put("CDELT" + axis, _cdelt[i]);

            if (_crval != null)
                wcs.put("CRVAL" + axis, _crval[i]);

            if (_cunit != null)
                wcs.put("CUNIT" + axis, _cunit[i]);

            if (_ctype != null)
                wcs.put("CTYPE" + axis, _ctype[i]);

            if (crota != null)
                wcs.put("CROTA" + axis, crota[i]);
            
            for (int parameter = 0; parameter < naxis; parameter++)
            {
                if (pvi != null && pvm != null && pvv != null)    
                    wcs.put("PV" + axis + "_" + parameter, pvv[i]);
                
                if (psi != null && psm != null && psv != null)
                    wcs.put("PV" + axis + "_" + parameter, psv[i]);
            }
        }
        return wcs;
    }

    /**
     * Returns a list of the WCSKeywords in the form keyword = value.
     */
    public String toString()
    {
        StringBuffer sb = new StringBuffer();
        sb.append("NAXIS = ").append(naxis).append(LF);
        if (lonpole != null)
            sb.append("LONPOLE = ").append(lonpole[0]).append(LF);
        if (latpole != null)
            sb.append("LATPOLE = ").append(latpole[0]).append(LF);
        if (restfrq != null)
            sb.append("RESTFRQ = ").append(restfrq[0]).append(LF);
        if (restwav != null)
            sb.append("RESTWAV = ").append(restwav[0]).append(LF);
        
        for (int i = 0; i < naxis; i++)
        {
            if (crpix != null)
                sb.append("CRPIX").append(i + 1).append(" = ").append(crpix[i]).append(LF);
            if (cdelt != null)
                sb.append("CDELT").append(i + 1).append(" = ").append(cdelt[i]).append(LF);
            if (crval != null)
                sb.append("CRVAL").append(i + 1).append(" = ").append(crval[i]).append(LF);
            if (cunit != null)
                sb.append("CUNIT").append(i + 1).append(" = ").append(cunit[i]).append(LF);
            if (ctype != null)
                sb.append("CTYPE").append(i + 1).append(" = ").append(ctype[i]).append(LF);
            if (crota != null) 
                sb.append("CROTA").append(i + 1).append(" = ").append(crota[i]).append(LF);         
        }
        
        if (pvi != null && pvm != null & pvv != null)
        {
            for (int i = 0; i < pvi.length; i++)
                sb.append("PV").append(pvi[i]).append("_").append(pvm[i]).append(" = ").append(pvv[i]).append(LF);
        }
        
        if (psi != null && psm != null & psv != null)
        {
            for (int i = 0; i < psi.length; i++) 
                sb.append("PS").append(psi[i]).append("_").append(psm[i]).append(" = ").append(psv[i]).append(LF);
        }
        
        int index = 0;
        for (int j = 1; j <= naxis; j++)
        {
            for (int k = 1; k <= naxis; k++)
            {
                if (pc != null)
                    sb.append("PC").append(j).append("_").append(k).append(" = ").append(pc[index++]).append(LF);
                if (cd != null)
                    sb.append("CD").append(j).append("_").append(k).append(" = ").append(cd[index++]).append(LF);
            }
        }   

        return sb.toString();
    }
    
    /**
     * 
     * @return copy of the WCSKeywords.
     */
    private WCSKeywords keywordsCopy()
    {
        WCSKeywords wcs =  new WCSKeywordsImpl();
        Iterator<Map.Entry<String,Object>> iter = keywords.iterator();
        while ( iter.hasNext() )
        {
            Map.Entry<String,Object> me = iter.next();
            Object value = me.getValue();
            if (value instanceof String)
                wcs.put(me.getKey(), (String) me.getValue());
            else if (value instanceof Integer)
                wcs.put(me.getKey(), (Integer) me.getValue());
            else if (value instanceof Long)
                wcs.put(me.getKey(), (Long) me.getValue());
            else if (value instanceof Float)
                wcs.put(me.getKey(), (Float) me.getValue());
            else
                wcs.put(me.getKey(), (Double) me.getValue());
        }
        return wcs;
    }

    // Write an Integer List to an int array.
    private static int[] toIntArray(List integerList)
    {
	int[] array = new int[integerList.size()];
	for (int i = 0; i < integerList.size(); i++)
	    array[i] = ((Integer) integerList.get(i)).intValue();
	return array;
    }
    
    // Write an Double List to an double array.
    private static double[] toDoubleArray(List doubleList)
    {
	double[] array = new double[doubleList.size()];
	for (int i = 0; i < doubleList.size(); i++)
	    array[i] = ((Double) doubleList.get(i)).doubleValue();
	return array;
    }
    
    // Convert a spatial ctype from TNX to TAN.
    // RA---TNX -> RA---TAN 
    // DEC--TNX -> DEC--TAN
    private void changeTNXToTAN(String[] ctype)
    {
    	if (ctype == null)
    		return;
    	for (int i = 0; i < ctype.length; i++)
    		ctype[i] = ctype[i].replaceAll("--TNX", "--TAN");
    }

    /**
     * 
     *
     */
    public static class Result
    {
        public double[] coordinates;
        public String[] units;

        public Result(double[] coordinates, String[] units)
        {
            this.coordinates = coordinates;
            this.units = units;
        }

    }

}
