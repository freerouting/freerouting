/*
 *  Copyright (C) 2014  Alfons Wirtz  
 *   website www.freerouting.net
 *
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License at <http://www.gnu.org/licenses/> 
 *   for more details.
 *
 * WebStart.java
 *
 * Created on 4. Dezember 2006, 07:01
 *
 */

package gui;

/**
 * Function used for Java Websrart.
 * Some put to a separate class to avoid runtime undefined in offline applications.
 * @author Alfons Wirtz
 */
public class WebStart
{
   /*
    * Separate function to avoid runtime undefines in an offline application.
    */
    public static java.net.URL get_code_base()
    {
        try
        {
            javax.jnlp.BasicService basic_service =
                    (javax.jnlp.BasicService)javax.jnlp.ServiceManager.lookup("javax.jnlp.BasicService");
            return basic_service.getCodeBase();
        }
        catch(Exception e)
        {
            return null;
        }
    }
    
    
    public static javax.jnlp.FileContents save_dialog(String p_parent, String[] p_file_extensions,
            java.io.InputStream p_input_stream,  String p_name)
    {
        try
        {
            javax.jnlp.FileSaveService file_save_service =
                    (javax.jnlp.FileSaveService)javax.jnlp.ServiceManager.lookup("javax.jnlp.FileSaveService");
            javax.jnlp.FileContents curr_file_contents =
                    file_save_service.saveFileDialog(p_parent, p_file_extensions, p_input_stream, p_name);
            return curr_file_contents;
        }
        catch (Exception e)
        {
            return null;
        }
    }
    
    /**
     * Looks up a file with the input name in the Cookie file system of Java Web Start.
     * Returns an input stream from that file or null, if no such file was found.
     */
    public static java.io.InputStream get_file_input_stream(String p_file_name)
    {
        java.net.URL code_base = WebStart.get_code_base();
        if (code_base != null)
        {
            try
            {
                javax.jnlp.PersistenceService persistence_service =
                        (javax.jnlp.PersistenceService)javax.jnlp.ServiceManager.lookup("javax.jnlp.PersistenceService");
                String [] muffins =  persistence_service.getNames(code_base);
                for (int i = 0; i < muffins.length; ++i)
                {
                    if (muffins[i].equals(p_file_name))
                    {
                        java.net.URL defaults_file_url = new java.net.URL(code_base.toString() + muffins[i]);
                        javax.jnlp.FileContents file_contents = persistence_service.get(defaults_file_url);
                        return file_contents.getInputStream();
                    }
                }
            }
            catch(Exception e)
            {
                
            }
        }
        return null;
    }
    
    /**
     * Looks up a file with the input name in the Cookie file system of Java Web Start.
     * This file will be overwritten.
     * Creates a new file, if no such file exists yet.
     */
    public static java.io.OutputStream get_file_output_stream(String p_file_name)
    {
        java.io.OutputStream output_stream = null;
        String [] muffins = null;
        javax.jnlp.PersistenceService persistence_service = null;
        java.net.URL code_base = get_code_base();
        if (code_base != null)
        {
            try
            {
                persistence_service =
                        (javax.jnlp.PersistenceService)javax.jnlp.ServiceManager.lookup("javax.jnlp.PersistenceService");
                muffins =  persistence_service.getNames(code_base);
            }
            catch(Exception e)
            {
                muffins = null;
            }
        }
        try
        {
            boolean file_exists = false;
            java.net.URL file_url = null;
            if (muffins != null)
            {
                for (int i = 0; i < muffins.length; ++i)
                {
                    if (muffins[i].equals(p_file_name))
                    {
                        file_url = new java.net.URL(code_base.toString() + muffins[i]);
                        file_exists = true;
                    }
                }
            }
            if (!file_exists)
            {
                file_url = new java.net.URL(code_base.toString() + p_file_name);
                long act_size = persistence_service.create(file_url, MAX_FILE_SIZE);
                if (act_size < MAX_FILE_SIZE)
                {
                    return null;
                }
            }
            javax.jnlp.FileContents file_contents = persistence_service.get(file_url);
            output_stream = file_contents.getOutputStream(true);
            
        }
        catch(Exception e)
        {
            return null;
        }
        return output_stream;
    }
    
    /*
     * Deletes all files ending with p_file_ending from the cookie file system.
     * Return false, if no file to delete was found
     * If p_confirm_message != null, the user is asked to confirm the delete action.
     */
    public static boolean delete_files(String p_file_ending, String p_confirm_messsage)
    {
        boolean file_deleted = false;
        try
        {
            java.net.URL code_base = WebStart.get_code_base();
            if (code_base == null)
            {
                return false;
            }
            javax.jnlp.PersistenceService persistence_service =
                    (javax.jnlp.PersistenceService)javax.jnlp.ServiceManager.lookup("javax.jnlp.PersistenceService");
            String [] muffins =  persistence_service.getNames(code_base);
            java.net.URL file_url = null;
            if (muffins != null)
            {
                for (int i = 0; i < muffins.length; ++i)
                {
                    if (muffins[i].endsWith(p_file_ending))
                    {
                        file_url = new java.net.URL(code_base.toString() + muffins[i]);
                        if (p_confirm_messsage == null  || WindowMessage.confirm(p_confirm_messsage))
                        {
                            persistence_service.delete(file_url);
                            file_deleted = true;
                        }
                    }
                }
            }
        }
        catch(Exception e)
        {
            file_deleted = false;
        }
        return file_deleted;
    }
    
    private static final long MAX_FILE_SIZE = 100000;
}
