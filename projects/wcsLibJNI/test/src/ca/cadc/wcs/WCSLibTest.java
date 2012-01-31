package ca.nrc.cadc.wcs;

public class WCSLibTest
{
    public static native int test();

    static 
    {
        System.loadLibrary("WCSLibTest");
    }

    public static void main(String[] args) 
    {
        System.out.println("WCSLibTest start");
        int status = WCSLibTest.test();
        System.out.println("WCSLibTest finish, status " + status);
    }

}
