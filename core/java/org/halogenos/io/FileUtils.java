/*
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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Utility class for managing files.
 * Adds functionality missing in android.os.FileUtils as well
 *
 * @hide
 **/
public class FileUtils {

    /**
     * Write a string to a file. (Does not append)
     *
     * @param string String to write
     * @param file File to write to
     * @return True on success, false on failure
     *
     * @hide
     **/
    public static boolean writeString(String string, File file) {
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
     * @return The content of the file or null on failure
     *
     * @hide
     **/
    public static String readString(File file) {
        try {
            return android.os.FileUtils.readTextFile(file, 0, null);
        } catch(IOException e) {
            return null;
        }
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