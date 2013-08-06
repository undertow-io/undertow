package io.undertow.util;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.xnio.IoUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

/**
 * Test file system watcher, both poll and non poll based
 *
 * @author Stuart Douglas
 */
@RunWith(Parameterized.class)
public class FileSystemWatcherTestCase {

    public static final String DIR_NAME = "/fileSystemWatcherTest";
    public static final String EXISTING_FILE_NAME = "a.txt";
    public static final String EXISTING_DIR = "existingDir";

    private final BlockingDeque<Collection<FileChangeEvent>> results = new LinkedBlockingDeque<Collection<FileChangeEvent>>();

    File rootDir;
    File existingSubDir;

    private final boolean forcePoll;

    @Parameterized.Parameters
    public static List<Object[]> parameters() {
        final List<Object[]> params = new ArrayList<Object[]>();
        params.add(new Boolean[]{true});
        params.add(new Boolean[]{false});
        return params;
    }

    public FileSystemWatcherTestCase(boolean forcePoll) {
        this.forcePoll = forcePoll;
    }

    @Before
    public void setup() throws Exception {

        rootDir = new File(System.getProperty("java.io.tmpdir") + DIR_NAME);
        FileUtils.deleteRecursive(rootDir);

        rootDir.mkdirs();
        File existing = new File(rootDir, EXISTING_FILE_NAME);
        touchFile(existing);
        existingSubDir = new File(rootDir, EXISTING_DIR);
        existingSubDir.mkdir();
        existing = new File(existingSubDir, EXISTING_FILE_NAME);
        touchFile(existing);
    }

    private static void touchFile(File existing) throws IOException {
        FileOutputStream out = new FileOutputStream(existing);
        try {
            out.write(("data" + System.currentTimeMillis()).getBytes());
            out.flush();
        } finally {
            IoUtils.safeClose(out);
        }
    }

    @After
    public void after() {
        FileUtils.deleteRecursive(rootDir);
    }


    @Test
    public void testFileSystemWatcher() throws Exception {
        FileSystemWatcher watcher = new FileSystemWatcher();
        watcher.setForcePoll(forcePoll);
        watcher.setPollInterval(10);
        try {
            watcher.start();
            watcher.addPath(rootDir, new FileChangeCallback() {
                @Override
                public void handleChanges(Collection<FileChangeEvent> changes) {
                    results.add(changes);
                }
            });
            //first add a file
            File added = new File(rootDir, "newlyAddedFile.txt").getAbsoluteFile();
            touchFile(added);
            checkResult(added, FileChangeEvent.Type.ADDED);
            added.setLastModified(500);
            checkResult(added, FileChangeEvent.Type.MODIFIED);
            added.delete();
            Thread.sleep(1);
            checkResult(added, FileChangeEvent.Type.REMOVED);
            added = new File(existingSubDir, "newSubDirFile.txt");
            touchFile(added);
            checkResult(added, FileChangeEvent.Type.ADDED);
            added.setLastModified(500);
            checkResult(added, FileChangeEvent.Type.MODIFIED);
            added.delete();
            Thread.sleep(1);
            checkResult(added, FileChangeEvent.Type.REMOVED);
            File existing = new File(rootDir, EXISTING_FILE_NAME);
            existing.delete();
            Thread.sleep(1);
            checkResult(existing, FileChangeEvent.Type.REMOVED);
            File newDir = new File(rootDir, "newlyCreatedDirectory");
            newDir.mkdir();
            checkResult(newDir, FileChangeEvent.Type.ADDED);
            added = new File(newDir, "newlyAddedFileInNewlyAddedDirectory.txt").getAbsoluteFile();
            touchFile(added);
            checkResult(added, FileChangeEvent.Type.ADDED);
            added.setLastModified(500);
            checkResult(added, FileChangeEvent.Type.MODIFIED);
            added.delete();
            Thread.sleep(1);
            checkResult(added, FileChangeEvent.Type.REMOVED);


        } finally {
            watcher.stop();
        }

    }

    private void checkResult(File file, FileChangeEvent.Type type) throws InterruptedException {
        Collection<FileChangeEvent> results = this.results.poll(10, TimeUnit.SECONDS);
        Assert.assertNotNull(results);
        Assert.assertEquals(1, results.size());
        FileChangeEvent res = results.iterator().next();
        if (type == FileChangeEvent.Type.REMOVED && res.getType() == FileChangeEvent.Type.MODIFIED) {
            //sometime OS's will give a MODIFIED event before the REMOVED one
            results = this.results.poll(10, TimeUnit.SECONDS);
            Assert.assertNotNull(results);
            Assert.assertEquals(1, results.size());
            res = results.iterator().next();
        }
        Assert.assertEquals(file, res.getFile());
        Assert.assertEquals(type, res.getType());
    }
}
