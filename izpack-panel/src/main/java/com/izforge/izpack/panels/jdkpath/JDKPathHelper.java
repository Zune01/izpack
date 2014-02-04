package com.izforge.izpack.panels.jdkpath;

import java.io.File;
import java.util.Arrays;
import java.util.HashSet;
import java.util.StringTokenizer;

import com.coi.tools.os.win.MSWinConstants;
import com.izforge.izpack.api.data.InstallData;
import com.izforge.izpack.api.exception.NativeLibException;
import com.izforge.izpack.core.os.RegistryDefaultHandler;
import com.izforge.izpack.core.os.RegistryHandler;
import com.izforge.izpack.util.FileExecutor;
import com.izforge.izpack.util.Platform;
import com.izforge.izpack.util.Platform.Arch;

public class JDKPathHelper
{
    public static final String JDK_ROOT_KEY = "Software\\JavaSoft\\Java Development Kit";
    public static final String JDK_VALUE_NAME = "JavaHome";

    private String detectedVersion;
    private HashSet<String> badRegEntries;

    public enum VerifyResult {
        OK, BAD_VERSION, BAD_REAL_PATH, BAD_REG_PATH, BAD_BITNESS_32, BAD_BITNESS_64
    }
    
    private InstallData installData;
    private String minVersion;
    private String maxVersion;
    private boolean matchBits;
    
    public JDKPathHelper(InstallData installData, String minVersion, String maxVersion)
    {
        this.installData = installData;
        this.minVersion = minVersion;
        this.maxVersion = maxVersion;
        matchBits = installData.getVariables().getBoolean(JDKPathPanel.JDK_PATH_PANEL_MATCH_BITS);
    }
    
    public String getDetectedVersion()
    {
        return detectedVersion;
    }

    public void setDetectedVersion(String detectedVersion)
    {
        this.detectedVersion = detectedVersion;
    }

    private boolean compareVersions(String in, String template, boolean isMin, int assumedPlace,
            int halfRange, String useNotIdentifier)
    {
        StringTokenizer tokenizer = new StringTokenizer(in, " \t\n\r\f\"");
        int i;
        int currentRange = 0;
        String[] interestedEntries = new String[halfRange + halfRange];
        for (i = 0; i < assumedPlace - halfRange; ++i)
        {
            if (tokenizer.hasMoreTokens())
            {
                tokenizer.nextToken(); // Forget this entries.
            }
        }

        for (i = 0; i < halfRange + halfRange; ++i)
        { // Put the interesting Strings into an intermediaer array.
            if (tokenizer.hasMoreTokens())
            {
                interestedEntries[i] = tokenizer.nextToken();
                currentRange++;
            }
        }

        for (i = 0; i < currentRange; ++i)
        {
            if (useNotIdentifier != null && interestedEntries[i].contains(useNotIdentifier))
            {
                continue;
            }
            if (Character.getType(interestedEntries[i].charAt(0)) != Character.DECIMAL_DIGIT_NUMBER)
            {
                continue;
            }
            break;
        }
        if (i == currentRange)
        {
            detectedVersion = "<not found>";
            return (false);
        }
        detectedVersion = interestedEntries[i];
        StringTokenizer currentTokenizer = new StringTokenizer(interestedEntries[i], "._-");
        StringTokenizer neededTokenizer = new StringTokenizer(template, "._-");
        while (neededTokenizer.hasMoreTokens())
        {
            // Current can have no more tokens if needed has more
            // and if a privious token was not accepted as good version.
            // e.g. 1.4.2_02 needed, 1.4.2 current. The false return
            // will be right here. Only if e.g. needed is 1.4.2_00 the
            // return value will be false, but zero should not b e used
            // at the last version part.
            if (!currentTokenizer.hasMoreTokens()) { return (false); }
            String current = currentTokenizer.nextToken();
            String needed = neededTokenizer.nextToken();
            int currentValue;
            int neededValue;
            try
            {
                currentValue = Integer.parseInt(current);
                neededValue = Integer.parseInt(needed);
            }
            catch (NumberFormatException nfe)
            { // A number format exception will be raised if
              // there is a non numeric part in the version,
            // e.g. 1.5.0_beta. The verification runs only into
            // this deep area of version number (fourth sub place)
            // if all other are equal to the given limit. Then
            // it is right to return false because e.g.
            // the minimal needed version will be 1.5.0.2.
                return (false);
            }
            if (currentValue < neededValue)
            {
                if (isMin) { return (false); }
                return (true);
            }
            if (currentValue > neededValue)
            {
                if (isMin) { return (true); }
                return (false);
            }
        }
        return (true);
    }

