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
import ca.nrc.cadc.wcs.exceptions.NoSuchKeywordException;
import ca.nrc.cadc.wcs.exceptions.WCSLibInitializationException;
import ca.nrc.cadc.wcs.exceptions.WCSLibRuntimeException;

/**
 * Port to Java of the twcs.c C method. twcs.c is used to verify the compilation
 * and installation of the WCSLIB 4.2 C library.
 * 
 *  This Java port is used to verify the compilation and installation of the
 *  Java JNI implementation of the WCSLIB methods wcss2p() and wcsp2s().
 * 
 * @author jburke
 *
 */
public class TestWCSLib
{
    public static void main(String[] args)
    {
        boolean wcstest = false;
        boolean translate = false;
        boolean vertices = false;
        boolean pix2sky = false;
        
        if (args.length == 0)
        {
            wcstest = true;
            translate = true;
            vertices = true;
            pix2sky = true;
        }

        for (int i = 0; i < args.length; i++)
        {
            String arg = args[i];
            if (arg.equals("wcstest"))
                wcstest = true;
            if (arg.equals("translate"))
                translate = true;
            if (arg.equals("vertices"))
                vertices = true;
            if (arg.equals("pix2sky"))
                pix2sky = true;
        }
        
        if (wcstest)
        {
            try
            {
                System.out.println("Starting wcs test...\n");
                
                WCSKeywords keywords = getTestKeywords();
                Transform transform = new Transform(keywords);
                doWCSTest(transform, 4);
                
                System.out.println("\nFinished wcs test...\n");
            }
            catch (NoSuchKeywordException e)
            {
                System.out.println("Keyword error: " + e.getMessage());
            }
        }
        
        if (translate)
        {
            try
            {
                System.out.println("Starting translate test...\n");
                
                WCSKeywords keywords = getTranslateKeywords();
                
                keywords.put("LONPOLE", 180.0D);
                keywords.put("LATPOLE", 180.0D);
                keywords.put("RESTWAV", 180.0D);
                    
                Transform transform = new Transform(keywords);
                System.out.println("keywords to be translated:\n " + transform.toString());
                keywords = doTranslate(transform, 1);
                Transform returned = new Transform(keywords);
                System.out.println("translated keywords:\n " + returned.toString());
                System.out.println("Finished translate test...\n");
            }
            catch (NoSuchKeywordException e)
            {
                System.out.println("Keyword error: " + e.getMessage());
            }
        }
        
        if (vertices)
        {
            try
            {
                System.out.println("Starting vertices test...\n");
                
                WCSKeywords keywords = getVerticesKeywords();
                Transform transform = new Transform(keywords);
                doVertices(transform, 4);
                
                System.out.println("\nFinished vertices test...\n");
            }
            catch (NoSuchKeywordException e)
            {
                System.out.println("Keyword error: " + e.getMessage());
            }
        }

        if (pix2sky)
        {
            try
            {
                System.out.println("Starting pix2sky test...\n");

                WCSKeywords keywords = getPix2SkyKeywords();
                Transform transform = new Transform(keywords);
                doPix2Sky(transform, 1);

                System.out.println("Finished pix2sky test...\n");
            }
            catch (NoSuchKeywordException e)
            {
                System.out.println("Keyword error: " + e.getMessage());
            }
        }

    }
    
