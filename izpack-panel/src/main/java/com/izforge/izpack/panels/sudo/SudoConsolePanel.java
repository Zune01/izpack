package com.izforge.izpack.panels.sudo;

import java.io.PrintWriter;
import java.util.Properties;

import com.izforge.izpack.api.data.InstallData;
import com.izforge.izpack.api.handler.Prompt;
import com.izforge.izpack.api.substitutor.VariableSubstitutor;
import com.izforge.izpack.installer.console.AbstractConsolePanel;
import com.izforge.izpack.installer.console.ConsolePanel;
import com.izforge.izpack.installer.panel.PanelView;
import com.izforge.izpack.util.Console;
import com.izforge.izpack.util.PlatformModelMatcher;


public class SudoConsolePanel extends AbstractConsolePanel implements ConsolePanel
{
    private static final String SUDO_MESSAGE = "You must execute this installer either as root, or with the help of 'sudo'";
    private Prompt prompt;
    private VariableSubstitutor replacer;
    private PlatformModelMatcher matcher;

    public SudoConsolePanel(PanelView<Console> panel, Prompt prompt, VariableSubstitutor replacer, PlatformModelMatcher matcher)
    {
        super(panel);
        this.prompt = prompt;
        this.replacer = replacer;
        this.matcher = matcher;
    }

    @Override
    public boolean generateProperties(InstallData installData, PrintWriter printWriter)
    {
        printWriter.println("# " + SUDO_MESSAGE);
        return true;
    }
    
    @Override
    public boolean run(InstallData installData, Properties properties)
    {
        SudoPanelHelper helper = new SudoPanelHelper(installData, replacer, matcher, null); 
        if (helper.isSudoNeeded()) {
            System.err.println(SUDO_MESSAGE);
            return false;
        } else {
            return true;
        }
    }

    @Override
    public boolean run(InstallData installData, Console console)
    {
        SudoPanelHelper helper = new SudoPanelHelper(installData, replacer, matcher, null); 
        return false;
    }

}
