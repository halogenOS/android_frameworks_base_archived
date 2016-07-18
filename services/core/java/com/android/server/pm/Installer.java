/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.server.pm;

import android.annotation.Nullable;
import android.content.Context;
import android.content.pm.PackageStats;
import android.os.Build;
import android.text.TextUtils;
import android.util.Slog;

import dalvik.system.VMRuntime;

import com.android.internal.os.InstallerConnection;
import com.android.server.SystemService;

public final class Installer extends SystemService {
    private static final String TAG = "Installer";

    private final InstallerConnection mInstaller;

    public Installer(Context context) {
        super(context);
        mInstaller = new InstallerConnection();
    }

    @Override
    public void onStart() {
        Slog.i(TAG, "Waiting for installd to be ready.");
        mInstaller.waitForConnection();
    }

    private static String escapeNull(String arg) {
        if (TextUtils.isEmpty(arg))
            return "!";
        else {
            if (arg.indexOf('\0') != -1 || arg.indexOf(' ') != -1)
                throw new IllegalArgumentException(arg);
            
            return arg;
        }
    }

    @Deprecated
    public int install(String name, int uid, int gid, String seinfo) {
        return install(null, name, uid, gid, seinfo);
    }

    public int install(String uuid, String name, int uid, int gid, String seinfo) {
        StringBuilder builder = new StringBuilder();
        builder
                .append("install")          .append(' ')
                .append(escapeNull(uuid))   .append(' ')
                .append(name)               .append(' ')
                .append(uid)                .append(' ')
                .append(gid)                .append(' ')
                .append(seinfo != null ? seinfo : "!")
            ;
        
        return mInstaller.execute(builder.toString());
    }

    public int dexopt(String apkPath, int uid, boolean isPublic,
            String instructionSet, int dexoptNeeded) {
        return dexopt(apkPath, uid, isPublic, instructionSet, dexoptNeeded, true);
    }

    public int dexopt(String apkPath, int uid, boolean isPublic,
            String instructionSet, int dexoptNeeded, boolean bootComplete) {
        if (!isValidInstructionSet(instructionSet)) {
            Slog.e(TAG, "Invalid instruction set: " + instructionSet);
            return -1;
        }

        return mInstaller.dexopt(apkPath, uid, isPublic, instructionSet, dexoptNeeded,
                bootComplete);
    }

    public int dexopt(String apkPath, int uid, boolean isPublic, String pkgName,
            String instructionSet, int dexoptNeeded, boolean vmSafeMode,
            boolean debuggable, @Nullable String outputPath) {
        return dexopt(apkPath, uid, isPublic, pkgName, instructionSet, dexoptNeeded, vmSafeMode,
                debuggable, outputPath, true);
    }

    public int dexopt(String apkPath, int uid, boolean isPublic, String pkgName,
            String instructionSet, int dexoptNeeded, boolean vmSafeMode,
            boolean debuggable, @Nullable String outputPath, boolean bootComplete) {
        if (!isValidInstructionSet(instructionSet)) {
            Slog.e(TAG, "Invalid instruction set: " + instructionSet);
            return -1;
        }
        
        return mInstaller.dexopt(apkPath, uid, isPublic, pkgName,
                instructionSet, dexoptNeeded, vmSafeMode,
                debuggable, outputPath, bootComplete);
    }

    public int idmap(String targetApkPath, String overlayApkPath, int uid) {
        StringBuilder builder = new StringBuilder();
        builder
                .append("idmap")        .append(' ')
                .append(targetApkPath)  .append(' ')
                .append(overlayApkPath) .append(' ')
                .append(uid)
            ;
        return mInstaller.execute(builder.toString());
    }

    public int movedex(String srcPath, String dstPath, String instructionSet) {
        if (!isValidInstructionSet(instructionSet)) {
            Slog.e(TAG, "Invalid instruction set: " + instructionSet);
            return -1;
        }

        StringBuilder builder = new StringBuilder();
        builder
                .append("movedex")      .append(' ')
                .append(srcPath)        .append(' ')
                .append(dstPath)        .append(' ')
                .append(instructionSet)
            ;
        
        return mInstaller.execute(builder.toString());
    }

    public int rmdex(String codePath, String instructionSet) {
        if (!isValidInstructionSet(instructionSet)) {
            Slog.e(TAG, "Invalid instruction set: " + instructionSet);
            return -1;
        }

        StringBuilder builder = new StringBuilder();
        builder
                .append("rmdex")        .append(' ')
                .append(codePath)       .append(' ')
                .append(instructionSet)
            ;
        
        return mInstaller.execute(builder.toString());
    }

    /**
     * Removes packageDir or its subdirectory
     */
    public int rmPackageDir(String packageDir) {
        StringBuilder builder = new StringBuilder();
        builder
                .append("rmpackagedir") .append(' ')
                .append(packageDir)
            ;
        
        return mInstaller.execute(builder.toString());
    }

    @Deprecated
    public int remove(String name, int userId) {
        return remove(null, name, userId);
    }