    public static void doWCSTest(Transform transform, int NAXIS)
    {
        Result result;
        int NCOORD = 361;
        int NELEM = 9;
        double TOL = 1.0e-10;

        double[][] pixel1 = new double[NCOORD][NELEM];
        double[][] pixel2 = new double[NCOORD][NELEM];
        double[][] world1 = new double[NCOORD][NELEM];
        double[][] world2 = new double[NCOORD][NELEM];
        double r;
        double resid;
        double time = 1.0;
        double freq = 1.42040595e9 - 180.0 * 62500.0;
        for (int k = 0; k < NCOORD; k++)
        {
           world1[k][0] = 0.0;
           world1[k][1] = 0.0;
           world1[k][2] = 0.0;
           world1[k][3] = 0.0;

           world1[k][2] = time;
           time *= 1.01;

           world1[k][0] = 2.99792458e8 / freq;
           freq += 62500.0;
        }

        int k;
        int lng;
        double lat1;
        double lng1;
        double residmax = 0.0;
        for (int lat = 90; lat >= -90; lat--)
        {
           lat1 = (double) lat;

           for (lng = -180, k = 0; lng <= 180; lng++, k++)
           {
              lng1 = (double) lng;

              world1[k][3] = lng1;
              world1[k][1] = lat1;
           }
             
           System.out.println("Testing with lat1 = " + lat1);
           
           try
           {
               result = transform.sky2pix(world1[0]);

           }
           catch (WCSLibInitializationException ie)
           {
              System.out.println("   sky2pix(1) init ERROR (lat1 = " + lat1 + ") " + ie.getMessage());
              break;
           }
           catch (WCSLibRuntimeException re)
           {
              System.out.println("   sky2pix(1) runtime ERROR (lat1 = " + lat1 + ") " + re.getMessage());
              break;
           }
           
           
           try
           {
               result = transform.pix2sky(result.coordinates);
           }
           catch (WCSLibInitializationException ie)
           {
              System.out.println("   pix2sky init ERROR (lat1 = " + lat1 + ") " + ie.getMessage());
              break;
           }
           catch (WCSLibRuntimeException re)
           {
              System.out.println("   pix2sky runtime ERROR (lat1 = " + lat1 + ") " + re.getMessage());
              break;
           }

           try
           {
               result = transform.sky2pix(result.coordinates);
           }
           catch (WCSLibInitializationException ie)
           {
              System.out.println("   sky2pix(2) init ERROR (lat1 = " + lat1 + ") " + ie.getMessage());
              break;
           }
           catch (WCSLibRuntimeException re)
           {
              System.out.println("   sky2pix(2) runtime ERROR (lat1 = " + lat1 + ") " + re.getMessage());
              break;
           }
            
           for (k = 0; k < NCOORD; k++)
           {
              resid = 0.0;
              for (int i = 0; i < NAXIS; i++)
              {
                r = pixel2[k][i] - pixel1[k][i];
                resid += r*r;
              }

              resid = Math.sqrt(resid);
              if (resid > residmax) residmax = resid;

              if (resid > TOL)
              {
                  StringBuffer sb = new StringBuffer();
                  sb.append("\nClosure error:\n");
                  sb.append("world1: ");
                  sb.append(world1[k][0]).append(" ");
                  sb.append(world1[k][1]).append(" ");
                  sb.append(world1[k][2]).append(" ");
                  sb.append(world1[k][3]).append(" ");
                  sb.append("\n");
                  sb.append("pixel1: ");
                  sb.append(pixel1[k][0]).append(" ");
                  sb.append(pixel1[k][1]).append(" ");
                  sb.append(pixel1[k][2]).append(" ");
                  sb.append(pixel1[k][3]).append(" ");
                  sb.append("\n");
                  sb.append("world2: ");
                  sb.append(world2[k][0]).append(" ");
                  sb.append(world2[k][1]).append(" ");
                  sb.append(world2[k][2]).append(" ");
                  sb.append(world2[k][3]).append(" ");
                  sb.append("\n");
                  sb.append("pixel2: ");
                  sb.append(pixel2[k][0]).append(" ");
                  sb.append(pixel2[k][1]).append(" ");
                  sb.append(pixel2[k][2]).append(" ");
                  sb.append(pixel2[k][3]).append(" ");
                  sb.append("\n");
                  System.out.println(sb.toString());
              }
           }
        }
        System.out.println("Test completed.");
    }

