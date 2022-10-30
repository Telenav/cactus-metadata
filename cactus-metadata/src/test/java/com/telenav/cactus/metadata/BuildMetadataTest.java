////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
//
// © 2011-2022 Telenav, Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// https://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
package com.telenav.cactus.metadata;

import static com.telenav.cactus.metadata.BuildMetadata.KEY_GIT_COMMIT_HASH;
import static com.telenav.cactus.metadata.BuildMetadata.KEY_GIT_COMMIT_TIMESTAMP;
import static com.telenav.cactus.metadata.BuildMetadata.KEY_GIT_REPO_CLEAN;
import static com.telenav.cactus.metadata.BuildMetadata.todaysLocalDate;
import static com.telenav.cactus.metadata.BuildName.toBuildNumber;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.Month;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import static java.time.format.DateTimeFormatter.ISO_DATE_TIME;
import static java.time.format.DateTimeFormatter.ISO_INSTANT;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author timb
 */
public class BuildMetadataTest
{

    static final ZonedDateTime ZDT = ZonedDateTime.parse("2021-03-01T15:36:09Z",
            ISO_DATE_TIME);
    static final String SOME_HASH = "a304c27daedf56b9374a382ac06f55c7415b2d49";
    static final String SOME_HASH_SHORT = "a304c27";

    @Test
    public void testCleanRepo()
    {
        if (tooCloseToMidnightGMT()) {
            // This can fail if this test runs at EXACTLY the right
            // time near midnight GMT, and we compute our expected local date
            // before midnight and the metadata is instantiated after
            //
            // Unlikely, but annoying to debug.
            return;
        }
        BuildMetadata md = new BuildMetadata(BuildMetadataTest.class,
                BuildMetadata.Type.CURRENT,
                map(SOME_HASH, ZDT, true));

        Map<String, String> props = md.buildProperties();
        assertTrue(props.containsKey(KEY_GIT_COMMIT_TIMESTAMP));
        assertTrue(props.containsKey(KEY_GIT_COMMIT_HASH));
        assertTrue(props.containsKey(KEY_GIT_REPO_CLEAN));
        assertTrue(props.containsKey(BuildMetadata.KEY_BUILD_DATE));
        assertTrue(props.containsKey(BuildMetadata.KEY_BUILD_NAME));
        assertTrue(props.containsKey(BuildMetadata.KEY_BUILD_NUMBER));

        LocalDate expectedDate = ZDT.toLocalDate();

        Optional<String> commitHash = md.gitCommitHash();
        assertEquals(props, md.buildProperties(), "Build properties unexpectedly changed.");

        assertTrue(commitHash.isPresent());
        assertEquals(SOME_HASH, md.gitCommitHash().get());
        assertTrue(md.isCleanRepository());
        assertTrue(md.shortGitCommitHash().isPresent());
        assertEquals(SOME_HASH_SHORT, md.shortGitCommitHash().get());

        assertEquals(expectedDate, md.currentBuildDate());
        assertEquals(toBuildNumber(expectedDate), md.currentBuildNumber());
    }

    @Test
    public void testDirtyRepo()
    {
        if (tooCloseToMidnightGMT())
        {
            // This can fail if this test runs at EXACTLY the right
            // time near midnight GMT, and we compute our expected local date
            // before midnight and the metadata is instantiated after
            //
            // Unlikely, but annoying to debug.
            return;
        }
        LocalDate currentDate = todaysLocalDate();
        LocalDate gitDate = ZDT.toLocalDate();

        assertNotEquals(currentDate, gitDate);

        BuildMetadata md = new BuildMetadata(BuildMetadataTest.class,
                BuildMetadata.Type.CURRENT,
                map(SOME_HASH, ZDT, false));

        assertTrue(md.gitCommitHash().isPresent());
        assertEquals(SOME_HASH, md.gitCommitHash().get());
        assertFalse(md.isCleanRepository());
        assertTrue(md.shortGitCommitHash().isPresent());
        assertEquals(SOME_HASH_SHORT, md.shortGitCommitHash().get());

        assertEquals(currentDate, md.currentBuildDate());
        int buildNumber = md.currentBuildNumber();
        assertNotEquals(toBuildNumber(currentDate), toBuildNumber(gitDate),
                "Should not produce the same build number: " + currentDate
                + " and " + gitDate);
        assertEquals(toBuildNumber(currentDate), buildNumber);
        assertNotEquals(toBuildNumber(gitDate), buildNumber);
    }

    @Test
    public void testUpdater() throws Exception
    {
        if (tooCloseToMidnightGMT())
        {
            // This can fail if this test runs at EXACTLY the right
            // time near midnight GMT, and we compute our expected local date
            // before midnight and the metadata is instantiated after
            //
            // Unlikely, but annoying to debug.
            return;
        }
        withTempFile(md ->
        {
            assertNotNull(md);
            assertTrue(md.isCleanRepository());
            assertEquals(SOME_HASH, md.gitCommitHash().get());
            LocalDate currentDate = todaysLocalDate();
            LocalDate gitDate = ZDT.toLocalDate();
            assertEquals(gitDate, md.currentBuildDate());
            assertNotEquals(currentDate, md.currentBuildDate());
            assertEquals(toBuildNumber(gitDate), md.currentBuildNumber());
        },
                KEY_GIT_REPO_CLEAN, "true",
                KEY_GIT_COMMIT_HASH, SOME_HASH,
                KEY_GIT_COMMIT_TIMESTAMP, ZDT.format(ISO_DATE_TIME));
    }

