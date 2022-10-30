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
package com.telenav.cactus.metadata.plugin;

import java.io.File;
import java.io.IOException;
import java.lang.ProcessBuilder.Redirect;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.telenav.cactus.metadata.BuildMetadata.KEY_GIT_COMMIT_HASH;
import static com.telenav.cactus.metadata.BuildMetadata.KEY_GIT_COMMIT_TIMESTAMP;
import static com.telenav.cactus.metadata.BuildMetadata.KEY_GIT_REPO_CLEAN;
import static com.telenav.cactus.metadata.BuildMetadataUpdater.main;
import static java.lang.System.currentTimeMillis;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.Files.createDirectories;
import static java.nio.file.Files.delete;
import static java.nio.file.Files.exists;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.nio.file.StandardOpenOption.WRITE;
import static java.time.format.DateTimeFormatter.ISO_DATE_TIME;
import static org.apache.maven.plugins.annotations.InstantiationStrategy.SINGLETON;
import static org.apache.maven.plugins.annotations.LifecyclePhase.PROCESS_SOURCES;
import static org.apache.maven.plugins.annotations.ResolutionScope.NONE;

/**
 * Generates build.properties and project.properties files into
 * <code>target/classes/project.properties</code> and
 * <code>target/classes/build.properties</code> (configurable using the
 * <code>project-properties-dest</code> property).
 *
 * @author Tim Boudreau
 */
@SuppressWarnings("unused")
@org.apache.maven.plugins.annotations.Mojo(
        defaultPhase = PROCESS_SOURCES,
        requiresDependencyResolution = NONE,
        instantiationStrategy = SINGLETON,
        name = "build-metadata", threadSafe = true)
public class BuildMetadataMojo extends AbstractMojo
{
    private static final Map<String, Optional<Path>> BINARY_PATH_CACHE = new ConcurrentHashMap<>();
    private static final DateTimeFormatter GIT_LOG_FORMAT = new DateTimeFormatterBuilder()
            .appendValue(ChronoField.YEAR, 4).appendLiteral("-")
            .appendValue(ChronoField.MONTH_OF_YEAR, 2).appendLiteral("-")
            .appendValue(ChronoField.DAY_OF_MONTH, 2)
            .appendLiteral(' ')
            .appendValue(ChronoField.HOUR_OF_DAY, 2)
            .appendLiteral(':')
            .appendValue(ChronoField.MINUTE_OF_HOUR, 2)
            .appendLiteral(':')
            .appendValue(ChronoField.SECOND_OF_MINUTE, 2)
            .appendLiteral(' ')
            .appendOffset("+HHMM", "+0000")
            .parseLenient()
            .toFormatter();

    /**
     * The relative path to the destination directory.
     */
    @Parameter(property = "cactus.project-properties-destination",
            defaultValue = "target/classes/project.properties")
    private String projectPropertiesDestination;

    @Parameter(property = "cactus.build.metadata.skip")
    private boolean skip;

    @Parameter(property = "cactus.verbose")
    private boolean verbose;

    @Parameter(defaultValue = "${project}", readonly = true)
    private MavenProject project;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException
    {
        Logger log = LoggerFactory.getLogger(BuildMetadataMojo.class);
        try
        {
            performTasks(log, project);
        }
        catch (Exception ex)
        {
            if (ex instanceof MojoExecutionException)
            {
                throw ((MojoExecutionException) ex);
            }
            else if (ex instanceof MojoFailureException)
            {
                throw ((MojoFailureException) ex);
            }
            else
            {
                throw new MojoExecutionException(
                        "Failed generating metadata", ex);
            }
        }
    }

    protected void performTasks(Logger log, MavenProject project) throws Exception
    {
        if (skip)
        {
            log.info("Build metadata is skipped");
            return;
        }
        if ("pom".equals(project.getPackaging()))
        {
            log.info("Not writing project metadata for a non-java project.");
            return;
        }
        Path propsFile = project.getBasedir().toPath().resolve(
                projectPropertiesDestination);
        if (!exists(propsFile.getParent()))
        {
            createDirectories(propsFile.getParent());
        }
        String propertiesFileContent = projectProperties(project);
        writeString(propsFile, propertiesFileContent);
        List<String> args = new ArrayList<>(8);
        args.add(propsFile.getParent().toString());
        Optional<Path> checkout = checkoutRoot(project.getBasedir().toPath());
        if (!checkout.isPresent())
        {
            log.warn("Did not find a git checkout for " + project.getBasedir());
        }
        else
        {
            Path repo = checkout.get();
            args.add(KEY_GIT_COMMIT_HASH);
            String head = repoHead(repo).orElse(
                    "0000000000000000000000000000000000000000");
            args.add(head);

            args.add(KEY_GIT_REPO_CLEAN);
            boolean dirty = isDirty(repo).orElse(true);
            args.add(Boolean.toString(dirty));

            commitDate(repo, log).ifPresent(when ->
            {
                args.add(KEY_GIT_COMMIT_TIMESTAMP);
                args.add(when.format(ISO_DATE_TIME));
            });
        }
        main(args.toArray(new String[args.size()]));
        ifVerbose(() ->
        {
            log.info("Wrote project.properties");
            log.info("------------------------");
            log.info("to " + propsFile + "\n");
            log.info(propertiesFileContent + "\n");
            Path buildProps = propsFile.getParent().resolve(
                    "build.properties");
            if (exists(buildProps))
            {
                log.info("Wrote build.properties");
                log.info("----------------------");
                log.info("to " + buildProps + "\n");
                log.info(readString(buildProps));
            }
            else
            {
                log.warn("No build file was generated in " + buildProps);
            }
            return null;
        });
    }

