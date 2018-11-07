// © 2018 and later: Unicode, Inc. and others.
// License & terms of use: http://www.unicode.org/copyright.html#License
/*
 * ******************************************************************************
 * Copyright (C) 2007-2013, International Business Machines Corporation and others.
 * All Rights Reserved.
 * ******************************************************************************
 */
package com.ibm.icu.dev.tool.tzu;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

/**
 * A class that represents an updatable ICU4J jar file. A file is an updatable ICU4J jar file if it
 * <ul>
 * <li>exists</li>
 * <li>is a file (ie. not a directory)</li>
 * <li>does not end with .ear or .war (these file types are unsupported)</li>
 * <li>ends with .jar</li>
 * <li>is updatable according the <code>isUpdatable</code></li>
 * <li>is not signed.</li>
 * </ul>
 */
public class ICUFile {
    private static final String BUNDLED_ICU4J_JAR = "icu4j-core.jar";

    /**
     * ICU version to use if one cannot be found.
     */
    private static final String ICU_VERSION_UNKNOWN = "Unknown";

    /**
     * A directory entry that is found in every updatable ICU4J jar file.
     */
    private static final String TZ_ENTRY_DIR = "com/ibm/icu/impl";

    /**
     * The timezone resource files.
     */
    private static final String[] ZONEINFO_RESOURCES = {
        "zoneinfo.res",
        "zoneinfo64.res",
    };

    /**
     * Other time zone resource files for ICU 4.4 or later
     */
    private static final String[] OTHER_TZ_RESOURCES = {
        "metaZones.res",
        "timezoneTypes.res",
        "windowsZones.res",
    };

    /**
     * Other time zone resource files for ICU 3.8 to 4.2
     */
    private static final String[] OTHER_TZ_RESOURCES_38_TO_42 = {
        "metazoneInfo.res",
        "supplementalData.res",
    };

    /**
     * Key to use when getting the version of a timezone resource.
     */
    public static final String TZ_VERSION_KEY = "TZVersion";

    /**
     * Timezone version to use if one cannot be found.
     */
    public static final String TZ_VERSION_UNKNOWN = "Unknown";

    /**
     * Jar entry path where some files are duplicated. This is an issue with icu4j 3.8.0.
     */
    public static final String DUPLICATE_ENTRY_PATH = "com/ibm/icu/impl/duration/impl/data";

    /**
     * The buffer size to use for copying data.
     */
    private static final int BUFFER_SIZE = 1024;

    /**
     * A map that caches links from URLs to time zone data to their downloaded File counterparts.
     */
    private static final Map cacheMap = new HashMap();


    // ----------------- Instance Fields ---------------------------
    /**
     * The current logger.
     */
    private Logger logger;

    /**
     * The ICU4J jar file represented by this ICUFile.
     */
    private File icuFile;

    /**
     * The ICU version of the ICU4J jar.
     */
    private String icuVersion;

    /**
     * The ICU data version number
     */
    private int icuDataVersion;

    /**
     * The entry for the zoneinfo resource inside the ICU4J jar.
     */
    private JarEntry tzEntry;

    /**
     * The other time zone resource entries inside the ICU4J jar.
     */
    private List<JarEntry> otherTzEntries;

    /**
     * The version of the timezone resource inside the ICU4J jar.
     */
    private String tzVersion;


    /**
     * Determines the version of a timezone resource as a standard file without locking the file.
     * 
     * @param tzFile
     *            The file representing the timezone resource.
     * @param logger
     *            The current logger.
     * @return The version of the timezone resource.
     */
    public static String findFileTZVersion(File tzFile, Logger logger) {
        ICUFile rawTZFile = new ICUFile(logger);

        try {
            File temp = File.createTempFile("zoneinfo", ".res");
            temp.deleteOnExit();
            rawTZFile.copyFile(tzFile, temp);
            return findTZVersion(temp, logger);
        } catch (IOException ex) {
            logger.errorln(ex.getMessage());
            return null;
        }
    }