    private static void doVertices(Transform transform, int NAXIS)
    {
        double[] pixel = new double[2];
        
        try
        {
            pixel[0] = 0.5;
            pixel[1] = 0.5;
            Result result = transform.pix2sky(pixel);
            System.out.print(pixel [0] +", " +  pixel[1] + " -> ");
            System.out.print( + result.coordinates[0] + ", " + result.coordinates[1]);
            System.out.println(" units(" + result.units[0] + ", " + result.units[1] + ")");

            pixel[0] = 0.5;
            pixel[1] = 2248.5;
            result = transform.pix2sky(pixel);
            System.out.print(pixel [0] +", " +  pixel[1] + " -> ");
            System.out.print( + result.coordinates[0] + ", " + result.coordinates[1]);
            System.out.println(" units(" + result.units[0] + ", " + result.units[1] + ")");

            pixel[0] = 8.5;
            pixel[1] = 2248.5;
            result = transform.pix2sky(pixel);
            System.out.print(pixel [0] +", " +  pixel[1] + " -> ");
            System.out.print( + result.coordinates[0] + ", " + result.coordinates[1]);
            System.out.println(" units(" + result.units[0] + ", " + result.units[1] + ")");

            pixel[0] = 8.5;
            pixel[1] = 0.5;
            result = transform.pix2sky(pixel);
            System.out.print(pixel [0] +", " +  pixel[1] + " -> ");
            System.out.print( + result.coordinates[0] + ", " + result.coordinates[1]);
            System.out.println(" units(" + result.units[0] + ", " + result.units[1] + ")");

        }
        catch (WCSLibInitializationException ie)
        {
           System.out.println("   pix2sky init ERROR " + ie.getMessage());
        }
        catch (WCSLibRuntimeException re)
        {
           System.out.println("   pix2sky runtime ERROR " + re.getMessage());
        }
    }
    
    private static WCSKeywords doTranslate(Transform transform, int NAXIS)
    {
        WCSKeywords keywords = null;
        try
        {
            //transform.translate("FREQ-???");
            keywords = transform.translate("WAVE-???");
        }
        catch (WCSLibInitializationException ie)
        {
           System.out.println("   translate init ERROR " + ie.getMessage());
        }
        catch (WCSLibRuntimeException re)
        {
           System.out.println("   translate runtime ERROR " + re.getMessage());
        }
        return keywords;
    }

    private static void doPix2Sky(Transform transform, int NAXIS)
    {
        try
        {
            System.out.println("keywords:\n" + transform.toString());
            Result result = transform.pix2sky( new double[] { 0.5, 1.5 });
            System.out.print("1st transform: " + result.coordinates[0] + "(" + result.units[0] + "), ");
            System.out.println(result.coordinates[1] + "(" + result.units[1] + ")");

            result = transform.pix2sky( new double[] { 185600 + 0.5, 185600 + 1.5 });
            System.out.print("2nd transform: " + result.coordinates[0] + "(" + result.units[0] + "), ");
            System.out.println(result.coordinates[1] + "(" + result.units[1] + ")\n");
        }
        catch (WCSLibInitializationException ie)
        {
           System.out.println("   translate init ERROR " + ie.getMessage());
        }
        catch (WCSLibRuntimeException re)
        {
           System.out.println("   translate runtime ERROR " + re.getMessage());
        }
    }

    private static WCSKeywords getTranslateKeywords()
    {
        WCSKeywords keywords = new WCSKeywordsImpl();

        keywords.put("CDELT1", 97656.25D);
        keywords.put("CRPIX1", 32.0D);
        keywords.put("CRVAL1", 1.378351174e9D);
        keywords.put("CTYPE1", "FREQ");
        keywords.put("CUNIT1", "Hz");
        
        keywords.put("RESTFRQ", 1.420405752e9D);
        keywords.put("MJD-AVG", 51085.98247222222D);
        keywords.put("MJD-OBS", 51085.979D);
        keywords.put("NAXIS", 1);
        keywords.put("NAXIS1", 63);
        keywords.put("SPECSYS", "TOPOCENT");
        keywords.put("VELOSYS", 26108.0D);

         return keywords;
    }
    