    private static String readString(Path file) throws IOException
    {
        return new String(Files.readAllBytes(file), UTF_8);
    }

    private static void writeString(Path path, String content) throws IOException
    {
        Files.write(path, content.getBytes(UTF_8), WRITE, TRUNCATE_EXISTING,
                CREATE);
    }

    private String projectProperties(MavenProject project)
    {
        StringBuilder sb = new StringBuilder();
        String name = project.getName();
        if (name == null)
        {
            name = project.getArtifactId();
        }
        return sb.append("project-name=").append(name)
                .append("\nproject-version=").append(project.getVersion())
                .append("\nproject-group-id=").append(project.getGroupId())
                .append("\nproject-artifact-id=")
                .append(project.getArtifactId())
                .append('\n').toString();
    }

    private <T> T ifVerbose(Callable<T> run) throws Exception
    {
        if (verbose)
        {
            return run.call();
        }
        return null;
    }

    // To avoid depending on cactus-git:
    private static Optional<Path> checkoutRoot(Path dir)
    {
        Path curr = dir;
        do
        {
            if (Files.exists(curr.resolve(".git")))
            {
                return Optional.of(dir);
            }
        }
        while ((curr = curr.getParent()) != null);
        return Optional.empty();
    }

    private static Optional<Boolean> runGitForExitCode(Path in, String... args)
            throws IOException, InterruptedException, ExecutionException
    {
        Optional<Path> gitBinary = findExecutable("git");
        if (!gitBinary.isPresent())
        {
            return Optional.empty();
        }
        Path git = gitBinary.get();
        List<String> cmd = new ArrayList<>();
        cmd.add(git.toString());
        addCommonGitArgs(cmd);
        cmd.addAll(Arrays.asList(args));
        ProcessBuilder pb = new ProcessBuilder(cmd);
        prepareGitEnvironment(pb);
        Process proc = pb.start();
        while (proc.isAlive())
        {
            Thread.sleep(50);
        }
        return Optional.of(proc.exitValue() == 0);
    }

    private static Optional<String> runGit(Path in, String... args) throws IOException, InterruptedException, ExecutionException
    {
        Optional<Path> gitBinary = findExecutable("git");
        if (!gitBinary.isPresent())
        {
            return Optional.empty();
        }
        Path git = gitBinary.get();
        List<String> cmd = new ArrayList<>();
        cmd.add(git.toString());
        addCommonGitArgs(cmd);
        for (String arg : args)
        {
            cmd.add(arg);
        }
        ProcessBuilder pb = new ProcessBuilder(cmd);
        prepareGitEnvironment(pb);
        Path tempFile = tempFile();
        try
        {
            pb.redirectOutput(Redirect.to(tempFile.toFile()));

            Process proc = pb.start();
            while (proc.isAlive())
            {
                Thread.sleep(50);
            }
            if (proc.exitValue() != 0)
            {
                return Optional.empty();
            }
            return Optional.of(new String(Files.readAllBytes(tempFile), UTF_8)
                    .trim());
        }
        finally
        {
            if (exists(tempFile))
            {
                delete(tempFile);
            }
        }
    }

    private static Path tempFile()
    {
        Path tmp = Paths.get(System.getProperty("java.io.tmpdir"));
        return tmp.resolve(newFileName());
    }

    private static String newFileName()
    {
        return "rg-" + currentTimeMillis() + "-" + ThreadLocalRandom
                .current().nextLong();
    }

    // A few methods borrowed from PathUtils in cactus-utils to avoid
    // creating a circular family-to-family dependency
    private static Optional<Path> findExecutable(String name,
            Path... additionalSearchLocations)
    {
        if (additionalSearchLocations.length == 0)
        {
            return BINARY_PATH_CACHE.computeIfAbsent(name, n ->
            {
                return _findExecutable(n);
            });
        }
        return _findExecutable(name, additionalSearchLocations);
    }