    /**
     * Determines the version of a timezone resource as a standard file, but locks the file for the
     * duration of the program.
     * 
     * @param tzFile
     *            The file representing the timezone resource.
     * @param logger
     *            The current logger.
     * @return The version of the timezone resource.
     */
    private static String findTZVersion(File tzFile, Logger logger) {
        try {
            String filename = tzFile.getName();
            String entryname = filename.substring(0, filename.length() - ".res".length());

            URL url = new URL(tzFile.getAbsoluteFile().getParentFile().toURL().toString());
            ClassLoader loader = new URLClassLoader(new URL[] { url });

            // UResourceBundle bundle = UResourceBundle.getBundleInstance("",
            // entryname, loader);

            URL bundleURL = new URL(new File(BUNDLED_ICU4J_JAR).toURL().toString());
            URLClassLoader bundleLoader = new URLClassLoader(new URL[] { bundleURL });
            Class bundleClass = bundleLoader.loadClass("com.ibm.icu.util.UResourceBundle");
            Method bundleGetInstance = bundleClass.getMethod("getBundleInstance", new Class[] {
                    String.class, String.class, ClassLoader.class });
            Object bundle = bundleGetInstance.invoke(null, new Object[] { "", entryname, loader });

            if (bundle != null) {
                Method bundleGetString = bundleClass.getMethod("getString",
                        new Class[] { String.class });
                String tzVersion = (String) bundleGetString.invoke(bundle,
                        new Object[] { TZ_VERSION_KEY });
                if (tzVersion != null)
                    return tzVersion;
            }
        } catch (MalformedURLException ex) {
            // this should never happen
            logger.errorln("Internal program error.");
            logger.logStackTraceToBoth(ex);
        } catch (ClassNotFoundException ex) {
            // this would most likely happen when UResourceBundle cannot be
            // resolved, which is when icu4j.jar is not where it should be
            logger.errorln("icu4j.jar not found");
            logger.logStackTraceToBoth(ex);
        } catch (NoSuchMethodException ex) {
            // this can only be caused by a very unlikely scenario
            logger.errorln("icu4j.jar not correct");
            logger.logStackTraceToBoth(ex);
        } catch (IllegalAccessException ex) {
            // this can only be caused by a very unlikely scenario
            logger.errorln("icu4j.jar not correct");
            logger.logStackTraceToBoth(ex);
        } catch (InvocationTargetException ex) {
            // if this is holding a MissingResourceException, then this is not
            // an error -- some zoneinfo files are missing version numbers
            if (!(ex.getTargetException() instanceof MissingResourceException)) {
                logger.errorln("icu4j.jar not correct");
                logger.logStackTraceToBoth(ex);
            }
        } catch (MissingResourceException ex) {
            // fall through
        }

        return TZ_VERSION_UNKNOWN;
    }

    /**
     * Finds the jar entry in the jar file that represents a timezone resource and returns it, or
     * null if none is found.
     * 
     * @param jar       The jar file to search.
     * @param entryName The target entry name
     *              
     * @return The jar entry representing the timezone resource in the jar file, or null if none is
     *         found.
     */
    private static JarEntry getTZEntry(JarFile jar, String entryName) {
        JarEntry tzEntry = null;
        Enumeration<JarEntry> e = jar.entries();
        while (e.hasMoreElements()) {
            tzEntry = e.nextElement();
            if (tzEntry.getName().endsWith(entryName))
                return tzEntry;
        }
        return null;
    }

    /**
     * Constructs an ICUFile around a file. See <code>initialize</code> for details.
     * 
     * @param file
     *            The file to wrap this ICUFile around.
     * @param logger
     *            The current logger.
     * @throws IOException
     */
    public ICUFile(File file, Logger logger) throws IOException {
        initialize(file, logger);
    }

    /**
     * Constructs an ICUFile around a file. See <code>initialize</code> for details.
     * 
     * @param filename
     *            The file to wrap this ICUFile around.
     * @param logger
     *            The current logger.
     * @throws IOException
     */
    public ICUFile(String filename, Logger logger) throws IOException {
        if (filename == null || filename.trim().length() == 0)
            throw new IOException("cannot be blank");

        initialize(new File(filename), logger);
    }

    /**
     * Constructs a blank ICUFile. Used internally for timezone resource files that are not
     * contained within a jar.
     * 
     * @param logger
     *            The current logger.
     */
    private ICUFile(Logger logger) {
        this.logger = logger;
    }

    /**
     * Compares two ICUFiles by the file they represent.
     * 
     * @param other
     *            The other ICUFile to compare to.
     * @return Whether the files represented by the two ICUFiles are equal.
     */
    public boolean equals(Object other) {
        return (!(other instanceof ICUFile)) ? false : icuFile.getAbsoluteFile().equals(
                ((ICUFile) other).icuFile.getAbsoluteFile());
    }

