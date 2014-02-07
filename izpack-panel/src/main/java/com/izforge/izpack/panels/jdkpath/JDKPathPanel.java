/*
 * $Id$
 * IzPack - Copyright 2001-2008 Julien Ponge, All Rights Reserved.
 *
 * http://izpack.org/
 * http://izpack.codehaus.org/
 *
 * Copyright 2004 Klaus Bartz
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

import java.awt.Desktop;
import java.io.File;
import java.net.URI;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.JEditorPane;
import javax.swing.JScrollPane;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;

import com.izforge.izpack.api.adaptator.IXMLElement;
import com.izforge.izpack.api.adaptator.impl.XMLElementImpl;
import com.izforge.izpack.api.data.Panel;
import com.izforge.izpack.api.handler.AbstractUIHandler;
import com.izforge.izpack.api.resource.Resources;
import com.izforge.izpack.api.substitutor.VariableSubstitutor;
import com.izforge.izpack.core.os.RegistryDefaultHandler;
import com.izforge.izpack.gui.IzPanelLayout;
import com.izforge.izpack.gui.log.Log;
import com.izforge.izpack.installer.data.GUIInstallData;
import com.izforge.izpack.installer.gui.InstallerFrame;
import com.izforge.izpack.panels.path.PathInputPanel;
import com.izforge.izpack.util.Platform;

/**
 * Panel which asks for the JDK path.
 * 
 * @author Klaus Bartz
 */
public class JDKPathPanel extends PathInputPanel implements HyperlinkListener
{

    static final String JDK_PATH_PANEL_MIN_VERSION = "JDKPathPanel.minVersion";

    static final String JDK_PATH_PANEL_MAX_VERSION = "JDKPathPanel.maxVersion";
    
    static final String JDK_PATH_PANEL_MATCH_BITS = "JDKPathPanel.matchBits";

    static final String JDK_PATH = "JDKPath";

    private static final long serialVersionUID = 3257006553327810104L;

    public static final String[] testFiles = new String[] { "lib" + File.separator + "tools.jar"};

    public static final String OSX_JDK_HOME = "/System/Library/Frameworks/JavaVM.framework/Versions/CurrentJDK/Home/";

    private JDKPathHelper helper;

    private String minVersion = null;

    private String maxVersion = null;
    
    private String variableName;

    private final RegistryDefaultHandler handler;

    private final VariableSubstitutor replacer;

    /**
     * The logger.
     */
    private static final Logger logger = Logger.getLogger(JDKPathPanel.class.getName());

    /**
     * Constructs a <tt>JDKPathPanel</tt>.
     * 
     * @param panel the panel meta-data
     * @param parent the parent window
     * @param installData the installation data
     * @param resources the resources
     * @param handler the registry handler
     * @param replacer the variable replacer
     * @param log the log
     */
    public JDKPathPanel(Panel panel, InstallerFrame parent, GUIInstallData installData,
            Resources resources, RegistryDefaultHandler handler, VariableSubstitutor replacer,
            Log log)
    {
        super(panel, parent, installData, resources, log);
        this.handler = handler;
        this.replacer = replacer;
        setMustExist(true);
        if (!installData.getPlatform().isA(Platform.Name.MAC_OSX))
        {
            setExistFiles(JDKPathPanel.testFiles);
        }
        setMinVersion(installData.getVariable(JDK_PATH_PANEL_MIN_VERSION));
        setMaxVersion(installData.getVariable(JDK_PATH_PANEL_MAX_VERSION));
        setVariableName(JDK_PATH);
        reportErrorInUI = false;
        helper = new JDKPathHelper(installData, getMinVersion(), getMaxVersion());
    }

