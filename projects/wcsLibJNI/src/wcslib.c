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

#include <jni.h>
#include <stdlib.h>
#include <stdio.h>
#include <string.h>
#include "wcslib/wcs.h"
#include "wcslib/wcslib.h"
#include "wcslib/wcsmath.h"

#define NCOORD 1

/* Status return values */
const int CRPIX_MEMORY_ALLOCATION_FAILED = 12;
const int PC_MEMORY_ALLOCATION_FAILED = 13;
const int CDELT_MEMORY_ALLOCATION_FAILED = 14;
const int CRVAL_MEMORY_ALLOCATION_FAILED = 15;
const int CUNIT_MEMORY_ALLOCATION_FAILED = 16;
const int CUNIT_ARRAY_INDEX_OUT_OF_BOUNDS = 17;
const int CTYPE_MEMORY_ALLOCATION_FAILED = 18;
const int CTYPE_ARRAY_INDEX_OUT_OF_BOUNDS = 19;
const int LONPOLE_MEMORY_ALLOCATION_FAILED = 20;
const int LATPOLE_MEMORY_ALLOCATION_FAILED = 21;
const int RESTFRQ_MEMORY_ALLOCATION_FAILED = 22;
const int RESTWAV_MEMORY_ALLOCATION_FAILED = 23;
const int PS_MEMORY_ALLOCATION_FAILED = 24;
const int PS_ARRAY_INDEX_OUT_OF_BOUNDS = 25;
const int CD_MEMORY_ALLOCATION_FAILED = 26;
const int CROTA_MEMORY_ALLOCATION_FAILED = 27;
const int PIXCRD_ARRAY_INDEX_OUT_OF_BOUNDS = 28;
const int WORLD_ARRAY_INDEX_OUT_OF_BOUNDS = 29;
const int RESULT_ARRAY_INDEX_OUT_OF_BOUNDS = 30;

/*
 * Class:     ca_nrc_cadc_wcs_WCSLib
 * Method:    wcsp2s
 * Signature: (I[D[D[D[D[Ljava/lang/String;[Ljava/lang/String;[D[D[D[D[I[I[D[I[I[Ljava/lang/String;[D[D[D[D)I
 */
JNIEXPORT jint JNICALL Java_ca_nrc_cadc_wcs_WCSLib_wcsp2s
(
    JNIEnv *env,                    /* Current JVM.                                         */
    jobject obj,                    /* Calling method.                                      */
    jint NAXIS,                     /* Number of axes (pixel and coordinate).               */
    jdoubleArray crpix,             /* CRPIXja cards for each pixel axis.                   */
    jdoubleArray pc,                /* PCi_ja  linear transformation matrix.                */
    jdoubleArray cdelt,             /* CDELTia cards for each coordinate axis.              */
    jdoubleArray crval,             /* CRVALia cards for each coordinate axis.              */
    jobjectArray cunit,             /* CUNITia cards for each coordinate axis.              */
    jobjectArray ctype,             /* CTYPEia cards for each coordinate axis.              */
    jdoubleArray lonpole,           /* LONPOLEa card.                                       */
    jdoubleArray latpole,           /* LATPOLEa card.                                       */
    jdoubleArray restfrq,           /* RESTFRQa card.                                       */
    jdoubleArray restwav,           /* RESTWAVa card.                                       */
    jintArray pvi,                  /* Axis number, as in PVi_ma (1-relative).              */
    jintArray pvm,                  /* Parameter number, ditto  (0-relative).               */
    jdoubleArray pvv,               /* Parameter value.                                     */
    jintArray psi,                  /* Axis number, as in PSi_ma (1-relative).              */
    jintArray psm,                  /* Parameter number, ditto  (0-relative).               */
    jobjectArray psv,               /* Parameter value.                                     */
    jdoubleArray cd,                /* CDi_ja linear transformation matrix.                 */
    jdoubleArray crota,             /* CROTAia cards for each coordinate axis.              */
    jdoubleArray pixcrd,            /* Pixel coordinates.                                   */
    jdoubleArray world,             /* World coordinates.                                   */
    jdoubleArray worldunits         /* World coordinate units.                              */
)