    /**
     * Determines the version of a timezone resource in a jar file without locking the jar file.
     * 
     * @return The version of the timezone resource.
     */
    public String findEntryTZVersion() {
        try {
            File temp = File.createTempFile("zoneinfo", ".res");
            temp.deleteOnExit();
            copyEntry(icuFile, tzEntry, temp);
            return findTZVersion(temp, logger);
        } catch (IOException ex) {
            logger.errorln(ex.getMessage());
            return null;
        }
    }

    /**
     * Returns the File object represented by this ICUFile object.
     * 
     * @return The File object represented by this ICUFile object.
     */
    public File getFile() {
        return icuFile;
    }

    /**
     * Returns the filename of this ICUFile object, without the path.
     * 
     * @return The filename of this ICUFile object, without the path.
     */
    public String getFilename() {
        return icuFile.getName();
    }

    /**
     * Returns the ICU version of this ICU4J jar.
     * 
     * @return The ICU version of this ICU4J jar.
     */
    public String getICUVersion() {
        return icuVersion;
    }

    /**
     * Returns the path of this ICUFile object, without the filename.
     * 
     * @return The path of this ICUFile object, without the filename.
     */
    public String getPath() {
        return icuFile.getAbsoluteFile().getParent();
    }

    // public static String findURLTZVersion(File tzFile) {
    // try {
    // File temp = File.createTempFile("zoneinfo", ".res");
    // temp.deleteOnExit();
    // copyFile(tzFile, temp);
    // return findTZVersion(temp);
    // } catch (IOException ex) {
    // ex.printStackTrace();
    // return null;
    // }
    // }

    /**
     * Returns the timezone resource version.
     * 
     * @return The timezone resource version.
     */
    public String getTZVersion() {
        return tzVersion;
    }

    /**
     * Returns the result of getFile().toString().
     * 
     * @return The result of getFile().toString().
     */
    public String toString() {
        return getFile().toString();
    }

    /**
     * Updates the timezone resource in this ICUFile using <code>insertURL</code> as the source of
     * the new timezone resource and the backup directory <code>backupDir</code> to store a copy
     * of the ICUFile.
     * 
     * @param baseURL
     *            The url location of the timezone resource to use.
     * @param backupDir
     *            The directory to store a backup for this ICUFile, or null if no backup.
     * @throws IOException
     * @throws InterruptedException
     */
    public void update(URL baseURL, File backupDir) throws IOException, InterruptedException {
        String message = "Updating " + icuFile.getPath() + " ...";
        logger.printlnToBoth("");
        logger.printlnToBoth(message);

        if (!icuFile.canRead() || !icuFile.canWrite())
            throw new IOException("Missing permissions for " + icuFile.getPath());

        int numEntries = 1;
        if (otherTzEntries != null) {
            numEntries += otherTzEntries.size();
        }

        JarEntry[] jarEntries = new JarEntry[numEntries];
        URL[] insertURLs = new URL[numEntries];

        jarEntries[0] = tzEntry;
        insertURLs[0] = getCachedURL(baseURL, tzEntry);

        if (insertURLs[0] == null) {
            throw new IOException(
                    "Could not download the Time Zone data, skipping update for this jar.");
        }

        if (numEntries > 1) {
            int i = 1;
            for (JarEntry otherEntry : otherTzEntries) {
                jarEntries[i] = otherEntry;
                insertURLs[i] = getCachedURL(baseURL, otherEntry);
                i++;
            }
        }

        File backupFile = null;
        if ((backupFile = createBackupFile(icuFile, backupDir)) == null)
            throw new IOException(
                    "Could not create an empty backup file (the original jar file remains unchanged).");
        if (!copyFile(icuFile, backupFile))
            throw new IOException(
                    "Could not copy the original jar file to the backup location (the original jar file remains unchanged).");
        logger.printlnToBoth("Backup location: " + backupFile.getPath());
        if (!createUpdatedJar(backupFile, icuFile, jarEntries, insertURLs))
            throw new IOException(
                    "Could not create an updated jar file at the original location (the original jar file is at the backup location).");

        // get the new timezone resource version
        tzVersion = findEntryTZVersion();

        message = "Successfully updated " + icuFile.getPath();
        logger.printlnToBoth(message);
    }

