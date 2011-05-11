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
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

/**
 * Class to determine the versions of the a java application and its member
 * packages. The version is determined based on the Implementation-Version
 * and Implementation-Vendor fields of the jars' manifest files.
 *
 * Changed 2011-04-21, -sz:
 * New methods added to retrieve version information of all JAR files in the 
 * system class path.
 */
public class VersionUtil
{
    public static final String PATH_SEPARATOR =  System.getProperty("path.separator");
    public static final String CLASS_PATH =  System.getProperty("java.class.path");
    public static final String NL =  System.getProperty("line.separator");
    public static final String ATT_VERSION =  "Implementation-Version";
    public static final String ATT_VENDOR =  "Implementation-Vendor";
    public static final String ATT_CLASS_PATH =  "Class-Path";

    /**
     * Return version of all JAR files in the system class path.
     * 2011-04-21
     * 
     * @return String of version information.
     * @author zhangsa
     */
    public static String allJarVersion()
    {
        StringBuilder sb = new StringBuilder();
        List<String> jarPaths = getAllJars();
        JarFile jf;
        for (String jarPath : jarPaths)
        {
            try
            {
                jf = new JarFile(jarPath);
                sb.append(jarCompleteVersion(jf));
            }
            catch (Exception e)
            {
                sb.append(String.format("Can not open JAR file of [%s].", jarPath)).append(NL);
                //e.printStackTrace();
            }
        }
        return sb.toString();
    }

    /**
     * Return version information of a JAR file.
     * 
     * @param jarFile JAR file object.
     * @return String of version information.
     */
    public static String jarVersion(JarFile jarFile)
    {
        String rtn = "";
        try
        {
            Manifest mf = jarFile.getManifest();
            if (mf == null)
                rtn = jarFile.getName() + " does not have manifest file." + NL;
            else
                rtn = formatDisplay(jarFile.getName(), mf);
        }
        catch (IOException e)
        {
            rtn = jarFile.getName() + " cannot open manifest file." + NL;
            //e.printStackTrace();
        }
        return rtn;
    }
    
    /**
     * Return version information of a JAR file,
     * as well as all included JAR files in its manifest class path.
     * 
     * @param jarFile
     * @return String of version information.
     */
    public static String jarCompleteVersion(JarFile jarFile)
    {
        StringBuilder sb = new StringBuilder();

        sb.append(NL);
        Manifest mf;
        try
        {
            mf = jarFile.getManifest();
            if (mf == null)
                throw new IOException(String.format("No mafifest file is defined in %s.",
                        jarFile.getName()));

            sb.append("JAR: ");
            sb.append(formatDisplay(jarFile.getName(), mf));
            sb.append("-------------------------------------").append(NL);

            List<String> allIncludedJars = getAllJars(jarFile);

            JarFile ijf; // included JAR file
            for (String jar : allIncludedJars)
            {
                ijf = new JarFile(jar);
                sb.append(jarVersion(ijf));
            }
            sb.append("-------------------------------------").append(NL);
        }
        catch (Exception e)
        {
            sb.append(String.format("Can not open manifest in JAR file [%s].", jarFile.getName()))
            .append(NL);
            //e.printStackTrace();
        }
        return sb.toString();
    }
    
    /**
     * Get all JAR files needed, based on the current class path.
     * 
     * @return a list of JAR file paths, as string.
     */
    public static List<String> getAllJars()
    {
        List<String> jars = new ArrayList<String>();
        
        String[] cpItems = CLASS_PATH.split(PATH_SEPARATOR);
        for (String cpItem : cpItems)
        {
            if (cpItem.endsWith(".jar"))
            {
                jars.add(cpItem);
            }
        }
        return jars;
    }

    /**
     * Return a list of all included JAR files of a JAR file.
     * 
     * @param jarFile The JAR file
     * @return List of JAR file paths
     * @throws IOException
     */
    public static List<String> getAllJars(JarFile jarFile) throws IOException
    {
        List<String> jars = new ArrayList<String>();

        String jarPath = jarFile.getName();

        String jarDir = jarPath.substring(0, jarPath.lastIndexOf(File.separatorChar) + 1);
        Manifest mf = jarFile.getManifest();

        String classPath = mf.getMainAttributes().getValue(ATT_CLASS_PATH);
        if (classPath == null) return jars;  //empty list

        String[] cpItems = classPath.split(" ");
        String jarItem;
        for (String cpItem : cpItems)
        {
            if (cpItem.endsWith(".jar"))
            {
                if (cpItem.indexOf(File.separatorChar) == -1)
                    jarItem = jarDir + cpItem; // cpItem is a relative path.
                else
                    jarItem = cpItem;

                jars.add(jarItem);
            }
        }
        return jars;
    }


    
    
    
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
			
			// bug fixing, 2011-04-20, -sz
			if (classPath == null)
			    return "No Class-Path defined in JAR: " + jarFile;
			
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
			manifest.getMainAttributes().getValue(ATT_VERSION);
		String vendor =
			manifest.getMainAttributes().getValue(ATT_VENDOR);

		if (version == null)
			version = "NA";

		if (vendor == null)
			vendor = "NA";

		return (src + ": " + version + " (c) " + vendor + "\n");
	}

    /**
     * @param args
     * @throws IOException 
     */
    public static void main(String[] args) throws IOException
    {
        System.out.print(allJarVersion());
    }

	
}