{
    jsize size;                     /* Array sizes.                                         */
    jdouble *CRPIX = NULL;          /* CRPIX cards.                                         */
    jdouble *PC = NULL;             /* PC matrix.                                           */
    jdouble *CDELT = NULL;          /* CDELT cards.                                         */
    jdouble *CRVAL = NULL;          /* CRVAL cards.                                         */
    jdouble *LONPOLE = NULL;        /* LONPOLE cards.                                       */
    jdouble *LATPOLE = NULL;        /* LATPOLE cards.                                       */
    jdouble *RESTFRQ = NULL;        /* RESTFRQ cards.                                       */
    jdouble *RESTWAV = NULL;        /* RESTWAV cards.                                       */
    jint *PVI = NULL;               /* PV axis number.                                      */
    jint *PVM = NULL;               /* PV parameter number.                                 */
    jdouble * PVV = NULL;           /* PV parameter value.                                  */
    jint *PSI = NULL;               /* PS axis number.                                      */
    jint *PSM = NULL;               /* PS parameter number.                                 */
    jdouble *CD = NULL;             /* CD cards.                                            */
    jdouble *CROTA = NULL;          /* CROTA cards.                                         */
    int i;                          /* Local variables.                                     */
    int altlin = 0;                 /* Alternative representations.                         */
    int alloc = 1;                  /* Allocate memory unconditionally for the arrays       */
    int status = 0;                 /* Return status.                                       */
    struct wcsprm *wcs;             /* Pointer to wcsprm struct.                            */
    double *imgcrd;					/* Array of intermediate world coordinates.             */
    double *PIXCRD;					/* Pixels coordinates to translate.                     */
    double *WORLD;					/* Translated World coordinates.                        */
    int stat[NCOORD];               /* Status return value for each coordinate.             */
    double phi[NCOORD];             /* Longitude in native coordinate system of projection. */
    double theta[NCOORD];           /* Latitude in native coordinate system of projection.  */

    /* Create arrays */
    const int NELEM = (*env)->GetArrayLength(env, pixcrd);
    imgcrd = malloc(NELEM * sizeof(double));
    PIXCRD = malloc(NELEM * sizeof(double));
    WORLD = malloc(NELEM * sizeof(double));
    for (i = 0; i < NELEM; i++)
    {
    	imgcrd[i] = 0.0;
    	PIXCRD[i] = 0.0;
    	WORLD[i] = 0.0;
    }

    /* Allocate space for wcsprm */
    wcs = malloc(sizeof(struct wcsprm));

    /* Set flag to -1 to initilize memory management */
    wcs->flag = -1;

    /* Allocate memory for the wcsprm arrays */
    if (status = wcsini(alloc, NAXIS, wcs))
    {
        /* Error allocating memory, return status code */
        return status;
    }

    /* Number of pixel and world coordinate elements,
     * given by the NAXIS or WCSAXESa keywords. */
    wcs->naxis = NAXIS;

    /* Coordinate reference pixels  */
    if (crpix)
    {
        CRPIX = (*env)->GetDoubleArrayElements(env, crpix, NULL);
        if (CRPIX) wcs->crpix = CRPIX;
        else status = CRPIX_MEMORY_ALLOCATION_FAILED;
    }

    /* Pixel coordinate transformation matrix */
    if (!status && pc)
    {
        PC = (*env)->GetDoubleArrayElements(env, pc, NULL);
        if (PC) wcs->pc = PC;
        else status = PC_MEMORY_ALLOCATION_FAILED;
    }

    /* Coordinate increments */
    if (!status && cdelt)
    {
        CDELT = (*env)->GetDoubleArrayElements(env, cdelt, NULL);
        if (CDELT) wcs->cdelt = CDELT;
        else status = CDELT_MEMORY_ALLOCATION_FAILED;
    }

    /* Coordinate reference values */
    if (!status && crval)
    {
        CRVAL = (*env)->GetDoubleArrayElements(env, crval, NULL);
        if (CRVAL) wcs->crval = CRVAL;
        else status = CRVAL_MEMORY_ALLOCATION_FAILED;
    }

    /* Units of measurement of CRVAL, CDELT, and CD */
    if (!status && cunit)
    {
        size = (*env)->GetArrayLength(env, cunit);
        for (i = 0; i < size; i++)
        {
            /* Object from object array */
            jobject jobj = (*env)->GetObjectArrayElement(env, cunit, i);
            if (jobj == NULL)
            {
                status = CUNIT_MEMORY_ALLOCATION_FAILED;
                break;
            }

            if ((*env)->ExceptionOccurred(env))
            {
                (*env)->DeleteLocalRef(env, jobj);
                (*env)->ExceptionClear(env);
                status = CUNIT_ARRAY_INDEX_OUT_OF_BOUNDS;
            }
            else
            {
                /* String value of the object */
                const char *CUNIT = (*env)->GetStringUTFChars(env, jobj, NULL);
                strcpy(wcs->cunit[i], CUNIT);

                /* Free object memory */
                (*env)->ReleaseStringUTFChars(env, jobj, CUNIT);
                (*env)->DeleteLocalRef(env, jobj);
            }
        }
    }

    /* Coordinate axis types */
    if (!status && ctype)
    {
        size = (*env)->GetArrayLength(env, ctype);
        for (i = 0; i < size; i++)
        {
            /* Object from object array */
            jobject jobj = (*env)->GetObjectArrayElement(env, ctype, i);
            if (jobj == NULL)
            {
                status = CTYPE_MEMORY_ALLOCATION_FAILED;
                break;
            }

            if ((*env)->ExceptionOccurred(env))
            {
                (*env)->DeleteLocalRef(env, jobj);
                (*env)->ExceptionClear(env);
                status = CTYPE_ARRAY_INDEX_OUT_OF_BOUNDS;
            }
            else
            {
                /* String value of the object */
                const char *CTYPE = (*env)->GetStringUTFChars(env, jobj, NULL);
                strcpy(wcs->ctype[i], CTYPE);

                /* Free object memory */
                (*env)->ReleaseStringUTFChars(env, jobj, CTYPE);
                (*env)->DeleteLocalRef(env, jobj);
            }
        }
    }

	/* Native longitude and latitude of the celestial pole */
    if (!status && lonpole)
    {
        LONPOLE = (*env)->GetDoubleArrayElements(env, lonpole, NULL);
        if (LONPOLE)
        {
        	if (LONPOLE[0] != UNDEFINED)
        		wcs->lonpole = LONPOLE[0];
        }
        else status = LONPOLE_MEMORY_ALLOCATION_FAILED;
    }

    if (!status && latpole)
    {
        LATPOLE = (*env)->GetDoubleArrayElements(env, latpole, NULL);
        if (LATPOLE)
        {
        	if (LATPOLE[0] != UNDEFINED)
        		wcs->latpole = LATPOLE[0];
        }
        else status = LATPOLE_MEMORY_ALLOCATION_FAILED;
    }

    /* Rest frequency and wavelength */
    if (!status && restfrq)
    {
        RESTFRQ = (*env)->GetDoubleArrayElements(env, restfrq, NULL);
        if (RESTFRQ)
        {
        	if (RESTFRQ[0] != UNDEFINED)
        		wcs->restfrq = RESTFRQ[0];
        }
        else status = RESTFRQ_MEMORY_ALLOCATION_FAILED;
    }

    if (!status && restwav)
    {
        RESTWAV = (*env)->GetDoubleArrayElements(env, restwav, NULL);
        if (RESTWAV)
        {
        	if (RESTWAV[0] != UNDEFINED)
        		wcs->restwav = RESTWAV[0];
        }
        else status = RESTWAV_MEMORY_ALLOCATION_FAILED;
    }

    /* PV */
    if (!status && pvi && pvm && pvv)
    {
        PVI = (*env)->GetIntArrayElements(env, pvi, NULL);
        PVM = (*env)->GetIntArrayElements(env, pvm, NULL);
        PVV = (*env)->GetDoubleArrayElements(env, pvv, NULL);

        if (PVI && PVM && PVV)
        {
            const int pv_size = (*env)->GetArrayLength(env, pvv);
            struct pvcard *PV = malloc(pv_size * sizeof(struct pvcard));
            wcs->npv = pv_size;
            for (i = 0; i < pv_size; i++)
            {
                PV[i].i = PVI[i];
                PV[i].m = PVM[i];
                PV[i].value = PVV[i];
                wcs->pv[i] = PV[i];
            }
            free(PV);
        }
    }

    /* PS */
    if (!status && psi && psm && psv)
    {
        PSI = (*env)->GetIntArrayElements(env, psi, NULL);
        PSM = (*env)->GetIntArrayElements(env, psm, NULL);

        if (PSI && PSM)
        {
            const int ps_size = (*env)->GetArrayLength(env, psi);
            struct pscard *PS = malloc(ps_size * sizeof(struct pscard));
            wcs->nps = ps_size;
            for (i = 0; i < ps_size; i++)
            {
                /* Object from object array */
                jobject jobj = (*env)->GetObjectArrayElement(env, psv, i);
                if (jobj == NULL)
                {
                    status = PS_MEMORY_ALLOCATION_FAILED;
                    break;
                }

                if ((*env)->ExceptionOccurred(env))
                {
                    (*env)->DeleteLocalRef(env, jobj);
                    (*env)->ExceptionClear(env);
                    status = PS_ARRAY_INDEX_OUT_OF_BOUNDS;
                }
                else
                {
                    /* String value of the object */
                    const char *PSV = (*env)->GetStringUTFChars(env, jobj, NULL);
                    strcpy(PS[i].value, PSV);
                    PS[i].i = PSI[i];
                    PS[i].m = PSM[i];
                    wcs->ps[i] = PS[i];

                    /* Free object memory */
                    (*env)->ReleaseStringUTFChars(env, jobj, PSV);
                    (*env)->DeleteLocalRef(env, jobj);
                }
            }
            free(PS);
        }
    }

    /* Bit flag represents if PC, CD, or CROTA cards are present */
    if (pc) altlin = 1;
    if (cd) altlin = 2;
    if (crota) altlin = 4;
    wcs->altlin = altlin;

    /* Pixel coordinate transformation matrix */
    if (!status && cd)
    {
        CD = (*env)->GetDoubleArrayElements(env, cd, NULL);
        if (CD) wcs->cd = CD;
        else status = CD_MEMORY_ALLOCATION_FAILED;
    }

    /* Coordinate rotation */
    if (!status && crota)
    {
        CROTA = (*env)->GetDoubleArrayElements(env, crota, NULL);
        if (CROTA) wcs->crota = CROTA;
        else status = CROTA_MEMORY_ALLOCATION_FAILED;
    }

    /* Pixel coordinates */
    if (!status)
    {
        (*env)->GetDoubleArrayRegion(env, pixcrd, 0, NELEM, PIXCRD);
        if ((*env)->ExceptionOccurred(env))
        {
            (*env)->ExceptionClear(env);
            status = PIXCRD_ARRAY_INDEX_OUT_OF_BOUNDS;
        }
    }

    /* World coordinates */
    if (!status)
    {
        (*env)->GetDoubleArrayRegion(env, world, 0, NELEM, WORLD);
        if ((*env)->ExceptionOccurred(env))
        {
            (*env)->ExceptionClear(env);
            status = WORLD_ARRAY_INDEX_OUT_OF_BOUNDS;
        }
    }

    /* Set flag to indicate the structure as been set and wcsset should be called */
    wcs->flag = 0;

    /* Call native lib */
    if (!status)
    {
        if (status = wcsp2s(wcs, NCOORD, NELEM, PIXCRD, imgcrd, phi, theta, WORLD, stat))
        {
        	fprintf(stderr, "ERROR %d from wcsset(): %s.\n", status, wcs_errmsg[status]);
            /* Add 12 to status to differentiate between initialization and runtime errors */
            status += 12;
        }
    }

    /* Copy coordinates back into world array */
    if (!status)
    {
        (*env)->SetDoubleArrayRegion(env, world, 0, NELEM, &WORLD[0]);
        if ((*env)->ExceptionOccurred(env))
        {
            (*env)->ExceptionClear(env);
            status = RESULT_ARRAY_INDEX_OUT_OF_BOUNDS;
        }
    }

    /* Copy coordinate units into units array */
    if (!status && cunit)
    {
        size = (*env)->GetArrayLength(env, cunit);
        for (i = 0; i < size; i++)
        {
            jstring js = (*env)->NewStringUTF(env, wcs->cunit[i]);
            (*env)->SetObjectArrayElement(env, worldunits, i, js);
            if ((*env)->ExceptionOccurred(env))
            {
                (*env)->ExceptionClear(env);
                status = RESULT_ARRAY_INDEX_OUT_OF_BOUNDS;
            }
            (*env)->DeleteLocalRef(env, js);
        }
    }

    /* Release to JVM */
    if (CRPIX) (*env)->ReleaseDoubleArrayElements(env, crpix, CRPIX, JNI_ABORT);
    if (PC) (*env)->ReleaseDoubleArrayElements(env, pc, PC, JNI_ABORT);
    if (CDELT) (*env)->ReleaseDoubleArrayElements(env, cdelt, CDELT, JNI_ABORT);
    if (CRVAL) (*env)->ReleaseDoubleArrayElements(env, crval, CRVAL, JNI_ABORT);
    if (LONPOLE) (*env)->ReleaseDoubleArrayElements(env, lonpole, LONPOLE, JNI_ABORT);
    if (LATPOLE) (*env)->ReleaseDoubleArrayElements(env, latpole, LATPOLE, JNI_ABORT);
    if (RESTFRQ) (*env)->ReleaseDoubleArrayElements(env, restfrq, RESTFRQ, JNI_ABORT);
    if (RESTWAV) (*env)->ReleaseDoubleArrayElements(env, restwav, RESTWAV, JNI_ABORT);
    if (PVI) (*env)->ReleaseIntArrayElements(env, pvi, PVI, JNI_ABORT);
    if (PVM) (*env)->ReleaseIntArrayElements(env, pvm, PVM, JNI_ABORT);
    if (PVV) (*env)->ReleaseDoubleArrayElements(env, pvv, PVV, JNI_ABORT);
    if (PSI) (*env)->ReleaseIntArrayElements(env, psi, PSI, JNI_ABORT);
    if (PSM) (*env)->ReleaseIntArrayElements(env, psm, PSM, JNI_ABORT);
    if (CD) (*env)->ReleaseDoubleArrayElements(env, cd, CD, JNI_ABORT);
    if (CROTA) (*env)->ReleaseDoubleArrayElements(env, crota, CROTA, JNI_ABORT);

    /* Release wcsprm struct memory */
    free(imgcrd);
    free(PIXCRD);
    free(WORLD);
    wcsfree(wcs);
    free(wcs);

    return status;
}