    @Override
    public void hyperlinkUpdate(HyperlinkEvent e)
    {
        try
        {
            if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED)
            {
                String urls = e.getURL().toExternalForm();
                if (Desktop.isDesktopSupported())
                {
                    Desktop desktop = Desktop.getDesktop();
                    desktop.browse(new URI(urls));
                }
            }
        }
        catch (Exception err)
        {
            logger.log(Level.WARNING, err.getMessage());
        }
    }

    /**
     * Indicates wether the panel has been validated or not.
     * 
     * @return Wether the panel has been validated or not.
     */
    public boolean isValidated()
    {
        boolean retval = false;
        if (super.isValidated())
        {
            switch (helper.verifyVersionEx(getPath()))
            {
            case OK:
                this.installData.setVariable(getVariableName(), pathSelectionPanel.getPath());
                retval = true;
                break;
            case BAD_REG_PATH:
                if (askQuestion(getString("installer.warning"),
                        getString("JDKPathPanel.nonValidPathInReg"),
                        AbstractUIHandler.CHOICES_YES_NO, AbstractUIHandler.ANSWER_NO) == AbstractUIHandler.ANSWER_YES)
                {
                    this.installData.setVariable(getVariableName(), pathSelectionPanel.getPath());
                    retval = true;
                }
                break;
            case BAD_REAL_PATH:
                break;
            case BAD_VERSION:
                String min = getMinVersion();
                String max = getMaxVersion();
                StringBuilder message = new StringBuilder();
                message.append(getString("JDKPathPanel.badVersion1"))
                        .append(helper.getDetectedVersion())
                        .append(getString("JDKPathPanel.badVersion2"));
                if (min != null && max != null)
                {
                    message.append(min).append(" - ").append(max);
                }
                else if (min != null)
                {
                    message.append(" >= ").append(min);
                }
                else if (max != null)
                {
                    message.append(" <= ").append(max);
                }

                message.append(getString("JDKPathPanel.badVersion3"));
                if (askQuestion(getString("installer.warning"), message.toString(),
                        AbstractUIHandler.CHOICES_YES_NO, AbstractUIHandler.ANSWER_NO) == AbstractUIHandler.ANSWER_YES)
                {
                    this.installData.setVariable(getVariableName(), pathSelectionPanel.getPath());
                    retval = true;
                }
                break;
            case BAD_BITNESS_32:
                emitError("Error", "The installer is running with a 32-bit JVM, but the selected JDK is 64-bit. You must choose a 32-bit JDK, or re-lauch the installer.");
                break;
            case BAD_BITNESS_64:
                emitError("Error", "The installer is running with a 64-bit JVM, but the selected JDK is 32-bit. You must choose a 64-bit JDK, or re-lauch the installer.");
                break;
            default:
                throw new RuntimeException(
                        "Internal error: unknown result of version verification.");

            }
        } else {
            emitError("Not a JDK", "The selected path does not contain a JDK.");
        }
        return (retval);
    }

    /**
     * Called when the panel becomes active.
     */
    public void panelActivate()
    {
        // Resolve the default for chosenPath
        super.panelActivate();
        String chosenPath = "";

        String msg = getString("JDKPathPanel.jdkDownload");
        if (msg != null && !msg.isEmpty())
        {
            add(IzPanelLayout.createParagraphGap());
            JEditorPane textArea = new JEditorPane("text/html; charset=utf-8", replacer.substitute(
                    msg, null));
            textArea.setCaretPosition(0);
            textArea.setEditable(false);
            textArea.addHyperlinkListener(this);
            textArea.setBackground(getBackground());

            JScrollPane scroller = new JScrollPane(textArea);
            scroller.setAlignmentX(LEFT_ALIGNMENT);
            add(scroller, NEXT_LINE);
        }

        // The variable will be exist if we enter this panel
        // second time. We would maintain the previos
        // selected path.
        if (installData.getVariable(getVariableName()) != null)
        {
            chosenPath = installData.getVariable(getVariableName());
        }
        else
        {
            if (installData.getPlatform().isA(Platform.Name.MAC_OSX))
            {
                chosenPath = OSX_JDK_HOME;
            }
            else
            {
                String javaHome = installData.getVariable("JAVA_HOME");
                if (javaHome != null)
                {
                    chosenPath = new File(javaHome).getAbsolutePath();
                }
            }
        }
        // Set the path for method pathIsValid ...
        pathSelectionPanel.setPath(chosenPath);

        if (!JDKPathHelper.pathIsValid(chosenPath) || !helper.verifyVersion(chosenPath))
        {
            chosenPath = helper.resolveInRegistry(handler);
            if (!JDKPathHelper.pathIsValid(chosenPath) || !helper.verifyVersion(chosenPath))
            {
                chosenPath = "";
            }
        }
        // Set the default to the path selection panel.
        pathSelectionPanel.setPath(chosenPath);
        String skipIfValid = this.installData.getVariable("JDKPathPanel.skipIfValid");
        // Should we skip this panel?
        if (chosenPath.length() > 0 && skipIfValid != null && "yes".equalsIgnoreCase(skipIfValid))
        {
            this.installData.setVariable(getVariableName(), chosenPath);
            parent.skipPanel();
        }

    }

    /**
     * Returns the current used maximum version.
     * 
     * @return the current used maximum version
     */
    public String getMaxVersion()
    {
        return maxVersion;
    }

    /**
     * Returns the current used minimum version.
     * 
     * @return the current used minimum version
     */
    public String getMinVersion()
    {
        return minVersion;
    }

    /**
     * Sets the given value as maximum for version control.
     * 
     * @param maxVersion version string to be used as maximum
     */
    protected void setMaxVersion(String maxVersion)
    {
        if (maxVersion != null && maxVersion.length() > 0)
        {
            this.maxVersion = maxVersion;
        }
        else
        {
            this.maxVersion = "99.0.0";
        }
    }

    /**
     * Sets the given value as minimum for version control.
     * 
     * @param minVersion version string to be used as minimum
     */
    protected void setMinVersion(String minVersion)
    {
        if (minVersion != null && minVersion.length() > 0)
        {
            this.minVersion = minVersion;
        }
        else
        {
            this.minVersion = "1.0.0";
        }
    }

    /**
     * Returns the name of the variable which should be used for the path.
     * 
     * @return the name of the variable which should be used for the path
     */
    public String getVariableName()
    {
        return variableName;
    }

    /**
     * Sets the name for the variable which should be set with the path.
     * 
     * @param variableName variable name to be used
     */
    public void setVariableName(String variableName)
    {
        this.variableName = variableName;
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.izforge.izpack.installer.IzPanel#getSummaryBody()
     */

    public String getSummaryBody()
    {
        return (this.installData.getVariable(getVariableName()));
    }

    @Override
    public void makeXMLData(IXMLElement panelRoot)
    {
        IXMLElement ipath = new XMLElementImpl("jdkPath", panelRoot);
        ipath.setContent(pathSelectionPanel.getPath());
        panelRoot.addChild(ipath);

        IXMLElement varname = new XMLElementImpl("jdkVarName", panelRoot);
        varname.setContent(variableName);
        panelRoot.addChild(varname);
    }
}
