package com.izforge.izpack.panels.sudo;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import com.izforge.izpack.api.data.InstallData;
import com.izforge.izpack.api.data.binding.OsModel;
import com.izforge.izpack.api.handler.AbstractUIHandler;
import com.izforge.izpack.api.substitutor.VariableSubstitutor;
import com.izforge.izpack.data.ExecutableFile;
import com.izforge.izpack.data.ParsableFile;
import com.izforge.izpack.installer.unpacker.ScriptParser;
import com.izforge.izpack.util.FileExecutor;
import com.izforge.izpack.util.Platform;
import com.izforge.izpack.util.PlatformModelMatcher;


public class SudoPanelHelper
{
    private InstallData installData;
    private VariableSubstitutor replacer;
    private PlatformModelMatcher matcher;
    private AbstractUIHandler handler;

    SudoPanelHelper(InstallData installData, VariableSubstitutor replacer, PlatformModelMatcher matcher, AbstractUIHandler handler)
    {
        this.installData = installData;
        this.replacer = replacer;
        this.matcher = matcher;
        this.handler = handler;
    }

    boolean isSudoNeeded() {
        if (!installData.getPlatform().isA(Platform.Name.UNIX)) {
            return false;
        }
        if (System.getProperty("user.name").equals("root")) {
            return false;
        }
        return true;
    }
    
    boolean doSudoCmd(String pass)
    {
        boolean isValid = false;
        File file = null;
        try
        {
            // write file in /tmp
            file = new File("/tmp/cmd_sudo.sh");// ""c:/temp/run.bat""
            FileOutputStream fos = new FileOutputStream(file);
            fos.write("echo $password | sudo -S ls\nexit $?".getBytes()); // "echo
            // $password
            // >
            // pipo.txt"
            fos.close();

            // execute
            Properties vars = new Properties();
            vars.put("password", pass);

            List<OsModel> oses = new ArrayList<OsModel>();
            oses.add(new OsModel("unix", null, null, null, null));

            ParsableFile parsableFile = new ParsableFile(file.getAbsolutePath(), null, null, oses);
            ScriptParser scriptParser = new ScriptParser(replacer, matcher);
            scriptParser.parse(parsableFile);

            ArrayList<ExecutableFile> executableFiles = new ArrayList<ExecutableFile>();
            ExecutableFile executableFile = new ExecutableFile(file.getAbsolutePath(),
                                                               ExecutableFile.POSTINSTALL, ExecutableFile.ABORT, oses,
                                                               false);
            executableFiles.add(executableFile);
            FileExecutor fileExecutor = new FileExecutor(executableFiles);
            int retval = fileExecutor.executeFiles(ExecutableFile.POSTINSTALL, matcher, handler);
            if (retval == 0)
            {
                this.installData.setVariable("password", pass);
                isValid = true;
            }
            // else is already showing dialog
            // {
            // JOptionPane.showMessageDialog(this, "Cannot execute 'sudo' cmd,
            // check your password", "Error", JOptionPane.ERROR_MESSAGE);
            // }
        }
        catch (Exception e)
        {
            // JOptionPane.showMessageDialog(this, "Cannot execute 'sudo' cmd,
            // check your password", "Error", JOptionPane.ERROR_MESSAGE);
            e.printStackTrace();
            isValid = false;
        }
        try
        {
            if (file != null && file.exists())
            {
                file.delete();// you don't
            }
            // want the file
            // with password
            // tobe arround,
            // in case of
            // error
        }
        catch (Exception e)
        {
            // ignore
        }
        return isValid;
    }
}
