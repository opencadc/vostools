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

import ca.nrc.cadc.wcs.Transform.Result;
import ca.nrc.cadc.wcs.exceptions.WCSLibInitializationException;
import ca.nrc.cadc.wcs.exceptions.WCSLibRuntimeException;

/**
 * This class contains the native method definitions to the WCSLIB 4.2 C routines
 * wcss2p() and wcsp2s(), and provides package level access to methods implementing 
 * the native definitions.
 * 
 * @author jburke
 *
 */
final class WCSLib
{
    private static Boolean loadOnce;

    /**
     * Loads the C wrapper library.
     * Solaris and Linux systems convert the loadLibrary parameter to
     * libwcsLibJNI.so, and look in the java.library.path for this file.
     * A Windws system will convert and look for a file called libwcsLibJNI.dll.
     */
    static
    {
        try
        {
            if (WCSLib.loadOnce == null)
                System.loadLibrary("wcsLibJNI");
            WCSLib.loadOnce = Boolean.TRUE;
        }
        catch(Error e)
        {
            throw new WCSLibInitializationException("failed to load wcsLibJNI", e);
        }
    }
    
    /**
     * Constant used to represent a null or unitialized value.
     * Taken the wcslib wcsmath.h.
     * #define UNDEFINED 987654321.0e99
     */
    protected static double UNDEFINED = 987654321.0e99;
    
    /**
     * Status return values from the native code.
     * 
     * The first ten status errors correspond to WCSLIB C status return values.
     * The remaining status errors are returned by the wrapper C code.
     */
    private static String[] STATUS_ERRORS = new String[]
    { 
        "Success",
        "Null wcsprm pointer passed.",
        "Memory allocation failed.",
        "Linear transformation matrix is singular.",
        "Inconsistent or unrecognized coordinate axis types.",
        "Invalid parameter value.",
        "Invalid coordinate transformation parameters.",
        "Ill-conditioned coordinate transformation parameters.",
        "One or more of the pixel coordinates were invalid, as indicated by the stat vector.",
        "One or more of the world coordinates were invalid, as indicated by the stat vector.",
        "No solution found in the specified interval.",
        "Invalid subimage specification (no spectral axis).",
        "CRPIX memory allocation failed.",
        "PC memory allocation failed.",
        "CDELT memory allocation failed.",
        "CRVAL memory allocation failed.",
        "CUNIT memory allocation failed.",
        "CUNIT array index out of bounds.",
        "CTYPE memory allocation failed.",
        "CTYPE array index out of bounds.",
        "PS value memory allocation failed.",
        "PS value array index out of bounds.",
        "CD memory allocation failed.",
        "CROTA memory allocation failed.",
        "Pixel array index out of bounds.",
        "World array index out of bounds.",
        "Result array index out of bounds."
    };

    private WCSLib() { }

    /***
     * Transforms pixel coordinates to world coordinates using the
     * native WCSLIB wcsp2s() C method.
     * 
     * @param naxis NAXIS - Number of axes in WCS description.
     * @param crpix CRPIXi - Pixel coordinate of the reference point.
     * @param pc PCi_j - Pixel coordinate transformation matrix.
     * @param cdelt CDELTi - Coordinate increment.
     * @param crval CRVALi - Coordinate value at reference point.
     * @param cunit CUNITi - Units of CRVAL and CDELT.
     * @param ctype CTYPEi - Axis type.
     * @param lonpole LONPOLE - Native longitude of the celestial pole.
     * @param latpole LATPOLE - Native latitude of the celestial pole.
     * @param restfrq RESTFRQ - Line rest frequency (Hz).
     * @param restwav RESTWAV - Line rest wavelength in vacuum (m).
     * @param pvi PVi_ma - Axis number i (1-relative).
     * @param pvm PVi_ma - Parameter number m (0-relative).
     * @param pvv PVi_ma - Parameter value.
     * @param psi PSi_ma - Axis number i (1-relative).
     * @param psm PSi_ma - Parameter number m (0-relative).
     * @param psv PSi_ma - Parameter value.
     * @param cd CDi_j - Spectrum coordinate matrix.
     * @param crota CROTAi - Coordinate rotation.
     * @param pixcrd Pixel coordinates.
     * @return World coordinates.
     * @throws WCSLibInitializationException if initialization of the WCSLIB C wcsprm struct fails.
     * @throws WCSLibRuntimeException if WCSLIB function execution fails.
     */
    protected static Result pix2sky
    (
        int      naxis,
        double[] crpix,
        double[] pc,
        double[] cdelt,
        double[] crval,
        String[] cunit,
        String[] ctype,
        double[] lonpole,
        double[] latpole,
        double[] restfrq,
        double[] restwav,
        int[]	 pvi,
        int[]	 pvm,
        double[] pvv,
        int[]	 psi,
        int[]	 psm,
        String[] psv,
        double[] cd,
        double[] crota,
        double[] pixcrd
    )
    {
        double[] world = new double[pixcrd.length];
        String[] worldUnits = new String[pixcrd.length];
        int status = wcsp2s(naxis, crpix, pc, cdelt, crval, cunit, ctype, lonpole, latpole, 
                            restfrq, restwav, pvi, pvm, pvv, psi, psm, psv, cd, crota, 
                            pixcrd, world, worldUnits);

        if (status == 0)
            return new Result(world, worldUnits);
        else if (status > 0 && status < 12)
            throw new WCSLibInitializationException(STATUS_ERRORS[status]);
        else
            throw new WCSLibRuntimeException(STATUS_ERRORS[status - 12]);
    }