    public int remove(String uuid, String name, int userId) {
        StringBuilder builder = new StringBuilder();
        builder
                .append("remove")           .append(' ')
                .append(escapeNull(uuid))   .append(' ')
                .append(name)               .append(' ')
                .append(userId)
            ;
        
        return mInstaller.execute(builder.toString());
    }

    public int rename(String oldname, String newname) {
        StringBuilder builder = new StringBuilder();
        builder
                .append("rename")   .append(' ')
                .append(oldname)    .append(' ')
                .append(newname)
            ;
        
        return mInstaller.execute(builder.toString());
    }

    @Deprecated
    public int fixUid(String name, int uid, int gid) {
        return fixUid(null, name, uid, gid);
    }

    public int fixUid(String uuid, String name, int uid, int gid) {
        StringBuilder builder = new StringBuilder();
        builder
                .append("fixuid")           .append(' ')
                .append(escapeNull(uuid))   .append(' ')
                .append(name)               .append(' ')
                .append(uid)                .append(' ')
                .append(gid)
            ;
        
        return mInstaller.execute(builder.toString());
    }

    @Deprecated
    public int deleteCacheFiles(String name, int userId) {
        return deleteCacheFiles(null, name, userId);
    }

    public int deleteCacheFiles(String uuid, String name, int userId) {
        StringBuilder builder = new StringBuilder();
        builder
                .append("rmcache")          .append(' ')
                .append(escapeNull(uuid))   .append(' ')
                .append(name)               .append(' ')
                .append(userId)
            ;
        
        return mInstaller.execute(builder.toString());
    }

    @Deprecated
    public int deleteCodeCacheFiles(String name, int userId) {
        return deleteCodeCacheFiles(null, name, userId);
    }

    public int deleteCodeCacheFiles(String uuid, String name, int userId) {
        StringBuilder builder = new StringBuilder();
        builder
                .append("rmcodecache")      .append(' ')
                .append(escapeNull(uuid))   .append(' ')
                .append(name)               .append(' ')
                .append(userId)
            ;
        
        return mInstaller.execute(builder.toString());
    }

    @Deprecated
    public int createUserData(String name, int uid, int userId, String seinfo) {
        return createUserData(null, name, uid, userId, seinfo);
    }

    public int createUserData(String uuid, String name, int uid, int userId, String seinfo) {
        StringBuilder builder = new StringBuilder();
        builder
                .append("mkuserdata")       .append(' ')
                .append(escapeNull(uuid))   .append(' ')
                .append(name)               .append(' ')
                .append(uid)                .append(' ')
                .append(userId)             .append(' ')
                .append(seinfo != null ? seinfo : "!")
            ;
        
        return mInstaller.execute(builder.toString());
    }

    public int createUserConfig(int userId) {
        StringBuilder builder = new StringBuilder();
        builder
                .append("mkuserconfig") .append(' ')
                .append(userId)
            ;
        
        return mInstaller.execute(builder.toString());
    }

    @Deprecated
    public int removeUserDataDirs(int userId) {
        return removeUserDataDirs(null, userId);
    }

    public int removeUserDataDirs(String uuid, int userId) {
        StringBuilder builder = new StringBuilder();
        builder
                .append("rmuser")           .append(' ')
                .append(escapeNull(uuid))   .append(' ')
                .append(userId)
            ;
        
        return mInstaller.execute(builder.toString());
    }

    public int copyCompleteApp(String fromUuid, String toUuid, String packageName,
            String dataAppName, int appId, String seinfo) {
        StringBuilder builder = new StringBuilder();
        builder
                .append("cpcompleteapp")        .append(' ')
                .append(escapeNull(fromUuid))   .append(' ')
                .append(escapeNull(toUuid))     .append(' ')
                .append(packageName)            .append(' ')
                .append(dataAppName)            .append(' ')
                .append(appId)                  .append(' ')
                .append(seinfo)
            ;
        return mInstaller.execute(builder.toString());
    }

    @Deprecated
    public int clearUserData(String name, int userId) {
        return clearUserData(null, name, userId);
    }

    public int clearUserData(String uuid, String name, int userId) {
        StringBuilder builder = new StringBuilder();
        builder
                .append("rmuserdata")       .append(' ')
                .append(escapeNull(uuid))   .append(' ')
                .append(name)               .append(' ')
                .append(userId)
            ;
        
        return mInstaller.execute(builder.toString());
    }

    public int markBootComplete(String instructionSet) {
        if (!isValidInstructionSet(instructionSet)) {
            Slog.e(TAG, "Invalid instruction set: " + instructionSet);
            return -1;
        }

        StringBuilder builder = new StringBuilder();
        builder
                .append("markbootcomplete") .append(' ')
                .append(instructionSet)
            ;
        
        return mInstaller.execute(builder.toString());
    }

    @Deprecated
    public int freeCache(long freeStorageSize) {
        return freeCache(null, freeStorageSize);
    }