/*
 * Class:     ca_nrc_cadc_wcs_WCSLib
 * Method:    wcss2p
 * Signature: (I[D[D[D[D[Ljava/lang/String;[Ljava/lang/String;[D[D[D[D[I[I[D[I[I[Ljava/lang/String;[D[D[D[D)I
 */
JNIEXPORT jint JNICALL Java_ca_nrc_cadc_wcs_WCSLib_wcss2p
(
    JNIEnv *env,                    /* Current JVM.                                         */
    jobject obj,                    /* Calling method.                                      */
    jint NAXIS,                     /* Number of axes (pixel and coordinate).               */
    jdoubleArray crpix,             /* CRPIXja cards for each pixel axis.                   */
    jdoubleArray pc,                /* PCi_ja  linear transformation matrix.                */
    jdoubleArray cdelt,             /* CDELTia cards for each coordinate axis.              */
    jdoubleArray crval,             /* CRVALia cards for each coordinate axis.              */
    jobjectArray cunit,             /* CUNITia cards for each coordinate axis.              */
    jobjectArray ctype,             /* CTYPEia cards for each coordinate axis.              */
    jdoubleArray lonpole,           /* LONPOLEa card.                                       */
    jdoubleArray latpole,           /* LATPOLEa card.                                       */
    jdoubleArray restfrq,           /* RESTFRQa card.                                       */
    jdoubleArray restwav,           /* RESTWAVa card.                                       */
    jintArray pvi,                  /* Axis number, as in PVi_ma (1-relative).              */
    jintArray pvm,                  /* Parameter number, ditto  (0-relative).               */
    jdoubleArray pvv,               /* Parameter value.                                     */
    jintArray psi,                  /* Axis number, as in PSi_ma (1-relative).              */
    jintArray psm,                  /* Parameter number, ditto  (0-relative).               */
    jobjectArray psv,               /* Parameter value.                                     */
    jdoubleArray cd,                /* CDi_ja linear transformation matrix.                 */
    jdoubleArray crota,             /* CROTAia cards for each coordinate axis.              */
    jdoubleArray world,             /* World coordinates.                                   */
    jdoubleArray pixcrd,            /* Pixel coordinates.                                   */
    jobjectArray pixcrdunits        /* Pixel coordinate units.                              */
)

