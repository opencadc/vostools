/*
 ************************************************************************
 ****  C A N A D I A N   A S T R O N O M Y   D A T A   C E N T R E  *****
 *
 * (c) 2003.                            (c) 2003.
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
 *
 * 
 ****  C A N A D I A N   A S T R O N O M Y   D A T A   C E N T R E  *****
 ************************************************************************
 */

package ca.nrc.cadc.util;

import java.io.File;
import java.io.IOException;
import java.util.StringTokenizer;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

/**
 * Class to determine the versions of the a java application and its member
 * packages. The version is determined based on the Implementation-Version
 * and Implementation-Vendor fields of the jars' manifest files.
 *
 */
public class VersionUtil
{
	/**
	 * Determines the version of a package an object belongs to. The version
	 * and vendor are specified in the jar file of the package.
	 * @param o an object belonging to the package for which the version is
	 * required
	 * @return version of the package
	 */
	public static String version(Object o)
	{
		return version(o.getClass());
	}

	/**
	 * Determines the version of a package an object belongs to. The version
	 * and vendor are specified in the jar file of the package.
	 * @param clazz  The Class who belongs to the package of the desired
     *               version.
	 * @return String version of the package
	 */
    public static String version(final Class clazz)
    {
        if (clazz == null)
        {
            return null;
        }

        final Package p = clazz.getPackage();
        if (p == null)
        {
            return "";
        }

        final String version = p.getImplementationVersion();
		if (version == null)
        {
            return "";
        }

        String vendor = p.getImplementationVendor();
		if (vendor == null)
        {
            vendor = "NA";
        }

        return (version + " (c) " + vendor);
    }

    /**
	 * <p>Returs a string containing the version/vendor information for all the
	 * jar file in the classpath of the argument jarFile (typically the
	 * the application jar file). 
	 * </p><p>
	 * The version/vendor information for a given jar file is return only if
	 * the jar files has a manifest with the version/vendor information set 
	 * in the main header of the manifest. It is also required that the
	 * classpath is specified in the manifest of the argument jarFile.
	 * </p>
	 * @param jarFile path to the application jar file. Hint: most of the
	 * time <tt>System.getProperty("java.class.path", ".")</tt> suffices for
	 * obtaining the path to the application jar file.
	 * @return Formatted string containing information regarding the jar files
	 * in the class path and theri corresponding versions/vendors.
	 */
	public static String detailedVersion(String jarFile)
	{
		String result = "";
		try
		{
			JarFile jar = new JarFile(jarFile);
			Manifest manifest = jar.getManifest();
			if (manifest == null)
			{
				return (jarFile + ": NA \n");
			}
			result += formatDisplay(jarFile, manifest);
			String classPath =
				manifest.getMainAttributes().getValue("Class-Path");
			StringTokenizer jarFiles = new StringTokenizer(classPath, " ");
			String jarPath =
				jarFile.substring(
					0,
					jarFile.lastIndexOf(File.separatorChar) + 1);
			while (jarFiles.hasMoreTokens())
			{
				jarFile = jarFiles.nextToken();
				if (jarFile.indexOf(File.separatorChar) == -1)
				{
					// jar file path relative to the main jar file
					jarFile = jarPath + jarFile;

				}
				try
				{
					jar = new JarFile(jarFile);
				}
				catch (IOException e)
				{
					result += jarFile + ": Accessing error\n";
					continue;
				}
				manifest = jar.getManifest();
				if (manifest == null)
				{
					result += jarFile + ": NA \n";
					continue;
				}
				result = formatDisplay(result + jarFile, manifest);
			}
		}
		catch (IOException e)
		{
			result =
				"Errors reading the application jar file: " + jarFile + " \n";
		}

		return result;
	}

	/**
	 * Given a manifest, it appends vendor and version information to a given
	 * source string
	 * @param src source string to append info to
	 * @param manifest jar manifest file that contains vendor and version info.
	 * @return src string + appended version and vendor info.
	 */
	private static String formatDisplay(String src, Manifest manifest)
	{
		String version =
			manifest.getMainAttributes().getValue("Implementation-Version");
		String vendor =
			manifest.getMainAttributes().getValue("Implementation-Vendor");

		if (version == null)
			version = "NA";

		if (vendor == null)
			vendor = "NA";

		return (src + ": " + version + " (c) " + vendor + "\n");
	}

}