    /***
     * Transforms world coordinates to pixel coordinates using the
     * native WCSLIB wcss2p() C method.
     * 
     * @param naxis NAXIS - Number of axes in WCS description.
     * @param crpix CRPIXi - Pixel coordinate of the reference point.
     * @param pc PCi_j - Pixel coordinate transformation matrix.
     * @param cdelt CDELTi - Coordinate increment.
     * @param crval CRVALi - Coordinate value at reference point.
     * @param cunit CUNITi - Units of CRVAL and CDELT.
     * @param ctype CTYPEi - Axis type.
     * @param lonpole LONPOLE - Native longitude of the celestial pole.
     * @param latpole LATPOLE - Native latitude of the celestial pole.
     * @param restfrq RESTFRQ - Line rest frequency (Hz).
     * @param restwav RESTWAV - Line rest wavelength in vacuum (m).
     * @param pvi PVi_ma - Axis number i (1-relative).
     * @param pvm PVi_ma - Parameter number m (0-relative).
     * @param pvv PVi_ma - Parameter value.
     * @param psi PSi_ma - Axis number i (1-relative).
     * @param psm PSi_ma - Parameter number m (0-relative).
     * @param psv PSi_ma - Parameter value.
     * @param cd CDi_j - Spectrum coordinate matrix.
     * @param crota CROTAi - Coordinate rotation.
     * @param world World coordinates.
     * @return Pixel coordinates.
     * @throws WCSLibInitializationException if initialization of the WCSLIB C wcsprm struct fails.
     * @throws WCSLibRuntimeException if WCSLIB method execution fails.
     */
    protected static Result sky2pix
    (
        int      naxis,
        double[] crpix,
        double[] pc,
        double[] cdelt,
        double[] crval,
        String[] cunit,
        String[] ctype,
        double[] lonpole,
        double[] latpole,
        double[] restfrq,
        double[] restwav,
        int[]	 pvi,
        int[]	 pvm,
        double[] pvv,
        int[]	 psi,
        int[]	 psm,
        String[] psv,
        double[] cd,
        double[] crota,
        double[] world
    )
    {
        double[] pixcrd = new double[world.length];
        String[] pixcrdUnits = new String[pixcrd.length];
        int status = wcss2p(naxis, crpix, pc, cdelt, crval, cunit, ctype, lonpole, latpole, 
                            restfrq, restwav, pvi, pvm, pvv, psi, psm, psv, cd, crota, 
                            world, pixcrd, pixcrdUnits);

        if (status == 0)
            return new Result(pixcrd, pixcrdUnits);
        else if (status > 0 && status < 12)
            throw new WCSLibInitializationException(STATUS_ERRORS[status]);
        else
            throw new WCSLibRuntimeException(STATUS_ERRORS[status - 12]);
    }