{
    jsize size;                     /* Array sizes.                                         */
    jdouble *CRPIX = NULL;          /* CRPIX cards.                                         */
    jdouble *PC = NULL;             /* PC matrix.                                           */
    jdouble *CDELT = NULL;          /* CDELT cards.                                         */
    jdouble *CRVAL = NULL;          /* CRVAL cards.                                         */
    jdouble *LONPOLE = NULL;        /* LONPOLE cards.                                       */
    jdouble *LATPOLE = NULL;        /* LATPOLE cards.                                       */
    jdouble *RESTFRQ = NULL;        /* RESTFRQ cards.                                       */
    jdouble *RESTWAV = NULL;        /* RESTWAV cards.                                       */
    jint *PVI = NULL;               /* PV axis number.                                      */
    jint *PVM = NULL;               /* PV parameter number.                                 */
    jdouble *PVV = NULL;            /* PV parameter value.                                  */
    jint *PSI = NULL;               /* PS axis number.                                      */
    jint *PSM = NULL;               /* PS parameter number.                                  */
    jdouble *CD = NULL;             /* CD cards.                                            */
    jdouble *CROTA = NULL;          /* CROTA cards.                                         */
    int i;                          /* Local variables.                                     */
    int altlin;                     /* Alternative representations.                         */
    int alloc = 1;                  /* Allocate memory unconditionally for the arrays       */
    int status = 0;                 /* Return status.                                       */
    struct wcsprm *wcs;             /* Pointer to wcsprm struct.                            */
    double *imgcrd;					/* Array of intermediate world coordinates.             */
    double *PIXCRD;					/* Translated Pixels coordinates.                       */
    double *WORLD;					/* World coordinates to translate.                      */
    int stat[NCOORD];               /* Status return value for each coordinate.             */
    double phi[NCOORD];             /* Longitude in native coordinate system of projection. */
    double theta[NCOORD];           /* Latitude in native coordinate system of projection.  */

    /* Create arrays */
    const int NELEM = (*env)->GetArrayLength(env, world);
    imgcrd = malloc(NELEM * sizeof(double));
    WORLD = malloc(NELEM * sizeof(double));
    PIXCRD = malloc(NELEM * sizeof(double));
    for (i = 0; i < NELEM; i++)
    {
    	imgcrd[i] = 0.0;
    	WORLD[i] = 0.0;
    	PIXCRD[i] = 0.0;
    }

    /* Allocate space for wcsprm */
    wcs = malloc(sizeof(struct wcsprm));

    /* Set flag to -1 to initialize memory management */
    wcs->flag = -1;

    /* Allocate memory for the wcsprm arrays */
    if (status = wcsini(alloc, NAXIS, wcs))
    {
        /* Error allocating memory, return status code */
        return status;
    }

    /* Number of pixel and world coordinate elements,
     * given by the NAXIS or WCSAXESa keywords. */
    wcs->naxis = NAXIS;

    /* Coordinate reference pixels  */
    if (crpix)
    {
        CRPIX = (*env)->GetDoubleArrayElements(env, crpix, NULL);
        if (CRPIX) wcs->crpix = CRPIX;
        else status = CRPIX_MEMORY_ALLOCATION_FAILED;
    }

    /* Pixel coordinate transformation matrix */
    if (!status && pc)
    {
        PC = (*env)->GetDoubleArrayElements(env, pc, NULL);
        if (PC) wcs->pc = PC;
        else status = PC_MEMORY_ALLOCATION_FAILED;
    }

    /* Coordinate increments */
    if (!status && cdelt)
    {
        CDELT = (*env)->GetDoubleArrayElements(env, cdelt, NULL);
        if (CDELT) wcs->cdelt = CDELT;
        else status = CDELT_MEMORY_ALLOCATION_FAILED;
    }

    /* Coordinate reference values */
    if (!status && crval)
    {
        CRVAL = (*env)->GetDoubleArrayElements(env, crval, NULL);
        if (CRVAL) wcs->crval = CRVAL;
        else status = CRVAL_MEMORY_ALLOCATION_FAILED;
    }

    /* Units of measurement of CRVAL, CDELT, and CD */
    if (!status && cunit)
    {
        size = (*env)->GetArrayLength(env, cunit);
        for (i = 0; i < size; i++)
        {
            /* Object from object array */
            jobject jobj = (*env)->GetObjectArrayElement(env, cunit, i);
            if (jobj == NULL)
            {
                status = CUNIT_MEMORY_ALLOCATION_FAILED;
                break;
            }

            if ((*env)->ExceptionOccurred(env))
            {
                (*env)->DeleteLocalRef(env, jobj);
                (*env)->ExceptionClear(env);
                status = CUNIT_ARRAY_INDEX_OUT_OF_BOUNDS;
            }
            else
            {
                /* String value of the object */
                const char *CUNIT = (*env)->GetStringUTFChars(env, jobj, NULL);
                strcpy(wcs->cunit[i], CUNIT);

                /* Free object memory */
                (*env)->ReleaseStringUTFChars(env, jobj, CUNIT);
                (*env)->DeleteLocalRef(env, jobj);
            }
        }
    }

    /* Coordinate axis types */
    if (!status && ctype)
    {
        size = (*env)->GetArrayLength(env, ctype);
        for (i = 0; i < size; i++)
        {
            /* Object from object array */
            jobject jobj = (*env)->GetObjectArrayElement(env, ctype, i);
            if (jobj == NULL)
            {
                status = CTYPE_MEMORY_ALLOCATION_FAILED;
                break;
            }

            if ((*env)->ExceptionOccurred(env))
            {
                (*env)->DeleteLocalRef(env, jobj);
                (*env)->ExceptionClear(env);
                status = CTYPE_ARRAY_INDEX_OUT_OF_BOUNDS;
            }
            else
            {
                /* String value of the object */
                const char *CTYPE = (*env)->GetStringUTFChars(env, jobj, NULL);
                strcpy(wcs->ctype[i], CTYPE);

                /* Free object memory */
                (*env)->ReleaseStringUTFChars(env, jobj, CTYPE);
                (*env)->DeleteLocalRef(env, jobj);
            }
        }
    }

	/* Native longitude and latitude of the celestial pole */
    if (!status && lonpole)
    {
        LONPOLE = (*env)->GetDoubleArrayElements(env, lonpole, NULL);
        if (LONPOLE)
        {
        	if (LONPOLE[0] != UNDEFINED)
        		wcs->lonpole = LONPOLE[0];
        }
        else status = LONPOLE_MEMORY_ALLOCATION_FAILED;
    }

    if (!status && latpole)
    {
        LATPOLE = (*env)->GetDoubleArrayElements(env, latpole, NULL);
        if (LATPOLE)
        {
        	if (LATPOLE[0] != UNDEFINED)
        		wcs->latpole = LATPOLE[0];
        }
        else status = LATPOLE_MEMORY_ALLOCATION_FAILED;
    }

    /* Rest frequency and wavelength */
    if (!status && restfrq)
    {
        RESTFRQ = (*env)->GetDoubleArrayElements(env, restfrq, NULL);
        if (RESTFRQ)
        {
        	if (RESTFRQ[0] != UNDEFINED)
        		wcs->restfrq = RESTFRQ[0];
        }
        else status = RESTFRQ_MEMORY_ALLOCATION_FAILED;
    }

    if (!status && restwav)
    {
        RESTWAV = (*env)->GetDoubleArrayElements(env, restwav, NULL);
        if (RESTWAV)
        {
        	if (RESTWAV[0] != UNDEFINED)
        		wcs->restwav = RESTWAV[0];
        }
        else status = RESTWAV_MEMORY_ALLOCATION_FAILED;
    }

    /*  PV */
    if (!status && pvi && pvm && pvv)
    {
        PVI = (*env)->GetIntArrayElements(env, pvi, NULL);
        PVM = (*env)->GetIntArrayElements(env, pvm, NULL);
        PVV = (*env)->GetDoubleArrayElements(env, pvv, NULL);

        if (PVI && PVM && PVV)
        {
            const int pv_size = (*env)->GetArrayLength(env, pvv);
            struct pvcard *PV = malloc(pv_size * sizeof(struct pvcard));
            wcs->npv = pv_size;
            for (i = 0; i < pv_size; i++)
            {
                PV[i].i = PVI[i];
                PV[i].m = PVM[i];
                PV[i].value = PVV[i];
                wcs->pv[i] = PV[i];
            }
            free(PV);
        }
    }

    /*  PS */
    if (!status && psi && psm && psv)
    {
        PSI = (*env)->GetIntArrayElements(env, psi, NULL);
        PSM = (*env)->GetIntArrayElements(env, psm, NULL);

        if (PSI && PSM)
        {
            const int ps_size = (*env)->GetArrayLength(env, psi);
            struct pscard *PS = malloc(ps_size * sizeof(struct pscard));
            wcs->nps = ps_size;
            for (i = 0; i < size; i++)
            {
                /* Object from object array */
                jobject jobj = (*env)->GetObjectArrayElement(env, psv, i);
                if (jobj == NULL)
                {
                    status = PS_MEMORY_ALLOCATION_FAILED;
                    break;
                }

                if ((*env)->ExceptionOccurred(env))
                {
                    (*env)->DeleteLocalRef(env, jobj);
                    (*env)->ExceptionClear(env);
                    status = PS_ARRAY_INDEX_OUT_OF_BOUNDS;
                }
                else
                {
                    /* String value of the object */
                    const char *PSV = (*env)->GetStringUTFChars(env, jobj, NULL);
                    strcpy(PS[i].value, PSV);
                    PS[i].i = PSI[i];
                    PS[i].m = PSM[i];
                    wcs->ps[i] = PS[i];

                    /* Free object memory */
                    (*env)->ReleaseStringUTFChars(env, jobj, PSV);
                    (*env)->DeleteLocalRef(env, jobj);
                }
            }
            free(PS);
        }
    }

    /* Bit flag represents if PC, CD, or CROTA cards are present */
    if (pc) altlin = 1;
    if (cd) altlin = 2;
    if (crota) altlin = 4;
    wcs->altlin = altlin;

    /* Pixel coordinate transformation matrix */
    if (!status && cd)
    {
        CD = (*env)->GetDoubleArrayElements(env, cd, NULL);
        if (CD) wcs->cd = CD;
        else status = CD_MEMORY_ALLOCATION_FAILED;
    }

    /* Coordinate rotation */
    if (!status && crota)
    {
        CROTA = (*env)->GetDoubleArrayElements(env, crota, NULL);
        if (CROTA) wcs->crota = CROTA;
        else status = CROTA_MEMORY_ALLOCATION_FAILED;
    }

    /* World coordinates */
    if (!status)
    {
        (*env)->GetDoubleArrayRegion(env, world, 0, NELEM, WORLD);
        if ((*env)->ExceptionOccurred(env))
        {
            (*env)->ExceptionClear(env);
            status = WORLD_ARRAY_INDEX_OUT_OF_BOUNDS;
        }
    }

    /* Pixel coordinates */
    if (!status)
    {
        (*env)->GetDoubleArrayRegion(env, pixcrd, 0, NELEM, PIXCRD);
        if ((*env)->ExceptionOccurred(env))
        {
            (*env)->ExceptionClear(env);
            status = PIXCRD_ARRAY_INDEX_OUT_OF_BOUNDS;
        }
    }

    /* Set flag to indicate the structure as been set and wcsset should be called */
    wcs->flag = 0;

    /* Call native lib */
    if (!status)
    {
        if (status = wcss2p(wcs, NCOORD, NELEM, WORLD, phi, theta, imgcrd, PIXCRD, stat))
        {
            /* Add 12 to status to differentiate between initialization and runtime errors */
            status += 12;
        }
    }

    /* Copy coordinates back into world array */
    if (!status)
    {
        (*env)->SetDoubleArrayRegion(env, pixcrd, 0, NELEM, &PIXCRD[0]);
        if ((*env)->ExceptionOccurred(env))
        {
            (*env)->ExceptionClear(env);
            status = RESULT_ARRAY_INDEX_OUT_OF_BOUNDS;
        }
    }

    /* Copy coordinate units into units array */
    if (!status && cunit)
    {
        size = (*env)->GetArrayLength(env, cunit);
        for (i = 0; i < size; i++)
        {
            jstring js = (*env)->NewStringUTF(env, wcs->cunit[i]);
            (*env)->SetObjectArrayElement(env, pixcrdunits, i, js);
            if ((*env)->ExceptionOccurred(env))
            {
                (*env)->ExceptionClear(env);
                status = RESULT_ARRAY_INDEX_OUT_OF_BOUNDS;
            }
            (*env)->DeleteLocalRef(env, js);
        }
    }

    /* Release to JVM */
    if (CRPIX) (*env)->ReleaseDoubleArrayElements(env, crpix, CRPIX, JNI_ABORT);
    if (PC) (*env)->ReleaseDoubleArrayElements(env, pc, PC, JNI_ABORT);
    if (CDELT) (*env)->ReleaseDoubleArrayElements(env, cdelt, CDELT, JNI_ABORT);
    if (CRVAL) (*env)->ReleaseDoubleArrayElements(env, crval, CRVAL, JNI_ABORT);
    if (LONPOLE) (*env)->ReleaseDoubleArrayElements(env, lonpole, LONPOLE, JNI_ABORT);
    if (LATPOLE) (*env)->ReleaseDoubleArrayElements(env, latpole, LATPOLE, JNI_ABORT);
    if (RESTFRQ) (*env)->ReleaseDoubleArrayElements(env, restfrq, RESTFRQ, JNI_ABORT);
    if (RESTWAV) (*env)->ReleaseDoubleArrayElements(env, restwav, RESTWAV, JNI_ABORT);
    if (PVI) (*env)->ReleaseIntArrayElements(env, pvi, PVI, JNI_ABORT);
    if (PVM) (*env)->ReleaseIntArrayElements(env, pvm, PVM, JNI_ABORT);
    if (PVV) (*env)->ReleaseDoubleArrayElements(env, pvv, PVV, JNI_ABORT);
    if (PSI) (*env)->ReleaseIntArrayElements(env, psi, PSI, JNI_ABORT);
    if (PSM) (*env)->ReleaseIntArrayElements(env, psm, PSM, JNI_ABORT);
    if (CD) (*env)->ReleaseDoubleArrayElements(env, cd, CD, JNI_ABORT);
    if (CROTA) (*env)->ReleaseDoubleArrayElements(env, crota, CROTA, JNI_ABORT);

    /* Release wcsprm struct memory */
    free(imgcrd);
    free(PIXCRD);
    free(WORLD);
    wcsfree(wcs);
    free(wcs);

    return status;
}

