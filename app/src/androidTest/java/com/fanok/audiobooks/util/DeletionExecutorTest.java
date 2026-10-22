package com.fanok.audiobooks.util;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import androidx.test.platform.app.InstrumentationRegistry;
import com.fanok.audiobooks.util.DeletionPlanner.Plan;
import com.fanok.audiobooks.util.DeletionPlanner.Row;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class DeletionExecutorTest {

    private static final String BOOK_A = "https://akniga.org/a";

    private Context context;
    private File base;
    private File root;
    private List<String> roots;

    @Before
    public void setUp() throws IOException {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        base = new File(context.getCacheDir(), "deletion-executor-test");
        deleteTree(base);
        root = new File(base, "root");
        roots = Collections.singletonList(root.getAbsolutePath());
        for (String path : new String[]{"A/01.mp3", "A/10.mp3", "A (other reader)/01.mp3", "other/1.mp3", "A.mp3"}) {
            File file = new File(root, path);
            assertTrue(file.getParentFile().isDirectory() || file.getParentFile().mkdirs());
            try (FileOutputStream out = new FileOutputStream(file)) {
                out.write(new byte[]{1, 2, 3});
            }
        }
    }

    @After
    public void tearDown() {
        deleteTree(base);
    }

    @Test
    public void deletesOnlyTheRecordedCopiesAndPrunesTheirEmptyFolder() {
        List<Row> bookA = Arrays.asList(
                new Row(BOOK_A, "https://cdn.akniga.org/a/01.mp3", file("A/01.mp3")),
                new Row(BOOK_A, "https://cdn.akniga.org/a/10.mp3", "file://" + file("A/10.mp3")),
                new Row(BOOK_A, "https://cdn.akniga.org/a/1.mp3", null));
        Plan plan = DeletionPlanner.plan(bookA, roots, false, url -> url);

        Set<String> failed = DownloadStorageHelper.deleteRecordedUris(context, plan.urisToDelete, roots);

        assertTrue(failed.isEmpty());
        assertFalse(new File(root, "A/01.mp3").exists());
        assertFalse(new File(root, "A/10.mp3").exists());
        assertFalse(new File(root, "A").exists());
        assertTrue(new File(root, "A (other reader)/01.mp3").isFile());
        assertTrue(new File(root, "other/1.mp3").isFile());
        assertTrue(new File(root, "A.mp3").isFile());
        assertTrue(root.isDirectory());
    }

    @Test
    public void keepsAFolderThatStillHasAnEntry() {
        Plan plan = DeletionPlanner.plan(
                Collections.singletonList(new Row(BOOK_A, "https://cdn.akniga.org/a/01.mp3", file("A/01.mp3"))),
                roots, true, url -> url);

        Set<String> failed = DownloadStorageHelper.deleteRecordedUris(context, plan.urisToDelete, roots);

        assertTrue(failed.isEmpty());
        assertFalse(new File(root, "A/01.mp3").exists());
        assertTrue(new File(root, "A/10.mp3").isFile());
    }

    @Test
    public void aRootFolderIsNeverPrunedEvenWhenEmpty() {
        File only = new File(root, "other/1.mp3");
        Set<String> failed = DownloadStorageHelper.deleteRecordedUris(context,
                Collections.singletonList(only.getAbsolutePath()), Collections.singletonList(new File(root, "other").getAbsolutePath()));

        assertTrue(failed.isEmpty());
        assertFalse(only.exists());
        assertTrue(new File(root, "other").isDirectory());
    }

    private String file(String relative) {
        return new File(root, relative).getAbsolutePath();
    }

    private static void deleteTree(File file) {
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) deleteTree(child);
        }
        //noinspection ResultOfMethodCallIgnored
        file.delete();
    }
}