    public int freeCache(String uuid, long freeStorageSize) {
        StringBuilder builder = new StringBuilder();
        builder
                .append("freecache")        .append(' ')
                .append(escapeNull(uuid))   .append(' ')
                .append(String.valueOf(freeStorageSize))
            ;
        
        return mInstaller.execute(builder.toString());
    }

    @Deprecated
    public int getSizeInfo(String pkgName, int persona, String apkPath, String libDirPath,
            String fwdLockApkPath, String asecPath, String[] instructionSets, PackageStats pStats) {
        return getSizeInfo(null, pkgName, persona, apkPath, libDirPath, fwdLockApkPath, asecPath,
                instructionSets, pStats);
    }

    public int getSizeInfo(String uuid, String pkgName, int persona, String apkPath,
            String libDirPath, String fwdLockApkPath, String asecPath, String[] instructionSets,
            PackageStats pStats) {
        for (String instructionSet : instructionSets) {
            if (!isValidInstructionSet(instructionSet)) {
                Slog.e(TAG, "Invalid instruction set: " + instructionSet);
                return -1;
            }
        }

        StringBuilder builder = new StringBuilder();
        builder
                .append("getsize")          .append(' ')
                .append(escapeNull(uuid))   .append(' ')
                .append(pkgName)            .append(' ')
                .append(persona)            .append(' ')
                .append(apkPath)            .append(' ')
                // TODO: Extend getSizeInfo to look at the full subdirectory tree,
                // not just the first level.
                .append(libDirPath != null ? libDirPath : "!")         .append(' ')
                .append(fwdLockApkPath != null ? fwdLockApkPath : "!") .append(' ')
                .append(asecPath != null ? asecPath : "!")             .append(' ')
                // TODO: Extend getSizeInfo to look at *all* instrution sets, not
                // just the primary.
                .append(instructionSets[0])
            ;

        String s = mInstaller.transact(builder.toString());
        String res[] = s.split(" ");

        if ((res == null) || (res.length != 5))
            return -1;
        
        try {
            pStats.codeSize         = Long.parseLong(res[1]);
            pStats.dataSize         = Long.parseLong(res[2]);
            pStats.cacheSize        = Long.parseLong(res[3]);
            pStats.externalCodeSize = Long.parseLong(res[4]);
            return Integer.parseInt(res[0]);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    public int moveFiles() {
        return mInstaller.execute("movefiles");
    }

    @Deprecated
    public int linkNativeLibraryDirectory(String dataPath, String nativeLibPath32, int userId) {
        return linkNativeLibraryDirectory(null, dataPath, nativeLibPath32, userId);
    }

    /**
     * Links the 32 bit native library directory in an application's data directory to the
     * real location for backward compatibility. Note that no such symlink is created for
     * 64 bit shared libraries.
     *
     * @return -1 on error
     */
    public int linkNativeLibraryDirectory(String uuid, String dataPath, String nativeLibPath32,
            int userId) {
        if (dataPath == null) {
            Slog.e(TAG, "linkNativeLibraryDirectory dataPath is null");
            return -1;
        } else if (nativeLibPath32 == null) {
            Slog.e(TAG, "linkNativeLibraryDirectory nativeLibPath is null");
            return -1;
        }

        StringBuilder builder = new StringBuilder();
        builder
                .append("linklib")          .append(' ')
                .append(escapeNull(uuid))   .append(' ')
                .append(dataPath)           .append(' ')
                .append(nativeLibPath32)    .append(' ')
                .append(userId)
            ;

        return mInstaller.execute(builder.toString());
    }

    @Deprecated
    public boolean restoreconData(String pkgName, String seinfo, int uid) {
        return restoreconData(null, pkgName, seinfo, uid);
    }

    public boolean restoreconData(String uuid, String pkgName, String seinfo, int uid) {
        StringBuilder builder = new StringBuilder();
        builder
                .append("restorecondata")               .append(' ')
                .append(escapeNull(uuid))               .append(' ')
                .append(pkgName)                        .append(' ')
                .append(seinfo != null ? seinfo : "!")  .append(' ')
                .append(uid)
            ;
        
        return (mInstaller.execute(builder.toString()) == 0);
    }

    public int createOatDir(String oatDir, String dexInstructionSet) {
        StringBuilder builder = new StringBuilder();
        builder
                .append("createoatdir") .append(' ')
                .append(oatDir)         .append(' ')
                .append(dexInstructionSet)
            ;
        
        return mInstaller.execute(builder.toString());
    }


    public int linkFile(String relativePath, String fromBase, String toBase) {
        StringBuilder builder = new StringBuilder();
        builder
                .append("linkfile")     .append(' ')
                .append(relativePath)   .append(' ')
                .append(fromBase)       .append(' ')
                .append(toBase)
            ;
        
        return mInstaller.execute(builder.toString());
    }

    /**
     * Returns true if {@code instructionSet} is a valid instruction set.
     */
    private static boolean isValidInstructionSet(String instructionSet) {
        if (instructionSet == null) 
            return false;

        for (String abi : Build.SUPPORTED_ABIS)
            if (instructionSet.equals(VMRuntime.getInstructionSet(abi)))
                return true;

        return false;
    }

}