    /**
     * Copies the jar entry <code>insertEntry</code> in <code>inputFile</code> to
     * <code>outputFile</code>.
     * 
     * @param inputFile
     *            The jar file containing <code>insertEntry</code>.
     * @param inputEntry
     *            The entry to copy.
     * @param outputFile
     *            The output file.
     * @return Whether the operation was successful.
     */
    private boolean copyEntry(File inputFile, JarEntry inputEntry, File outputFile) {
        logger.loglnToBoth("Copying from " + inputFile + "!/" + inputEntry + " to " + outputFile
                + ".");
        JarFile jar = null;
        InputStream istream = null;
        OutputStream ostream = null;
        byte[] buffer = new byte[BUFFER_SIZE];
        int bytesRead;
        boolean success = false;

        try {
            jar = new JarFile(inputFile);
            istream = jar.getInputStream(inputEntry);
            ostream = new FileOutputStream(outputFile);

            while ((bytesRead = istream.read(buffer)) != -1)
                ostream.write(buffer, 0, bytesRead);

            success = true;
            logger.loglnToBoth("Copy successful.");
        } catch (IOException ex) {
            outputFile.delete();
            logger.loglnToBoth("Copy failed.");
            logger.logStackTraceToBoth(ex);
        } finally {
            // safely close the streams
            tryClose(jar);
            tryClose(istream);
            tryClose(ostream);
        }
        return success;
    }

    /**
     * Copies <code>inputFile</code> to <code>outputFile</code>.
     * 
     * @param inputFile
     *            The input file.
     * @param outputFile
     *            The output file.
     * @return Whether the operation was successful.
     */
    private boolean copyFile(File inputFile, File outputFile) {
        logger.loglnToBoth("Copying from " + inputFile + " to " + outputFile + ".");
        InputStream istream = null;
        OutputStream ostream = null;
        byte[] buffer = new byte[BUFFER_SIZE];
        int bytesRead;
        boolean success = false;

        try {
            istream = new FileInputStream(inputFile);
            ostream = new FileOutputStream(outputFile);

            while ((bytesRead = istream.read(buffer)) != -1)
                ostream.write(buffer, 0, bytesRead);

            success = true;
            logger.loglnToBoth("Copy successful.");
        } catch (IOException ex) {
            outputFile.delete();
            logger.loglnToBoth("Copy failed.");
            logger.logStackTraceToBoth(ex);
        } finally {
            // safely close the streams
            tryClose(istream);
            tryClose(ostream);
        }
        return success;
    }

    /**
     * Creates a temporary file for the jar file <code>inputFile</code> under the directory
     * <code>backupBase</code> and returns it, or returns null if a temporary file could not be
     * created. Does not put any data in the newly created file yet.
     * 
     * @param inputFile
     *            The file to backup.
     * @param backupBase
     *            The directory where backups are to be stored.
     * @return The temporary file that was created.
     */
    private File createBackupFile(File inputFile, File backupBase) {
        logger.loglnToBoth("Creating backup file for " + inputFile + " at " + backupBase + ".");
        String filename = inputFile.getName();
        String suffix = ".jar";
        String prefix = filename.substring(0, filename.length() - suffix.length());

        if (backupBase == null) {
            try {
                // no backup directory means we need to create a temporary file
                // that will be deleted on exit
                File backupFile = File.createTempFile(prefix + "~", suffix);
                backupFile.deleteOnExit();
                return backupFile;
            } catch (IOException ex) {
                return null;
            }
        }

        File backupFile = null;
        File backupDesc = null;
        File backupDir = new File(backupBase.getPath(), prefix);
        PrintStream ostream = null;

        try {
            backupBase.mkdir();
            backupDir.mkdir();
            backupFile = File.createTempFile(prefix + "~", suffix, backupDir);
            backupDesc = new File(backupDir.getPath(), backupFile.getName().substring(0,
                    backupFile.getName().length() - suffix.length())
                    + ".txt");
            backupDesc.createNewFile();
            ostream = new PrintStream(new FileOutputStream(backupDesc));
            ostream.println(inputFile.getPath());
            logger.loglnToBoth("Successfully created backup file at " + backupFile + ".");
        } catch (IOException ex) {
            logger.loglnToBoth("Failed to create backup file.");
            logger.logStackTraceToBoth(ex);
            if (backupFile != null)
                backupFile.delete();
            if (backupDesc != null)
                backupDesc.delete();
            backupDir.delete();
            backupFile = null;
        } finally {
            tryClose(ostream);
        }

        return backupFile;
    }