    /**
     * Returns the path to the needed JDK if found in the registry. If there are more than one JDKs
     * registered, that one with the highest allowd version will be returned. Works only on windows.
     * On Unix an empty string returns.
     *
     * @return the path to the needed JDK if found in the windows registry
     */
    String resolveInRegistry(RegistryDefaultHandler handler)
    {
        String retval = "";
        int oldVal = 0;
        RegistryHandler registryHandler = null;
        badRegEntries = new HashSet<String>();
        try
        {
            // Get the default registry handler.
            registryHandler = handler.getInstance();
            if (registryHandler == null)
            // We are on a os which has no registry or the
            // needed dll was not bound to this installation. In
            // both cases we forget the try to get the JDK path from registry.
            {
                return (retval);
            }
            oldVal = registryHandler.getRoot(); // Only for security...
            registryHandler.setRoot(MSWinConstants.HKEY_LOCAL_MACHINE);
            String[] keys = registryHandler.getSubkeys(JDK_ROOT_KEY);
            if (keys == null || keys.length == 0)
            {
                return (retval);
            }
            Arrays.sort(keys);
            int i = keys.length - 1;
            // We search for the highest allowed version, therefore retrograde
            while (i > 0)
            {
                if (compareVersions(keys[i], maxVersion, false, 4, 4, "__NO_NOT_IDENTIFIER_"))
                { // First allowd version found, now we have to test that the min value
                    // also allows this version.
                    if (compareVersions(keys[i], minVersion, true, 4, 4, "__NO_NOT_IDENTIFIER_"))
                    {
                        String cv = JDK_ROOT_KEY + "\\" + keys[i];
                        String path = registryHandler.getValue(cv, JDK_VALUE_NAME).getStringData();
                        // Use it only if the path is valid.
                        // Set the path for method pathIsValid ...
                        if (!pathIsValid(path))
                        {
                            badRegEntries.add(keys[i]);
                        }
                        else if ("".equals(retval))
                        {
                            retval = path;
                        }
                    }
                }
                i--;
            }
        }
        catch (Exception e)
        { // Will only be happen if registry handler is good, but an
            // exception at performing was thrown. This is an error...
            e.printStackTrace();
        }
        finally
        {
            if (registryHandler != null && oldVal != 0)
            {
                try
                {
                    registryHandler.setRoot(MSWinConstants.HKEY_LOCAL_MACHINE);
                }
                catch (NativeLibException e)
                {
                    e.printStackTrace();
                }
            }
        }
        return (retval);
    }

    VerifyResult verifyVersionEx(String path)
    {
        VerifyResult retval = VerifyResult.OK;
        // No min and max, version always ok.
        if (minVersion == null && maxVersion == null)
        {
            return retval;
        }

        if (!pathIsValid(path))
        {
            return (VerifyResult.BAD_REAL_PATH);
        }
        // No get the version ...
        // We cannot look to the version of this vm because we should
        // test the given JDK VM.
        String[] params;
        if (installData.getPlatform().isA(Platform.Name.WINDOWS))
        {
            params = new String[]{
                    "cmd",
                    "/c",
                    path + File.separator + "bin" + File.separator + "java",
                    "-version"
            };
        }
        else
        {
            params = new String[]{
                    path + File.separator + "bin" + File.separator + "java",
                    "-version"
            };
        }
        String[] output = new String[2];
        FileExecutor fileExecutor = new FileExecutor();
        fileExecutor.executeCommand(params, output);
        // "My" VM writes the version on stderr :-(
        String vs = (output[0].length() > 0) ? output[0] : output[1];
        if (minVersion != null)
        {
            if (!compareVersions(vs, minVersion, true, 4, 4, "__NO_NOT_IDENTIFIER_"))
            {
                retval = VerifyResult.BAD_VERSION;
            }
        }
        if (maxVersion != null)
        {
            if (!compareVersions(vs, maxVersion, false, 4, 4, "__NO_NOT_IDENTIFIER_"))
            {
                retval = VerifyResult.BAD_VERSION;
            }
        }
        if (retval == VerifyResult.OK && badRegEntries != null && badRegEntries.size() > 0)
        {   // Test for bad registry entry.
            if (badRegEntries.contains(getDetectedVersion()))
            {
                retval = VerifyResult.BAD_REG_PATH;
            }
        }
        if (matchBits) {
            String javaVmName = System.getProperty("java.vm.name");
            boolean target_64bit = vs.contains("64-bit") || vs.contains("64-Bit");
            boolean installer_64bit = javaVmName.contains("64-bit") || javaVmName.contains("64-Bit"); 
            if (installer_64bit ^ target_64bit) {
                retval = target_64bit ? VerifyResult.BAD_BITNESS_32 : VerifyResult.BAD_BITNESS_64;
            }
        }
        return (retval);
    }
    
    boolean verifyVersion(String path) {
        return verifyVersionEx(path).equals(VerifyResult.OK);
    }

    /**
     * Returns whether the chosen path is true or not. If existFiles are not null, the existence of
     * it under the chosen path are detected. This method can be also implemented in derived
     * classes to handle special verification of the path.
     *
     * @return true if existFiles are exist or not defined, else false
     */
    static boolean pathIsValid(String strPath)
    {
        for (String existFile : JDKPathPanel.testFiles)
        {
            File path = new File(strPath, existFile).getAbsoluteFile();
            if (!path.exists())
            {
                return false;
            }
        }
        return true;
    }

}