    private static WCSKeywords getTestKeywords()
    {
        WCSKeywords keywords = new WCSKeywordsImpl();
        
        keywords.put("NAXIS", 4);
        
        keywords.put("CRPIX1", 513.0D);
        keywords.put("CRPIX2", 0.0D);
        keywords.put("CRPIX3", 0.0D);
        keywords.put("CRPIX4", 0.0D);
        
        keywords.put("PC1_1", 1.1D);
        keywords.put("PC1_2", 0.0D);
        keywords.put("PC1_3", 0.0D);
        keywords.put("PC1_4", 0.0D);
        keywords.put("PC2_1", 0.0D);
        keywords.put("PC2_2", 1.0D);
        keywords.put("PC2_3", 0.0D);
        keywords.put("PC2_4", 0.1D);
        keywords.put("PC3_1", 0.0D);
        keywords.put("PC3_2", 0.0D);
        keywords.put("PC3_3", 1.0D);
        keywords.put("PC3_4", 0.0D);
        keywords.put("PC4_1", 0.0D);
        keywords.put("PC4_2", 0.2D);
        keywords.put("PC4_3", 0.0D);
        keywords.put("PC4_4", 1.0D);
        
        keywords.put("CDELT1", -9.635265432e-6D);
        keywords.put("CDELT2", 1.0D);
        keywords.put("CDELT3", 0.1D);
        keywords.put("CDELT4", -1.0D);
        
        keywords.put("CRVAL1", 0.214982042D);
        keywords.put("CRVAL2", -30.0D);
        keywords.put("CRVAL3", 1.0D);
        keywords.put("CRVAL4", 150.0D);
        
        keywords.put("CTYPE1", "WAVE-F2W");
        keywords.put("CTYPE2", "XLAT-BON");
        keywords.put("CTYPE3", "TIME-LOG");
        keywords.put("CTYPE4", "XLON-BON");
        
        keywords.put("LONPOLE", 150.0D);
        keywords.put("LATPOLE", 999.0D);
        keywords.put("RESTFRQ", 1.42040575e9D);
        keywords.put("RESTWAV", 0.0D);
        
        keywords.put("PV4_1", 0.0D);
        keywords.put("PV4_2", 90.0D);
        keywords.put("PV2_1", -30.0D); 

        return keywords;
    }

    private static WCSKeywords getVerticesKeywords()
    {
        WCSKeywords keywords = new WCSKeywordsImpl();
        
        keywords.put("CD1_1", -1.74368624704027E-4);
        keywords.put("CD1_2", 2.45291066786335E-7);
        keywords.put("CD2_1", 9.3961005590014E-7);
        keywords.put("CD2_2", 1.74110153624481E-4);

        keywords.put("CRPIX1", 183.97113410273);
        keywords.put("CRPIX2", 2217.53366109879);

        keywords.put("CRVAL1", 76.3387499968215);
        keywords.put("CRVAL2", -69.08717903879);

        keywords.put("CTYPE1", "RA---TNX");
        keywords.put("CTYPE2", "DEC--TNX");

        keywords.put("CUNIT1", "deg");
        keywords.put("CUNIT2", "deg");

        keywords.put("EQUINOX", 2000.0);
        keywords.put("NAXIS", 2);
        keywords.put("NAXIS1", 8);
        keywords.put("NAXIS2", 2248);
        keywords.put("RADECSYS", "FK5");

        return keywords;
    }

    private static WCSKeywords getPix2SkyKeywords()
    {
        WCSKeywords keywords = new WCSKeywordsImpl();

        keywords.put("CDELT1", 0.00339439);
        keywords.put("CRPIX1", 0.5);
        keywords.put("CRVAL1", 370.0);
        keywords.put("CTYPE1", "WAVE");
        keywords.put("CUNIT1", "nm");
        keywords.put("NAXIS", 2);
        keywords.put("NAXIS1", 185600);

        keywords.put("CDELT2", 0.00339439);
        keywords.put("CRPIX2", 0.5);
        keywords.put("CRVAL2", 370.0);
        keywords.put("CTYPE2", "WAVE");
        keywords.put("CUNIT2", "m");
        keywords.put("NAXIS2", 185600);

         return keywords;
    }

}
