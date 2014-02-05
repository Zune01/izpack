/*
 * IzPack - Copyright 2001-2008 Julien Ponge, All Rights Reserved.
 *
 * http://izpack.org/
 * http://izpack.codehaus.org/
 *
 * Copyright 2002 Jan Blok
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.izforge.izpack.panels.jdkpath;

import java.io.File;
import java.io.PrintWriter;
import java.util.Properties;

import com.izforge.izpack.api.data.InstallData;
import com.izforge.izpack.api.substitutor.VariableSubstitutor;
import com.izforge.izpack.core.os.RegistryDefaultHandler;
import com.izforge.izpack.installer.console.AbstractConsolePanel;
import com.izforge.izpack.installer.panel.PanelView;
import com.izforge.izpack.util.Console;
import com.izforge.izpack.util.OsVersion;

/**
 * The Target panel console helper class.
 *
 * @author Mounir El Hajj
 */
public class JDKPathConsolePanel extends AbstractConsolePanel
{
    private JDKPathHelper helper;
    private final VariableSubstitutor variableSubstitutor;
    private final RegistryDefaultHandler handler;
    private String variableName;

    /**
     * Constructs a <tt>JDKPathConsolePanelHelper</tt>.
     *
     * @param variableSubstitutor the variable substituter
     * @param handler             the registry handler
     * @param panel               the parent panel/view. May be {@code null}
     */
    public JDKPathConsolePanel(VariableSubstitutor variableSubstitutor, RegistryDefaultHandler handler,
                               PanelView<Console> panel)
    {
        super(panel);
        this.variableSubstitutor = variableSubstitutor;
        this.handler = handler;
        setVariableName(JDKPathPanel.JDK_PATH);
    }

    public boolean generateProperties(InstallData installData, PrintWriter printWriter)
    {
        printWriter.println(getVariableName() + "=");
        return true;
    }

    public boolean run(InstallData installData, Properties properties)
    {
        String strTargetPath = properties.getProperty(getVariableName());
        if (strTargetPath == null || "".equals(strTargetPath.trim()))
        {
            System.err.println("Missing mandatory JDK path!");
            return false;
        }
        else
        {
            try
            {
                strTargetPath = variableSubstitutor.substitute(strTargetPath);
            }
            catch (Exception e)
            {
                // ignore
            }
            // Validate
            String minVersion = installData.getVariable(JDKPathPanel.JDK_PATH_PANEL_MIN_VERSION);
            String maxVersion = installData.getVariable(JDKPathPanel.JDK_PATH_PANEL_MAX_VERSION);
            
            helper = new JDKPathHelper(installData, minVersion, maxVersion);
            if (!JDKPathHelper.pathIsValid(strTargetPath) || !helper.verifyVersion(strTargetPath)) {
            	System.err.println("The specified JDKPath is not a JDK");
            	return false;
            }
            
            installData.setVariable(getVariableName(), strTargetPath);
            return true;
        }
    }

    /**
     * Runs the panel using the specified console.
     *
     * @param installData the installation data
     * @param console     the console
     * @return <tt>true</tt> if the panel ran successfully, otherwise <tt>false</tt>
     */
    @Override
    public boolean run(InstallData installData, Console console)
    {
        String minVersion = installData.getVariable(JDKPathPanel.JDK_PATH_PANEL_MIN_VERSION);
        String maxVersion = installData.getVariable(JDKPathPanel.JDK_PATH_PANEL_MAX_VERSION);

        String strPath;
        String strDefaultPath = installData.getVariable(getVariableName());
        if (strDefaultPath == null)
        {
            if (OsVersion.IS_OSX)
            {
                strDefaultPath = JDKPathPanel.OSX_JDK_HOME;
            }
            else
            {
                // Try the JAVA_HOME as child dir of the jdk path
                strDefaultPath = (new File(installData.getVariable("JAVA_HOME"))).getParent();
            }
        }

        helper = new JDKPathHelper(installData, minVersion, maxVersion);
        if (!JDKPathHelper.pathIsValid(strDefaultPath) || !helper.verifyVersion(strDefaultPath))
        {
            strDefaultPath = helper.resolveInRegistry(handler);
            if (!JDKPathHelper.pathIsValid(strDefaultPath) || !helper.verifyVersion(strDefaultPath))
            {
                strDefaultPath = "";
            }
        }

        boolean bKeepAsking = true;

        while (bKeepAsking)
        {
            strPath = console.prompt("Select JDK path [" + strDefaultPath + "] ", null);
            if (strPath == null)
            {
                // end of stream
                return false;
            }
            strPath = strPath.trim();
            if (strPath.equals(""))
            {
                strPath = strDefaultPath;
            }
            if (!JDKPathHelper.pathIsValid(strPath))
            {
                console.println("Path " + strPath + " is not valid.");
            }
            else if (!helper.verifyVersion(strPath))
            {
                String message = "The chosen JDK has the wrong version (available: " + helper.getDetectedVersion() + " required: "
                        + minVersion + " - " + maxVersion + ").";
                message += "\nContinue anyway? [no]";
                String strIn = console.prompt(message, null);
                if (strIn == null)
                {
                    // end of stream
                    return false;
                }
                strIn = strIn.toLowerCase();
                if (strIn != null && (strIn.equals("y") || strIn.equals("yes")))
                {
                    bKeepAsking = false;
                }
            }
            else
            {
                bKeepAsking = false;
            }
            installData.setVariable(getVariableName(), strPath);
        }

        return promptEndPanel(installData, console);
    }

    public String getVariableName()
    {
        return variableName;
    }

    public void setVariableName(String variableName)
    {
        this.variableName = variableName;
    }
}
