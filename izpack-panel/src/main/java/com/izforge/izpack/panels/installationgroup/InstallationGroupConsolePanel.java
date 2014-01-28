/*
 * IzPack - Copyright 2001-2012 Julien Ponge, All Rights Reserved.
 *
 * http://izpack.org/
 * http://izpack.codehaus.org/
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

package com.izforge.izpack.panels.installationgroup;

import com.izforge.izpack.api.data.AutomatedInstallData;
import com.izforge.izpack.api.data.InstallData;
import com.izforge.izpack.api.data.Pack;
import com.izforge.izpack.api.handler.Prompt;
import com.izforge.izpack.installer.console.AbstractConsolePanel;
import com.izforge.izpack.installer.console.ConsolePanel;
import com.izforge.izpack.installer.panel.PanelView;
import com.izforge.izpack.util.Console;
import com.izforge.izpack.util.PlatformModelMatcher;

import java.io.PrintWriter;
import java.text.MessageFormat;
import java.util.*;
import java.util.logging.Logger;

/**
 * Console implementation for the InstallationGroupPanel.
 *
 * @author radai.rosenblatt@gmail.com
 */
public class InstallationGroupConsolePanel extends AbstractConsolePanel implements ConsolePanel
{
    private static final transient Logger logger = Logger.getLogger(InstallationGroupPanel.class.getName());

    private static final String NOT_SELECTED = "Not Selected";
    private static final String DONE = "Done!";
    private static final String SPACE = " ";

    private final Prompt prompt;
    private final AutomatedInstallData automatedInstallData;
    private final PlatformModelMatcher matcher;
    private String variableName;

    public InstallationGroupConsolePanel(PanelView<Console> panel, Prompt prompt, AutomatedInstallData automatedInstallData, PlatformModelMatcher matcher)
    {
        super(panel);
        this.prompt = prompt;
        this.automatedInstallData = automatedInstallData;
        this.matcher = matcher;
        setVariableName(InstallationGroupPanel.INSTALL_GROUP);
    }

    @Override
    public boolean generateProperties(InstallData installData,
    		PrintWriter printWriter) {
        Map<String, GroupData> installGroups = InstallationGroups.getInstallGroups(automatedInstallData);
        StringBuilder output = new StringBuilder();
        for (String s : installGroups.keySet()) {
            if (output.length() > 0) {
                output.append(", ");
            }
            output.append(s);
        }
        printWriter.println("# Choose one of: " + output.toString());
    	printWriter.println(getVariableName() + "=");
    	return true;
    }
    
    @Override
    public boolean run(InstallData installData, Properties properties)
    {
        Map<String, GroupData> installGroups = InstallationGroups.getInstallGroups(automatedInstallData);
        String selectedGroup = properties.getProperty(getVariableName());
        if (selectedGroup == null || selectedGroup.trim().isEmpty()) {
        	System.err.println("Missing mandatory installation group");
        	return false;
        }
        GroupData selected = installGroups.get(selectedGroup);
        if (selected != null) {
            this.automatedInstallData.setVariable(getVariableName(), selected.name);
            InstallationGroupPanel.removeUnusedPacks(selected, automatedInstallData);
            return true;
        } else {
        	System.err.println("Installation group '" + selectedGroup + "' does not exist");
        	return false;
        }
    }

    @Override
    public boolean run(InstallData installData, Console console)
    {
        // Set/restore availablePacks from allPacks; consider OS constraints
        this.automatedInstallData.setAvailablePacks(new ArrayList<Pack>());
        for (Pack pack : this.automatedInstallData.getAllPacks())
        {
            if (matcher.matchesCurrentPlatform(pack.getOsConstraints()))
            {
                this.automatedInstallData.getAvailablePacks().add(pack);
            }
        }

        // If there are no groups, skip this panel
        Map<String, GroupData> installGroups = InstallationGroups.getInstallGroups(automatedInstallData);
        if (installGroups.size() == 0)
        {
            console.prompt("Skip InstallGroup selection", new String[]{"Yes", "No"}, "Yes");
            return false;
        }

        List<GroupData> sortedGroups = new ArrayList<GroupData>(installGroups.values());
        Collections.sort(sortedGroups, InstallationGroups.BY_SORT_KEY);

        GroupData selected = selectGroup(sortedGroups, console);
        while (selected==null) {
            out(Prompt.Type.ERROR, "Must select an option");
            selected = selectGroup(sortedGroups, console);
        }

        this.automatedInstallData.setVariable(getVariableName(), selected.name);
        logger.fine("Added variable " + getVariableName() + "=" + selected.name);

        InstallationGroupPanel.removeUnusedPacks(selected, automatedInstallData);

        out(Prompt.Type.INFORMATION, DONE);
        return promptEndPanel(installData, console);
    }

    private GroupData selectGroup(List<GroupData> options, Console console)
    {
        GroupData selected = null;
        if (options.size() < 10) {
            StringBuilder builder = new StringBuilder();
            for (int i = 1; i < options.size() + 1; i++) {
                GroupData data = options.get(i - 1);
                builder.append(MessageFormat.format("{0} {1}\n", i, data.description));
            }
            int selectedIndex = console.prompt(builder.toString(), 1, options.size(), 0);
            if (selectedIndex > 0) {
                selected = options.get(selectedIndex - 1);
            }
        } else {
            for (GroupData groupData : options) {
                if (selected!=null) {
                    out(Prompt.Type.INFORMATION, groupData.description + SPACE + NOT_SELECTED);
                    continue;
                }
                if (askUser(groupData.description)) {
                    selected = groupData;
                    continue;
                }
            }
        }
        return selected;
    }

    private void out(Prompt.Type type, String message)
    {
        prompt.message(type, message);
    }

    private boolean askUser(String message)
    {
        return Prompt.Option.YES == prompt.confirm(Prompt.Type.QUESTION, message, Prompt.Options.YES_NO);
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
