#include <math.h>
#include <stdlib.h>
#include <string.h>
#include <stdio.h>
#include <jni.h>
#include "wcs.h"
#include "WCSLibTest.h"

const double tol = 1.0e-10;

const int NAXIS = 4;
const double CRPIX[4] =  {  513.0,  0.0,  0.0,  0.0};
const double PC[4][4] = {{    1.1,  0.0,  0.0,  0.0},
                         {    0.0,  1.0,  0.0,  0.1},
                         {    0.0,  0.0,  1.0,  0.0},
                         {    0.0,  0.2,  0.0,  1.0}};
const double CDELT[4] =  {-9.635265432e-6, 1.0, 0.1, -1.0};

char CTYPE[4][9] = {"WAVE-F2W", "XLAT-BON", "TIME-LOG", "XLON-BON"};

const double CRVAL[4] = {0.214982042, -30.0, 1.0, 150.0};
const double LONPOLE  = 150.0;
const double LATPOLE  = 999.0;
const double RESTFRQ  =   1.42040575e9;
const double RESTWAV  =   0.0;

JNIEXPORT jint JNICALL Java_ca_nrc_cadc_wcs_WCSLibTest_test(JNIEnv * env, jclass this)

{
#define NELEM 9

    int    i, j, k, lat, lng, stat[361], status;
    double freq, img[361][NELEM], lat1, lng1, phi[361], pixel1[361][NELEM],
           pixel2[361][NELEM], r, resid, residmax, theta[361], time,
           world1[361][NELEM], world2[361][NELEM];
    double *pcij;
    struct pvcard PV[3];
    struct wcsprm *wcs;

    int NPV = 3;

    PV[0].i = 4;
    PV[0].m = 1;
    PV[0].value =  0.0;

    PV[1].i = 4;
    PV[1].m = 2;
    PV[1].value = 90.0;

    PV[2].i = 2;
    PV[2].m = 1;
    PV[2].value = -30.0;

    wcs = malloc(sizeof(struct wcsprm));
    wcs->flag = -1;
    wcsini(1, NAXIS, wcs);

    for (j = 0; j < NAXIS; j++) {
        wcs->crpix[j] = CRPIX[j];
    }

    pcij = wcs->pc;
    for (i = 0; i < NAXIS; i++) {
        for (j = 0; j < NAXIS; j++) {
            *(pcij++) = PC[i][j];
        }
    }

    for (i = 0; i < NAXIS; i++) {
        wcs->cdelt[i] = CDELT[i];
    }

    for (i = 0; i < NAXIS; i++) {
        strcpy(wcs->ctype[i], &CTYPE[i][0]);
    }

    for (i = 0; i < NAXIS; i++) {
        wcs->crval[i] = CRVAL[i];
    }

    wcs->lonpole = LONPOLE;
    wcs->latpole = LATPOLE;

    wcs->restfrq = RESTFRQ;
    wcs->restwav = RESTWAV;

    wcs->npv = NPV;
    for (i = 0; i < NPV; i++) {
        wcs->pv[i] = PV[i];
    }

    if (status = wcsset(wcs)) {
        printf("wcsset ERROR%3d\n", status);
    }
    wcsprt(wcs);

    time = 1.0;
    freq = 1.42040595e9 - 180.0 * 62500.0;
    for (k = 0; k < 361; k++) {
        world1[k][0] = 0.0;
        world1[k][1] = 0.0;
        world1[k][2] = 0.0;
        world1[k][3] = 0.0;

        world1[k][2] = time;
        time *= 1.01;

        world1[k][wcs->spec] = 2.99792458e8 / freq;
        freq += 62500.0;
    }

    residmax = 0.0;
    //for (lat = 90; lat >= -90; lat--) {
        lat = 90;
        lat1 = (double)lat;

        for (lng = -180, k = 0; lng <= 180; lng++, k++) {
            lng1 = (double)lng;

            world1[k][wcs->lng] = lng1;
            world1[k][wcs->lat] = lat1;
        }

        printf("NCOORD %i, ", 361);
        printf("NELEM %i\n", NELEM);
        for (i = 0; i < NELEM; i++)
        {
            printf("WORLD[0][%i] %f, ", i, world1[0][i]);
            printf("PIXCRD[0][%i] %f\n", i, img[0][i]);
        }

        if (status = wcss2p(wcs, 361, NELEM, world1[0], phi, theta, img[0],
                            pixel1[0], stat)) {
            printf("   wcss2p(1) ERROR %2d (lat1 = %f)\n", status, lat1);
            //continue;
        }
        for(i = 0; i < NELEM; i++)
        {
            printf("stat[%i] %i\n", i, stat[i]);
        }
        for (i = 0; i < NELEM; i++)
        {
            printf("img[0][%i] %d\n", i, img[0][i]);
        }
        if (status = wcsp2s(wcs, 361, NELEM, pixel1[0], img[0], phi, theta,
                            world2[0], stat)) {
            printf("   wcsp2s ERROR %2d (lat1 = %f)\n", status, lat1);
            //continue;
        }

        if (status = wcss2p(wcs, 361, NELEM, world2[0], phi, theta, img[0],
                            pixel2[0], stat)) {
            printf("   wcss2p(2) ERROR %2d (lat1 = %f)\n", status, lat1);
            //continue;
        }

        for (k = 0; k < 361; k++) {
            resid = 0.0;
            for (i = 0; i < NAXIS; i++) {
                r = pixel2[k][i] - pixel1[k][i];
                resid += r*r;
            }

            resid = sqrt(resid);
            if (resid > residmax) residmax = resid;

            if (resid > tol) {
                printf("\nClosure error:\n"
                       "world1:%18.12f%18.12f%18.12f%18.12f\n"
                       "pixel1:%18.12f%18.12f%18.12f%18.12f\n"
                       "world2:%18.12f%18.12f%18.12f%18.12f\n"
                       "pixel2:%18.12f%18.12f%18.12f%18.12f\n",
                world1[k][0], world1[k][1], world1[k][2], world1[k][3],
                pixel1[k][0], pixel1[k][1], pixel1[k][2], pixel1[k][3],
                world2[k][0], world2[k][1], world2[k][2], world2[k][3],
                pixel2[k][0], pixel2[k][1], pixel2[k][2], pixel2[k][3]);
            }
        }
    //}

    printf("Maximum closure residual: %10.3e pixel.\n", residmax);

    wcsfree(wcs);
    free(wcs);

    return 0;
}
