/*
 * Copyright (C) 2016 The CyanogenMod Project
 * Copyright (C) 2017 The halogenOS Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.halogenos.io;

import android.util.Log;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;


/**
 * Utility class for managing files.
 * Adds functionality missing in android.os.FileUtils as well
 *
 * @hide
 **/
public class FileUtils {

    private static final String TAG = "FileUtils";

    private FileUtils() {
    // This class is not supposed to be instantiated
    }

    /**
     * Write a string to a file. (Does not append)
     *
     * @param string String to write
     * @param file File to write to
     * @return True on success, false on failure
     *
     * @hide
     **/
    public static boolean writeString(File file, String string) {
        FileOutputStream outputStream = null;
        try {
            outputStream = new FileOutputStream(file, false /* no append */);
            outputStream.write(string.getBytes(StandardCharsets.UTF_8));
            outputStream.flush();
            return true;
        } catch (IOException e) {
            return false;
        } finally {
            if (outputStream != null) {
                try {
                    outputStream.close();
                } catch(IOException e) {
                    // Is not closing a failure?
                    return false;
                }
            }
        }
    }

    /**
     * Read a file to a string.
     *
     * @param file File to read
     * @param oneLine boolean determining if the output should only be first line
     * @return The content of the file or null on failure
     *
     * @hide
     **/
    public static String readString(File file, boolean oneLine) {
        String returnValue = null;
        BufferedReader reader = null;
        try {
            if (oneLine){
                reader = new BufferedReader(new FileReader(file), 512);
                returnValue = reader.readLine();
            } else {
                returnValue = android.os.FileUtils.readTextFile(file, 0, null);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (reader != null) reader.close();
            } catch (IOException e) {
                // Ignored, not much we can do anyway
            }
        }
        return returnValue;
    }

    public static String readString(String filePath) {
        return readString(new File(filePath), false);
    }

    public static String readString(File file) {
        return readString(file, false);
    }

    public static boolean writeString(String filePath, String value) {
        return writeString(new File(filePath), value);
    }


    /**
     * Checks whether the given file is readable
     *
     * @param fileName File to test for readability
     * @return true if readable, false if not
     *
     * @hide
     **/
    public static boolean isFileReadable(String fileName) {
        final File file = new File(fileName);
        return file.exists() && file.canRead();
    }

    /**
     * Checks whether the given file is writable
     *
     * @param fileName File to test for write access
     * @return true if writable, false if not
     *
     * @hide
     **/
    public static boolean isFileWritable(String fileName) {
        final File file = new File(fileName);
        return file.exists() && file.canWrite();
    }

    /**
     * Check if a file exists
     *
     * @param path Filename
     * @return True if exists, false if does not exist
     *
     * @hide
     **/
    public static boolean fileExists(String path) {
        return new File(path).exists();
    }

    /**
     * Get file path
     *
     * @param path First part
     * @param path Second part
     *
     * @hide
     **/
    public static String getFilePath(String path1, String path2) {
        return new File(path1, path2).getAbsolutePath();
    }
}