    /***
     * Transforms world coordinates to pixel coordinates using the
     * native WCSLIB wcss2p() C method.
     * 
     * @param naxis NAXIS - Number of axes in WCS description.
     * @param crpix CRPIXi - Pixel coordinate of the reference point.
     * @param pc PCi_j - Pixel coordinate transformation matrix.
     * @param cdelt CDELTi - Coordinate increment.
     * @param crval CRVALi - Coordinate value at reference point.
     * @param cunit CUNITi - Units of CRVAL and CDELT.
     * @param ctype CTYPEi - Axis type.
     * @param lonpole LONPOLE - Native longitude of the celestial pole.
     * @param latpole LATPOLE - Native latitude of the celestial pole.
     * @param restfrq RESTFRQ - Line rest frequency (Hz).
     * @param restwav RESTWAV - Line rest wavelength in vacuum (m).
     * @param pvi PVi_ma - Axis number i (1-relative).
     * @param pvm PVi_ma - Parameter number m (0-relative).
     * @param pvv PVi_ma - Parameter value.
     * @param psi PSi_ma - Axis number i (1-relative).
     * @param psm PSi_ma - Parameter number m (0-relative).
     * @param psv PSi_ma - Parameter value.
     * @param cd CDi_j - Spectrum coordinate matrix.
     * @param crota CROTAi - Coordinate rotation.
     * @param world World coordinates.
     * @return Pixel coordinates.
     * @throws WCSLibInitializationException if initialization of the WCSLIB C wcsprm struct fails.
     * @throws WCSLibRuntimeException if WCSLIB method execution fails.
     */
    protected static int translate
    (
        int      naxis,
        double[] crpix,
        double[] pc,
        double[] cdelt,
        double[] crval,
        String[] cunit,
        String[] ctype,
        double[] lonpole,
        double[] latpole,
        double[] restfrq,
        double[] restwav,
        int[]    pvi,
        int[]    pvm,
        double[] pvv,
        int[]    psi,
        int[]    psm,
        String[] psv,
        double[] cd,
        double[] crota,
        int      spectral_axis,
        String   spectral_ctype
    )
    {

        int status = wcssptr(naxis, crpix, pc, cdelt, crval, cunit, ctype, lonpole, latpole, 
                            restfrq, restwav, pvi, pvm, pvv, psi, psm, psv, cd, crota, 
                            spectral_axis, spectral_ctype);

        if (status == 0)
            return status;
        else if (status > 0 && status < 12)
            throw new WCSLibInitializationException(STATUS_ERRORS[status]);
        else
            throw new WCSLibRuntimeException(STATUS_ERRORS[status - 12]);
    }

    /**
     * Native method to transforms pixel coordinates to world coordinates
     * using the WCSLIB 4.2 wcsp2s() C method.
     * 
     * @return native method status value, 0 indicates success, other values
     *         indicate a problem during method exection. The STATUS_ERRORS
     *         array maps the status value to an error message.  
     */
    private static native int wcsp2s
    (
        int naxis,
        double[] crpix,
        double[] pc,
        double[] cdelt,
        double[] crval,
        String[] cunit,
        String[] ctype,
        double[] lonpole,
        double[] latpole,
        double[] restfrq,
        double[] restwav,
        int[]    pvi,
        int[]    pvm,
        double[] pvv,
        int[]    psi,
        int[]    psm,
        String[] psv,
        double[] cd,
        double[] crota,
        double[] pixcrd,
        double[] world,
        String[] worldUnits
    );

    /**
     * Native method to transforms world coordinates to pixel coordinates
     * using the WCSLIB 4.2 wcss2p() C method.
     * 
     * @return native method status value, 0 indicates success, other values
     *         indicate a problem during method exection. The STATUS_ERRORS
     *         array maps the status value to an error message.  
     */
    private static native int wcss2p
    (
        int naxis,
        double[] crpix,
        double[] pc,
        double[] cdelt,
        double[] crval,
        String[] cunit,
        String[] ctype,
        double[] lonpole,
        double[] latpole,
        double[] restfrq,
        double[] restwav,
        int[]    pvi,
        int[]    pvm,
        double[] pvv,
        int[]    psi,
        int[]    psm,
        String[] psv,
        double[] cd,
        double[] crota,
        double[] world,
        double[] pixcrd,
        String[] pixcrdUnits
    );

    /**
     * Native method to translate the spectral axis in a wcsprm struct
     * using the WCSLIB 4.2 wcssptr() C method.
     * 
     * @return native method status value, 0 indicates success, other values
     *         indicate a problem during method exection. The STATUS_ERRORS
     *         array maps the status value to an error message.  
     */
    private static native int wcssptr
    (
        int naxis,
        double[] crpix,
        double[] pc,
        double[] cdelt,
        double[] crval,
        String[] cunit,
        String[] ctype,
        double[] lonpole,
        double[] latpole,
        double[] restfrq,
        double[] restwav,
        int[]    pvi,
        int[]    pvm,
        double[] pvv,
        int[]    psi,
        int[]    psm,
        String[] psv,
        double[] cd,
        double[] crota,
        int      spectral_axis,
        String   spectral_type
    );

}