    private static Optional<Path> _findExecutable(String name,
            Path... additionalSearchLocations)
    {
        if (name.indexOf(File.separatorChar) >= 0)
        {
            Path path = Paths.get(name);
            if (Files.exists(path) && Files.isExecutable(path))
            {
                return Optional.of(path);
            }
            name = path.getFileName().toString();
        }
        String systemPath = System.getenv("PATH");
        Set<Path> all = new LinkedHashSet<>(Arrays.asList(
                additionalSearchLocations));
        if (systemPath != null)
        {
            for (String s : systemPath.split(":"))
            {
                all.add(Paths.get(s));
            }
        }
        Path home = home();
        // Ensure we look in some common places:
        all.addAll(Arrays.asList(Paths.get("/bin"),
                Paths.get("/usr/bin"),
                Paths.get("/usr/local/bin"),
                Paths.get("/opt/bin"),
                Paths.get("/opt/local/bin"),
                Paths.get("/opt/homebrew/bin"),
                home.resolve(".local").resolve("bin"),
                home.resolve("bin")
        ));
        return findExecutable(all, name);
    }

    public static Optional<Path> findExecutable(Iterable<? extends Path> in,
            String name)
    {
        for (Path path : in)
        {
            Path target = path.resolve(name);
            if (Files.exists(target) && Files.isExecutable(target))
            {
                return Optional.of(target);
            }
        }
        return Optional.empty();
    }

    public static Path home()
    {
        return fromSystemProperty("user.home", () -> fromSystemProperty(
                "java.io.tmpdir", () -> Paths.get("/")));
    }

    private static Path fromSystemProperty(String what, Supplier<Path> fallback)
    {
        String prop = System.getProperty(what);
        return prop == null
               ? fallback.get()
               : Paths.get(prop);
    }

    private static Optional<String> repoHead(Path repo) throws IOException, InterruptedException, InterruptedException, ExecutionException
    {
        // "rev-parse", "HEAD"
        return runGit(repo, "rev-parse", "HEAD");
    }

    private static Optional<Boolean> isDirty(Path path) throws IOException, InterruptedException, ExecutionException
    {
        // "diff", "--quiet", "--ignore-submodules=dirty"
        return runGitForExitCode(path, "diff", "--quiet",
                "--ignore-submodules=dirty");
    }

    private static Optional<ZonedDateTime> commitDate(Path path, Logger log)
            throws IOException, InterruptedException, ExecutionException
    {
        return runGit(path, "log", "-1", "--format=format:%cd", "--date=iso",
                "--no-color", "--encoding=utf8")
                .flatMap(date -> fromGitLogFormat(date, log));
    }

    private static Optional<ZonedDateTime> fromGitLogFormat(String txt,
            Logger log)
    {
        if (txt.isEmpty())
        {
            return Optional.empty();
        }
        try
        {
            return Optional.of(
                    ZonedDateTime.parse(txt, GIT_LOG_FORMAT)
                            .withZoneSameInstant(ZoneId.of("GMT")));
        }
        catch (DateTimeParseException ex)
        {
            log.error("Failed to parse git log date string '" + txt + "'", ex);
            return Optional.empty();
        }
    }

    private static void addCommonGitArgs(List<String> list)
    {
        // Want this for everything - using a pager would hang the
        // process waiting for input
        list.add("--no-pager");

        // Pending - we should probably modify GitCheckout.push() and
        // friends to either explicitly pass what they intend, or to
        // use this there.  But for our purposes, we are assuming remote
        // branches match local branches.
        list.add("-c");
        list.add("push.default=current");

        // Also defeat any entry in .gitconfig that tells pull always to rebase
        // since that would violate assumptions
        list.add("-c");
        list.add("pull.rebase=false");

        // And use a consistent rename limit for merges. 0 is a proxy for
        // "very large number".  C.f.
        // https://github.com/git/git/commit/9dd29dbef01e39fe9df81ad9e5e193128d8c5ad5
        list.add("-c");
        list.add("diff.renamelimit=0");

        // And use a consistent rename limit for merges. 0 is a proxy for
        // "very large number".  C.f.
        // https://github.com/git/git/commit/9dd29dbef01e39fe9df81ad9e5e193128d8c5ad5
        list.add("-c");
        list.add("init.defaultBranch=main");
    }

    private static void prepareGitEnvironment(ProcessBuilder bldr)
    {
        // As a sanity measure, if some command inadvertently tries
        // to invoke an interactive pager, ensure it is something that
        // exits immediately
        bldr.environment().put("GIT_PAGER", "/bin/cat");
        // Same reason - if something is going to pause asking for a password,
        // ensure we simply abort immediately
        bldr.environment().put("GIT_ASKPASS", "/usr/bin/false");
        // We do not want /etc/gitconfig to alter the behavior of the
        // plugin
        bldr.environment().put("GIT_CONFIG_NOSYSTEM", "1");
        // Make sure git does not think it can use the terminal (it shouldn't
        // think so anyway, but this can't hurt).
        bldr.environment().put("GIT_TERMINAL_PROMPT", "0");
    }

}