    @Test
    public void testInvalidDate() throws Exception
    {
        try
        {
            withInvalidArgs(KEY_GIT_REPO_CLEAN, "true",
                    KEY_GIT_COMMIT_HASH, SOME_HASH,
                    KEY_GIT_COMMIT_TIMESTAMP, "this is not a timestamp");
            fail("Exception should have been thrown");
        }
        catch (IllegalArgumentException ex)
        {
            // ok
        }
    }

    @Test
    public void testInvalidCleanDirty() throws Exception
    {
        try {
            withInvalidArgs(KEY_GIT_REPO_CLEAN, "snorkels",
                    KEY_GIT_COMMIT_HASH, SOME_HASH,
                    KEY_GIT_COMMIT_TIMESTAMP, ZDT.format(ISO_DATE_TIME));
            fail("Exception should have been thrown");
        } catch (IllegalArgumentException ex) {
            // ok
        }
    }

    @Test
    public void testInvalidHash() throws Exception
    {
        try {
            withInvalidArgs(KEY_GIT_REPO_CLEAN, "true",
                    KEY_GIT_COMMIT_HASH, "ABCthis is not a hash",
                    KEY_GIT_COMMIT_TIMESTAMP, ZDT.format(ISO_DATE_TIME));
            fail("Exception should have been thrown");
        } catch (IllegalArgumentException ex) {
            // ok
        }
    }
    @Test
    public void testInvalidNumberOfArguments() throws Exception
    {
        try
        {
            withInvalidArgs(KEY_GIT_REPO_CLEAN, "true",
                    KEY_GIT_COMMIT_HASH, SOME_HASH,
                    KEY_GIT_COMMIT_TIMESTAMP, ZDT.format(ISO_DATE_TIME),
                    "OOPS.");
            fail("Exception should have been thrown");
        } catch (IllegalArgumentException ex) {
            // ok
        }
    }

    @Test
    public void testGitParsedOutputIsViable() throws Exception
    {
        // This is what the date we really store looks like:
        String txt = "2022-05-30T20:51:39Z[GMT]";
        withTempFile(md ->
        {
            ZonedDateTime zdt = md.gitCommitTimestamp().get();
            assertEquals(ZoneId.of("GMT"), zdt.getZone());
            assertEquals(20, zdt.getHour());
            assertEquals(51, zdt.getMinute());
            assertEquals(39, zdt.getSecond());
            assertEquals(Month.MAY, zdt.getMonth());
            assertEquals(30, zdt.getDayOfMonth());
            assertEquals(2022, zdt.getYear());
        },
                KEY_GIT_REPO_CLEAN, "true",
                KEY_GIT_COMMIT_TIMESTAMP, txt,
                KEY_GIT_COMMIT_HASH, SOME_HASH
        );
    }

    private static void withInvalidArgs(String... args) throws Exception
    {
        withTempFile(meta -> {
            fail("Should not get invoked for " + Arrays.toString(args)
                    + " but got " + meta);
        }, args);
    }

    private static void withTempFile(Consumer<BuildMetadata> tester, String... args)
            throws Exception
    {
        Path temp = Paths.get(System.getProperty("java.io.tmpdir"));
        Path file = temp.resolve("mdtest-" + System.currentTimeMillis() + "-"
                + ThreadLocalRandom.current().nextInt(100000));
        try {
            List<String> allArgs = new LinkedList<>(Arrays.asList(args));
            allArgs.add(0, file.toString());
            BuildMetadataUpdater.main(allArgs.toArray(String[]::new));
            assertTrue(Files.exists(file), file + " not created");
            BuildMetadata meta = new BuildMetadata(BuildMetadataTest.class,
                    BuildMetadata.Type.PROJECT, Collections.emptyMap());
            Map<String, String> map = BuildMetadata.properties(
                    Files.readString(file.resolve("build.properties")));
            meta.buildProperties = map;
            tester.accept(meta);
        } finally {
            if (Files.exists(file)) {
                if (Files.exists(file.resolve("build.properties"))) {
                    Files.delete(file.resolve("build.properties"));
                }
                if (Files.exists(file.resolve("project.properties"))) {
                    Files.delete(file.resolve("project.properties"));
                }
                Files.delete(file);
            }
        }
    }

    private static boolean tooCloseToMidnightGMT()
    {
        ZonedDateTime when = ZonedDateTime.now().withZoneSameInstant(ZoneId.of("GMT"));
        return when.getHour() == 23 && when.getMinute() >= 59;
    }

    static Map<String, String> map(String commitHash, ZonedDateTime commitDateIso, boolean isClean)
    {
        Map<String, String> result = new TreeMap<>();
        result.put(KEY_GIT_COMMIT_HASH, commitHash);
        result.put(KEY_GIT_REPO_CLEAN, Boolean.toString(isClean));
        result.put(KEY_GIT_COMMIT_TIMESTAMP, commitDateIso.format(ISO_INSTANT));
        return result;
    }
}