/*
 * Class:     ca_nrc_cadc_wcs_WCSLib
 * Method:    wcssptr
 * Signature: (I[D[D[D[D[Ljava/lang/String;[Ljava/lang/String;[D[D[D[D[I[I[D[I[I[Ljava/lang/String;[D[DILjava/lang/String;)I
 */
JNIEXPORT jint JNICALL Java_ca_nrc_cadc_wcs_WCSLib_wcssptr
(
    JNIEnv *env,                    /* Current JVM.                                         */
    jobject obj,                    /* Calling method.                                      */
    jint NAXIS,                     /* Number of axes (pixel and coordinate).               */
    jdoubleArray crpix,             /* CRPIXja cards for each pixel axis.                   */
    jdoubleArray pc,                /* PCi_ja  linear transformation matrix.                */
    jdoubleArray cdelt,             /* CDELTia cards for each coordinate axis.              */
    jdoubleArray crval,             /* CRVALia cards for each coordinate axis.              */
    jobjectArray cunit,             /* CUNITia cards for each coordinate axis.              */
    jobjectArray ctype,             /* CTYPEia cards for each coordinate axis.              */
    jdoubleArray lonpole,           /* LONPOLEa card.                                       */
    jdoubleArray latpole,           /* LATPOLEa card.                                       */
    jdoubleArray restfrq,           /* RESTFRQa card.                                       */
    jdoubleArray restwav,           /* RESTWAVa card.                                       */
    jintArray pvi,                  /* Axis number, as in PVi_ma (1-relative).              */
    jintArray pvm,                  /* Parameter number, ditto  (0-relative).               */
    jdoubleArray pvv,               /* Parameter value.                                     */
    jintArray psi,                  /* Axis number, as in PSi_ma (1-relative).              */
    jintArray psm,                  /* Parameter number, ditto  (0-relative).               */
    jobjectArray psv,               /* Parameter value.                                     */
    jdoubleArray cd,                /* CDi_ja linear transformation matrix.                 */
    jdoubleArray crota,             /* CROTAia cards for each coordinate axis.              */
    jint spectral_axis,             /* Spectral aixs.                                       */
    jstring spectral_ctype          /* Spectral ctype for translation.                      */
)