    /**
     * Copies <code>inputFile</code> to <code>outputFile</code>, replacing
     * <code>insertEntry</code> with <code>inputURL</code>.
     * 
     * @param inputFile
     *            The input jar file.
     * @param outputFile
     *            The output jar file.
     * @param insertEntry
     *            The entry to be replaced.
     * @param inputURL
     *            The URL to use in replacing the entry.
     * @return Whether the operation was successful.
     */
    private boolean createUpdatedJar(File inputFile, File outputFile, JarEntry[] insertEntries,
            URL[] inputURLs) {
        logger.loglnToBoth("Copying " + inputFile + " to " + outputFile + ",");
        for (int i = 0; i < insertEntries.length; i++) {
            if (insertEntries[i] != null) {
                logger.loglnToBoth("    replacing " + insertEntries[i] + " with " + inputURLs[i]);
            }
        }

        JarFile jar = null;
        JarOutputStream ostream = null;
        InputStream istream = null;
        InputStream jstream = null;
        byte[] buffer = new byte[BUFFER_SIZE];
        int bytesRead;
        boolean success = false;
        Set<String> possibleDuplicates = new HashSet<String>();

        try {
            jar = new JarFile(inputFile);
            ostream = new JarOutputStream(new FileOutputStream(outputFile));

            Enumeration<JarEntry> e = jar.entries();
            while (e.hasMoreElements()) {
                JarEntry currentEntry = e.nextElement();
                String entryName = currentEntry.getName();
                if (entryName.startsWith(DUPLICATE_ENTRY_PATH)) {
                    if (!possibleDuplicates.contains(entryName)) {
                        possibleDuplicates.add(entryName);
                    } else {
                        // ruh roh, we have a duplicate entry!
                        // (just ignore it and continue)
                        logger.printlnToBoth("Warning: Duplicate " + entryName
                                + " found. Ignoring the duplicate.");
                        continue;
                    }
                }

                boolean isReplaced = false;
                for (int i = 0; i < insertEntries.length; i++) {
                    if (insertEntries[i] != null) {
                        if (entryName.equals(insertEntries[i].getName())) {
                            // if the current entry *is* the one that needs updating write a new entry based
                            // on the input stream (from the URL)
                            // currentEntry.setTime(System.currentTimeMillis());
                            ostream.putNextEntry(new JarEntry(entryName));

                            URLConnection con = inputURLs[i].openConnection();
                            con.setRequestProperty("user-agent", System.getProperty("http.agent"));
                            istream = con.getInputStream();

                            while ((bytesRead = istream.read(buffer)) != -1) {
                                ostream.write(buffer, 0, bytesRead);
                            }
                            istream.close();
                            isReplaced = true;
                            break;
                        }
                    }
                }
                if (!isReplaced) {
                    // if the current entry isn't the one that needs updating write a copy of the
                    // old entry from the old file
                    ostream.putNextEntry(new JarEntry(entryName));

                    jstream = jar.getInputStream(currentEntry);
                    while ((bytesRead = jstream.read(buffer)) != -1)
                        ostream.write(buffer, 0, bytesRead);
                    jstream.close();
                }
            }

            success = true;
            logger.loglnToBoth("Copy successful.");
        } catch (IOException ex) {
            outputFile.delete();
            logger.loglnToBoth("Copy failed:");
            logger.logStackTraceToBoth(ex);
        } finally {
            // safely close the streams
            tryClose(istream);
            tryClose(ostream);
            tryClose(jstream);
            tryClose(jar);
        }
        return success;
    }

