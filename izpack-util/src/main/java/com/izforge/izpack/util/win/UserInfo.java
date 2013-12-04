package com.izforge.izpack.util.win;

import com.izforge.izpack.util.Librarian;
import com.izforge.izpack.util.NativeLibraryClient;

public class UserInfo implements NativeLibraryClient 
{

    private Librarian librarian;

    public UserInfo(Librarian librarian) throws Exception
    {
        this.librarian = librarian;
        initialize();
    }

    private void initialize() throws Exception
    {
        try
        {
            librarian.loadLibrary("UserInfo", this);
        }
        catch (UnsatisfiedLinkError exception)
        {
            throw new Exception("Could not locate native library", exception);
        }
    }
    
    public native boolean isUserAnAdmin();

    @Override
    public void freeLibrary(String name)
    {
    }
}
