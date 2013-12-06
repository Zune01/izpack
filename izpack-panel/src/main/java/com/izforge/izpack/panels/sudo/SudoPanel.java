/*
 * IzPack - Copyright 2001-2008 Julien Ponge, All Rights Reserved.
 *
 * http://izpack.org/
 * http://izpack.codehaus.org/
 *
 * Copyright 2003 Jan Blok
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

package com.izforge.izpack.panels.sudo;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;

import com.izforge.izpack.api.data.Panel;
import com.izforge.izpack.api.data.binding.OsModel;
import com.izforge.izpack.api.resource.Resources;
import com.izforge.izpack.api.substitutor.VariableSubstitutor;
import com.izforge.izpack.data.ExecutableFile;
import com.izforge.izpack.data.ParsableFile;
import com.izforge.izpack.gui.LabelFactory;
import com.izforge.izpack.installer.data.GUIInstallData;
import com.izforge.izpack.installer.gui.InstallerFrame;
import com.izforge.izpack.installer.gui.IzPanel;
import com.izforge.izpack.installer.unpacker.ScriptParser;
import com.izforge.izpack.util.FileExecutor;
import com.izforge.izpack.util.Platform;
import com.izforge.izpack.util.PlatformModelMatcher;
import com.izforge.izpack.util.PrivilegedRunner;

/**
 * The packs selection panel class.
 *
 * @author Jan Blok
 * @since November 27, 2003
 */
public class SudoPanel extends IzPanel implements ActionListener
{

    /**
     *
     */
    private static final long serialVersionUID = 3689628116465561651L;

    private JTextField passwordField;

    private boolean isValid = false;

    private SudoPanelHelper helper;

    /**
     * The constructor.
     *
     * @param panel       the panel meta-data
     * @param parent      the parent window.
     * @param installData the installation data
     * @param resources   the resources
     * @param replacer    the variable replacer
     * @param matcher     the platform-model matcher
     */
    public SudoPanel(Panel panel, InstallerFrame parent, GUIInstallData installData, Resources resources,
                     VariableSubstitutor replacer, PlatformModelMatcher matcher)
    {
        super(panel, parent, installData, resources);
        this.helper = new SudoPanelHelper(installData, replacer, matcher, this);
        
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));

        add(LabelFactory
                    .create(
                            /* installData.getLangpack().getString("SudoPanel.info") */
                            "For installing administrator privileges are necessary",
                            JLabel.TRAILING));

        add(Box.createRigidArea(new Dimension(0, 5)));

        add(LabelFactory
                    .create(
                            /* installData.getLangpack().getString("SudoPanel.tip") */
                            "Please note that passwords are case-sensitive",
                            parent.getIcons().get("tip"), JLabel.TRAILING));

        add(Box.createRigidArea(new Dimension(0, 5)));

        JPanel spacePanel = new JPanel();
        spacePanel.setAlignmentX(LEFT_ALIGNMENT);
        spacePanel.setAlignmentY(CENTER_ALIGNMENT);
        spacePanel.setBorder(BorderFactory.createEmptyBorder(80, 30, 0, 50));
        spacePanel.setLayout(new BorderLayout(5, 5));
        spacePanel
                .add(
                        LabelFactory
                                .create(
                                        /* installData.getLangpack().getString("SudoPanel.specifyAdminPassword") */
                                        "Please specify your password:"),
                        BorderLayout.NORTH);
        passwordField = new JPasswordField();
        passwordField.addActionListener(this);
        JPanel space2Panel = new JPanel();
        space2Panel.setLayout(new BorderLayout());
        space2Panel.add(passwordField, BorderLayout.NORTH);
        space2Panel.add(Box.createRigidArea(new Dimension(0, 5)), BorderLayout.CENTER);
        spacePanel.add(space2Panel, BorderLayout.CENTER);
        add(spacePanel);
    }

    /**
     * Called when the panel becomes active.
     */
    public void panelActivate()
    {
        if (helper.isSudoNeeded())
        {
            passwordField.requestFocus();
        }
        else 
        {
            parent.skipPanel();
        }
    }

    /**
     * Actions-handling method.
     *
     * @param e The event.
     */
    public void actionPerformed(ActionEvent e)
    {
        doSudoCmd();
    }

    private boolean doSudoCmd()
    {
        return helper.doSudoCmd(passwordField.getText());
    }

    // check if sudo password is correct (so sudo can be used in all other
    // scripts, even without password, lasts for 5 minutes)

    /**
     * Indicates wether the panel has been validated or not.
     *
     * @return Always true.
     */
    public boolean isValidated()
    {
        if (!isValid)
        {
            isValid = doSudoCmd();
        }
        if (!isValid)
        {
            JOptionPane.showInternalMessageDialog(this, "Password", "Password is not valid",
                                                  JOptionPane.ERROR_MESSAGE);
        }
        return isValid;
    }
}