    /**
     * Performs the shared work of the constructors. Throws an IOException if <code>file</code>...
     * <ul>
     * <li>does not exist</li>
     * <li>is not a file</li>
     * <li>ends with .ear or .war (these file types are unsupported)</li>
     * <li>does not end with .jar</li>
     * <li>is not updatable according the <code>isUpdatable</code></li>
     * <li>is signed.</li>
     * </ul>
     * If an exception is not thrown, the ICUFile is fully initialized.
     * 
     * @param file
     *            The file to wrap this ICUFile around.
     * @param logger
     *            The current logger.
     * @throws IOException
     */
    private void initialize(File file, Logger log) throws IOException {
        this.icuFile = file;
        this.logger = log;
        String message = null;

        if (!file.exists()) {
            message = "Skipped " + file.getPath() + " (does not exist).";
        } else if (!file.isFile()) {
            message = "Skipped " + file.getPath() + " (not a file).";
        } else if (file.getName().endsWith(".ear") || file.getName().endsWith(".war")) {
            message = "Skipped " + file.getPath()
                    + " (this tool does not support .ear and .war files).";
            logger.loglnToBoth(message);
        } else if (!file.canRead() || !file.canWrite()) {
            message = "Skipped " + file.getPath() + " (missing permissions).";
        } else if (!file.getName().endsWith(".jar")) {
            message = "Skipped " + file.getPath() + " (not a jar file).";
        } else if (!isUpdatable()) {
            message = "Skipped " + file.getPath() + " (not an updatable ICU4J jar).";
        } else if (isSigned()) {
            message = "Skipped " + file.getPath() + " (cannot update signed jars).";
            logger.loglnToBoth(message);
        } else if (isEclipseFragment()) {
            message = "Skipped " + file.getPath()
                    + " (eclipse fragments must be updated through ICU).";
            logger.loglnToBoth(message);
        }

        if (message != null)
            throw new IOException(message);

        tzVersion = findEntryTZVersion();
    }

    /**
     * Determines whether the current jar is an Eclipse Data Fragment.
     * 
     * @return Whether the current jar is an Eclipse Fragment.
     */
    private boolean isEclipseDataFragment() {
        return (icuFile.getPath().indexOf("plugins" + File.separator + "com.ibm.icu.data.update") >= 0 && icuFile
                .getName().equalsIgnoreCase("icu-data.jar"));
    }

    /**
     * Determines whether the current jar is an Eclipse Fragment.
     * 
     * @return Whether the current jar is an Eclipse Fragment.
     */
    private boolean isEclipseFragment() {
        return (isEclipseDataFragment() || isEclipseMainFragment());
    }

    /**
     * Determines whether the current jar is an Eclipse Main Fragment.
     * 
     * @return Whether the current jar is an Eclipse Fragment.
     */
    private boolean isEclipseMainFragment() {
        return (icuFile.getPath().indexOf("plugins") >= 0 && icuFile.getName().startsWith(
                "com.ibm.icu_"));
    }

    /**
     * Determines whether a timezone resource in a jar file is signed.
     * 
     * @return Whether a timezone resource in a jar file is signed.
     */
    private boolean isSigned() {
        return tzEntry.getCertificates() != null;
    }

    /**
     * Gathers information on the jar file represented by this ICUFile object and returns whether it
     * is an updatable ICU4J jar file.
     * 
     * @return Whether the jar file represented by this ICUFile object is an updatable ICU4J jar
     *         file.
     */
    private boolean isUpdatable() {
        JarFile jar = null;
        boolean success = false;

        try {
            // open icuFile as a jar file
            jar = new JarFile(icuFile);

            // get its manifest to determine the ICU version
            Manifest manifest = jar.getManifest();
            icuVersion = ICU_VERSION_UNKNOWN;
            if (manifest != null) {
                // try main attributes first
                Attributes attrs = manifest.getMainAttributes();
                String ver = attrs.getValue(Attributes.Name.IMPLEMENTATION_VERSION);
                if (ver != null) {
                    icuVersion = ver;
                } else {
                    // try per-entry attributes
                    Iterator<Attributes> iter = manifest.getEntries().values().iterator();
                    while (iter.hasNext()) {
                        Attributes attr = iter.next();
                        ver = attr.getValue(Attributes.Name.IMPLEMENTATION_VERSION);
                        if (ver != null) {
                            icuVersion = ver;
                            break;
                        }
                    }
                }
            }

            // if the jar's directory structure contains TZ_ENTRY_DIR and there
            // is a timezone resource in the jar, then the jar is updatable
            if (jar.getJarEntry(TZ_ENTRY_DIR) != null) {
                for (String zoneinfo : ZONEINFO_RESOURCES) {
                    tzEntry = getTZEntry(jar, zoneinfo);
                    if (tzEntry != null) {
                        // get icu data version
                        String path = tzEntry.getName();
                        String[] tokens = path.split("/");
                        for (String token : tokens) {
                            if (token.startsWith("icudt") && token.endsWith("b")) {
                                String num = token.substring(5, token.length() - 1);
                                try {
                                    icuDataVersion = Integer.parseInt(num);
                                    success = true;
                                    break;
                                } catch (NumberFormatException ex) {
                                    // fall through
                                }
                            }
                        }
                        break;
                    }
                }
            }

            // if the jar file contains other time zone resource entries, collect them.
            // this is true for ICU4J 3.8.1 or later releases
            if (success && icuDataVersion >= 38) {
                String[] others = (icuDataVersion >= 44)
                    ? OTHER_TZ_RESOURCES : OTHER_TZ_RESOURCES_38_TO_42;

                for (String otherRes : others) {
                    JarEntry je = getTZEntry(jar, otherRes);
                    if (je != null) {
                        if (otherTzEntries == null) {
                            otherTzEntries = new ArrayList<JarEntry>();
                        }
                        otherTzEntries.add(je);
                    }
                }
            }

        } catch (IOException ex) {
            // unable to create the JarFile or unable to get the Manifest
            // log the unexplained i/o error, but we must drudge on
            logger.loglnToBoth("Error reading " + icuFile.getPath() + ".");
            logger.logStackTraceToBoth(ex);
        } finally {
            // close the jar gracefully
            if (!tryClose(jar))
                logger.errorln("Could not properly close the jar file " + icuFile + ".");
        }

        // return whether the jar is updatable or not
        return success;
    }