{
    jsize size;                     /* Array sizes.                                         */
    jdouble *CRPIX = NULL;          /* CRPIX cards.                                         */
    jdouble *PC = NULL;             /* PC matrix.                                           */
    jdouble *CDELT = NULL;          /* CDELT cards.                                         */
    jdouble *CRVAL = NULL;          /* CRVAL cards.                                         */
    jdouble *LONPOLE = NULL;        /* LONPOLE cards.                                       */
    jdouble *LATPOLE = NULL;        /* LATPOLE cards.                                       */
    jdouble *RESTFRQ = NULL;        /* RESTFRQ cards.                                       */
    jdouble *RESTWAV = NULL;        /* RESTWAV cards.                                       */
    jint *PVI = NULL;               /* PV axis number.                                      */
    jint *PVM = NULL;               /* PV parameter number.                                 */
    jdouble *PVV = NULL;            /* PV parameter value.                                  */
    jint *PSI = NULL;               /* PS axis number.                                      */
    jint *PSM = NULL;               /* PS parameter number.                                  */
    jdouble *CD = NULL;             /* CD cards.                                            */
    jdouble *CROTA = NULL;          /* CROTA cards.                                         */
    int i;                          /* Local variables.                                     */
    int altlin;                     /* Alternative representations.                         */
    int alloc = 1;                  /* Allocate memory unconditionally for the arrays       */
    int status = 0;                 /* Return status.                                       */
    char ctypeS[9];                 /* Spectral ctype                                       */
    struct wcsprm *wcs;             /* Pointer to wcsprm struct.                            */

    /* Allocate space for wcsprm */
    wcs = malloc(sizeof(struct wcsprm));

    /* Set flag to -1 to initialize memory management */
    wcs->flag = -1;

    /* Allocate memory for the wcsprm arrays */
    if (status = wcsini(alloc, NAXIS, wcs))
    {
        /* Error allocating memory, return status code */
        return status;
    }

    /* Number of pixel and world coordinate elements,
     * given by the NAXIS or WCSAXESa keywords. */
    wcs->naxis = NAXIS;

    /* Coordinate reference pixels  */
    if (crpix)
    {
        CRPIX = (*env)->GetDoubleArrayElements(env, crpix, NULL);
        if (CRPIX) wcs->crpix = CRPIX;
        else status = CRPIX_MEMORY_ALLOCATION_FAILED;
    }

    /* Pixel coordinate transformation matrix */
    if (!status && pc)
    {
        PC = (*env)->GetDoubleArrayElements(env, pc, NULL);
        if (PC) wcs->pc = PC;
        else status = PC_MEMORY_ALLOCATION_FAILED;
    }

    /* Coordinate increments */
    if (!status && cdelt)
    {
        CDELT = (*env)->GetDoubleArrayElements(env, cdelt, NULL);
        if (CDELT) wcs->cdelt = CDELT;
        else status = CDELT_MEMORY_ALLOCATION_FAILED;
    }

    /* Coordinate reference values */
    if (!status && crval)
    {
        CRVAL = (*env)->GetDoubleArrayElements(env, crval, NULL);
        if (CRVAL) wcs->crval = CRVAL;
        else status = CRVAL_MEMORY_ALLOCATION_FAILED;
    }

    /* Units of measurement of CRVAL, CDELT, and CD */
    if (!status && cunit)
    {
        size = (*env)->GetArrayLength(env, cunit);
        for (i = 0; i < size; i++)
        {
            /* Object from object array */
            jobject jobj = (*env)->GetObjectArrayElement(env, cunit, i);
            if (jobj == NULL)
            {
                status = CUNIT_MEMORY_ALLOCATION_FAILED;
                break;
            }

            if ((*env)->ExceptionOccurred(env))
            {
                (*env)->DeleteLocalRef(env, jobj);
                (*env)->ExceptionClear(env);
                status = CUNIT_ARRAY_INDEX_OUT_OF_BOUNDS;
            }
            else
            {
                /* String value of the object */
                const char *CUNIT = (*env)->GetStringUTFChars(env, jobj, NULL);
                strcpy(wcs->cunit[i], CUNIT);

                /* Free object memory */
                (*env)->ReleaseStringUTFChars(env, jobj, CUNIT);
                (*env)->DeleteLocalRef(env, jobj);
            }
        }
    }

    /* Coordinate axis types */
    if (!status && ctype)
    {
        size = (*env)->GetArrayLength(env, ctype);
        for (i = 0; i < size; i++)
        {
            /* Object from object array */
            jobject jobj = (*env)->GetObjectArrayElement(env, ctype, i);
            if (jobj == NULL)
            {
                status = CTYPE_MEMORY_ALLOCATION_FAILED;
                break;
            }

            if ((*env)->ExceptionOccurred(env))
            {
                (*env)->DeleteLocalRef(env, jobj);
                (*env)->ExceptionClear(env);
                status = CTYPE_ARRAY_INDEX_OUT_OF_BOUNDS;
            }
            else
            {
                /* String value of the object */
                const char *CTYPE = (*env)->GetStringUTFChars(env, jobj, NULL);
                strcpy(wcs->ctype[i], CTYPE);

                /* Free object memory */
                (*env)->ReleaseStringUTFChars(env, jobj, CTYPE);
                (*env)->DeleteLocalRef(env, jobj);
            }
        }
    }

    /* Native longitude and latitude of the celestial pole */
    if (!status && lonpole)
    {
        LONPOLE = (*env)->GetDoubleArrayElements(env, lonpole, NULL);
        if (LONPOLE)
        {
        	if (LONPOLE[0] != UNDEFINED)
        		wcs->lonpole = LONPOLE[0];
        }
        else status = LONPOLE_MEMORY_ALLOCATION_FAILED;
    }

    if (!status && latpole)
    {
        LATPOLE = (*env)->GetDoubleArrayElements(env, latpole, NULL);
        if (LATPOLE)
        {
        	if (LATPOLE[0] != UNDEFINED)
        		wcs->latpole = LATPOLE[0];
        }
        else status = LATPOLE_MEMORY_ALLOCATION_FAILED;
    }

    /* Rest frequency and wavelength */
    if (!status && restfrq)
    {
        RESTFRQ = (*env)->GetDoubleArrayElements(env, restfrq, NULL);
        if (RESTFRQ)
        {
        	if (RESTFRQ[0] != UNDEFINED)
        		wcs->restfrq = RESTFRQ[0];
        }
        else status = RESTFRQ_MEMORY_ALLOCATION_FAILED;
    }

    if (!status && restwav)
    {
        RESTWAV = (*env)->GetDoubleArrayElements(env, restwav, NULL);
        if (RESTWAV)
        {
        	if (RESTWAV[0] != UNDEFINED)
        		wcs->restwav = RESTWAV[0];
        }
        else status = RESTWAV_MEMORY_ALLOCATION_FAILED;
    }

    /*  PV */
    if (!status && pvi && pvm && pvv)
    {
        PVI = (*env)->GetIntArrayElements(env, pvi, NULL);
        PVM = (*env)->GetIntArrayElements(env, pvm, NULL);
        PVV = (*env)->GetDoubleArrayElements(env, pvv, NULL);

        if (PVI && PVM && PVV)
        {
            const int pv_size = (*env)->GetArrayLength(env, pvv);
            struct pvcard *PV = malloc(pv_size * sizeof(struct pvcard));
            wcs->npv = size;
            for (i = 0; i < size; i++)
            {
                PV[i].i = PVI[i];
                PV[i].m = PVM[i];
                PV[i].value = PVV[i];
                wcs->pv[i] = PV[i];
            }
            free(PV);
        }
    }

    /*  PS */
    if (!status && psi && psm && psv)
    {
        PSI = (*env)->GetIntArrayElements(env, psi, NULL);
        PSM = (*env)->GetIntArrayElements(env, psm, NULL);

        if (PSI && PSM)
        {
            const int ps_size = (*env)->GetArrayLength(env, psi);
            struct pscard *PS = malloc(ps_size * sizeof(struct pscard));
            wcs->nps = size;
            for (i = 0; i < size; i++)
            {
                /* Object from object array */
                jobject jobj = (*env)->GetObjectArrayElement(env, psv, i);
                if (jobj == NULL)
                {
                    status = PS_MEMORY_ALLOCATION_FAILED;
                    break;
                }

                if ((*env)->ExceptionOccurred(env))
                {
                    (*env)->DeleteLocalRef(env, jobj);
                    (*env)->ExceptionClear(env);
                    status = PS_ARRAY_INDEX_OUT_OF_BOUNDS;
                }
                else
                {
                    /* String value of the object */
                    const char *PSV = (*env)->GetStringUTFChars(env, jobj, NULL);
                    strcpy(PS[i].value, PSV);
                    PS[i].i = PSI[i];
                    PS[i].m = PSM[i];
                    wcs->ps[i] = PS[i];

                    /* Free object memory */
                    (*env)->ReleaseStringUTFChars(env, jobj, PSV);
                    (*env)->DeleteLocalRef(env, jobj);
                }
            }
            free(PS);
        }
    }

    /* Bit flag represents if PC, CD, or CROTA cards are present */
    if (pc) altlin = 1;
    if (cd) altlin = 2;
    if (crota) altlin = 4;
    wcs->altlin = altlin;

    /* Pixel coordinate transformation matrix */
    if (!status && cd)
    {
        CD = (*env)->GetDoubleArrayElements(env, cd, NULL);
        if (CD) wcs->cd = CD;
        else status = CD_MEMORY_ALLOCATION_FAILED;
    }

    /* Coordinate rotation */
    if (!status && crota)
    {
        CROTA = (*env)->GetDoubleArrayElements(env, crota, NULL);
        if (CROTA) wcs->crota = CROTA;
        else status = CROTA_MEMORY_ALLOCATION_FAILED;
    }

    /* Spectral type */
    if (!status)
    {
        const char *spctype = (*env)->GetStringUTFChars(env, spectral_ctype, NULL);
        strcpy(ctypeS, spctype);
        (*env)->ReleaseStringUTFChars(env, spectral_ctype, spctype);
    }

    /* Set flag to indicate the structure as been set and wcsset should be called */
    wcs->flag = 0;

    /* Call native lib */
    if (!status)
    {
        if (status = wcssptr(wcs, &spectral_axis, ctypeS))
        {
            /* Add 12 to status to differentiate between initialization and runtime errors */
            status += 12;
        }
    }

    if (!status)
    {
        if (status = wcsset(wcs)) {
            status += 12;
        }
    }

    if (!status && LONPOLE[0] == UNDEFINED && !lonpole == UNDEFINED)
    {
        (*env)->SetDoubleArrayRegion(env, lonpole, 0, 1, &LONPOLE[0]);
        if ((*env)->ExceptionOccurred(env))
        {
            (*env)->ExceptionClear(env);
            status = RESULT_ARRAY_INDEX_OUT_OF_BOUNDS;
        }
    }
    if (!status && LATPOLE[0] == UNDEFINED && !latpole == +90.0)
    {
        (*env)->SetDoubleArrayRegion(env, latpole, 0, 1, &LATPOLE[0]);
        if ((*env)->ExceptionOccurred(env))
        {
            (*env)->ExceptionClear(env);
            status = RESULT_ARRAY_INDEX_OUT_OF_BOUNDS;
        }
    }
    if (!status && RESTFRQ[0] == UNDEFINED && !restfrq == 0.0)
    {
        (*env)->SetDoubleArrayRegion(env, restfrq, 0, 1, &RESTFRQ[0]);
        if ((*env)->ExceptionOccurred(env))
        {
            (*env)->ExceptionClear(env);
            status = RESULT_ARRAY_INDEX_OUT_OF_BOUNDS;
        }
    }
    if (!status && RESTWAV[0] == UNDEFINED && !restwav == 0.0)
    {
        (*env)->SetDoubleArrayRegion(env, restwav, 0, 1, &RESTWAV[0]);
        if ((*env)->ExceptionOccurred(env))
        {
            (*env)->ExceptionClear(env);
            status = RESULT_ARRAY_INDEX_OUT_OF_BOUNDS;
        }
    }

    if (!status && CRPIX)
    {
        (*env)->SetDoubleArrayRegion(env, crpix, 0, NAXIS, &CRPIX[0]);
        if ((*env)->ExceptionOccurred(env))
        {
            (*env)->ExceptionClear(env);
            status = RESULT_ARRAY_INDEX_OUT_OF_BOUNDS;
        }
    }

    if (!status && CDELT)
    {
        (*env)->SetDoubleArrayRegion(env, cdelt, 0, NAXIS, &CDELT[0]);
        if ((*env)->ExceptionOccurred(env))
        {
            (*env)->ExceptionClear(env);
            status = RESULT_ARRAY_INDEX_OUT_OF_BOUNDS;
        }
    }

    if (!status && CRVAL)
    {
        (*env)->SetDoubleArrayRegion(env, crval, 0, NAXIS, &CRVAL[0]);
        if ((*env)->ExceptionOccurred(env))
        {
            (*env)->ExceptionClear(env);
            status = RESULT_ARRAY_INDEX_OUT_OF_BOUNDS;
        }
    }

    if (!status)
    {
        jstring js = (*env)->NewStringUTF(env, *wcs->cunit);
        (*env)->SetObjectArrayElement(env, cunit, 0, js);
        if ((*env)->ExceptionOccurred(env))
        {
            (*env)->ExceptionClear(env);
            status = RESULT_ARRAY_INDEX_OUT_OF_BOUNDS;
        }
        (*env)->DeleteLocalRef(env, js);
    }

    if (!status)
    {
        jstring js = (*env)->NewStringUTF(env, *wcs->ctype);
        (*env)->SetObjectArrayElement(env, ctype, 0, js);
        if ((*env)->ExceptionOccurred(env))
        {
            (*env)->ExceptionClear(env);
            status = RESULT_ARRAY_INDEX_OUT_OF_BOUNDS;
        }
        (*env)->DeleteLocalRef(env, js);
    }

    /* Release to JVM */
    if (CRPIX) (*env)->ReleaseDoubleArrayElements(env, crpix, CRPIX, 0);
    if (PC) (*env)->ReleaseDoubleArrayElements(env, pc, PC, JNI_ABORT);
    if (CDELT) (*env)->ReleaseDoubleArrayElements(env, cdelt, CDELT, 0);
    if (CRVAL) (*env)->ReleaseDoubleArrayElements(env, crval, CRVAL, 0);
    if (LONPOLE) (*env)->ReleaseDoubleArrayElements(env, lonpole, LONPOLE, 0);
    if (LATPOLE) (*env)->ReleaseDoubleArrayElements(env, latpole, LATPOLE, 0);
    if (RESTFRQ) (*env)->ReleaseDoubleArrayElements(env, restfrq, RESTFRQ, 0);
    if (RESTWAV) (*env)->ReleaseDoubleArrayElements(env, restwav, RESTWAV, 0);
    if (PVI) (*env)->ReleaseIntArrayElements(env, pvi, PVI, JNI_ABORT);
    if (PVM) (*env)->ReleaseIntArrayElements(env, pvm, PVM, JNI_ABORT);
    if (PVV) (*env)->ReleaseDoubleArrayElements(env, pvv, PVV, JNI_ABORT);
    if (PSI) (*env)->ReleaseIntArrayElements(env, psi, PSI, JNI_ABORT);
    if (PSM) (*env)->ReleaseIntArrayElements(env, psm, PSM, JNI_ABORT);
    if (CD) (*env)->ReleaseDoubleArrayElements(env, cd, CD, JNI_ABORT);
    if (CROTA) (*env)->ReleaseDoubleArrayElements(env, crota, CROTA, JNI_ABORT);

    /* Release wcsprm struct memory */
    wcsfree(wcs);
    free(wcs);

    return status;
}