    private URL getCachedURL(URL baseUrl, JarEntry entry) {
        String path = entry.getName();
        int lastSlash = path.lastIndexOf('/');
        String resName = path.substring(lastSlash + 1);

        String dataPath;
        if (icuDataVersion >= 44) {
            dataPath = "44";
        } else if (icuDataVersion >= 42) {
            dataPath = "42";
        } else if (icuDataVersion >= 40) {
            dataPath = "40";
        } else if (icuDataVersion >= 38) {
            dataPath = "38";
        } else {
            dataPath = "36";
        }

        URL url;
        try {
            url = new URL(baseUrl.toString() + "/" + dataPath + "/be/" + resName);
        } catch (MalformedURLException ex) {
            return null;
        }

        File outputFile = (File) cacheMap.get(url);
        if (outputFile != null) {
            try {
                return outputFile.toURL();
            } catch (MalformedURLException ex) {
                return null;
            }
        } else {
            InputStream istream = null;
            OutputStream ostream = null;
            byte[] buffer = new byte[BUFFER_SIZE];
            int bytesRead;
            boolean success = false;

            try {
                outputFile = File.createTempFile(resName, null);
                outputFile.deleteOnExit();

                logger.loglnToBoth("Downloading from " + url + " to " + outputFile.getPath() + ".");

                URLConnection con = url.openConnection();
                con.setRequestProperty("user-agent", System.getProperty("http.agent"));
                istream = con.getInputStream();
                ostream = new FileOutputStream(outputFile);

                while ((bytesRead = istream.read(buffer)) != -1)
                    ostream.write(buffer, 0, bytesRead);

                success = true;
                logger.loglnToBoth("Download successful.");
            } catch (IOException ex) {
                outputFile.delete();
                logger.loglnToBoth("Download failed.");
                logger.logStackTraceToBoth(ex);
            } finally {
                // safely close the streams
                tryClose(istream);
                tryClose(ostream);
            }
            try {
                return (success && outputFile != null) ? outputFile.toURL() : null;
            } catch (MalformedURLException ex) {
                return null;
            }
        }
    }

    /**
     * Tries to close <code>closeable</code> if possible.
     * 
     * @param closeable
     *            A closeable object
     * @return false if an IOException occured, true otherwise.
     */
    private boolean tryClose(InputStream closeable) {
        if (closeable != null)
            try {
                closeable.close();
                return true;
            } catch (IOException ex) {
                return false;
            }
        else
            return true;
    }

    /**
     * Tries to close <code>closeable</code> if possible.
     * 
     * @param closeable
     *            A closeable object
     * @return false if an IOException occured, true otherwise.
     */
    private boolean tryClose(OutputStream closeable) {
        if (closeable != null)
            try {
                closeable.close();
                return true;
            } catch (IOException ex) {
                return false;
            }
        else
            return true;
    }

    /**
     * Tries to close <code>closeable</code> if possible.
     * 
     * @param closeable
     *            A closeable object
     * @return false if an IOException occured, true otherwise.
     */
    private boolean tryClose(JarFile closeable) {
        if (closeable != null)
            try {
                closeable.close();
                return true;
            } catch (IOException ex) {
                return false;
            }
        else
            return true;
    }
}
