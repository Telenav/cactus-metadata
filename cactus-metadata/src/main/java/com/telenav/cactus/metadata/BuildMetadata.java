////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
//
// © 2011-2021 Telenav, Inc.
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

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.telenav.cactus.metadata.BuildName.toBuildNumber;
import static java.time.format.DateTimeFormatter.ISO_DATE_TIME;
import static java.util.Collections.emptyMap;

/**
 * Metadata about the calling KivaKit project as well as a program entrypoint
 * that creates this information when called from maven during the build
 * process.
 *
 * @author jonathanl (shibo)
 */
public class BuildMetadata
{
    public static final String KEY_BUILD_NAME = "build-name";

    public static final String KEY_BUILD_DATE = "build-date";

    public static final String KEY_BUILD_NUMBER = "build-number";

    public static final String KEY_GIT_COMMIT_TIMESTAMP = "commit-timestamp";

    public static final String KEY_GIT_COMMIT_HASH = "commit-long-hash";

    public static final String KEY_GIT_REPO_CLEAN = "no-local-modifications";

    /**
     * Metadata for projects
     */
    private static final Map<Class<?>, BuildMetadata> projectToMetadata = new ConcurrentHashMap<>();

    /**
     * @param type A class in the caller's project for loading resources
     * @return Metadata for the given project
     */
    public static BuildMetadata buildMetaData(Class<?> type)
    {
        return projectToMetadata.computeIfAbsent(type,
                ignored -> new BuildMetadata(type, Type.PROJECT,
                        emptyMap()));
    }

    /**
     * Returns a properties map from the given text
     */
    static Map<String, String> properties(String text)
    {
        Map<String, String> properties = new TreeMap<String, String>();
        try
        {
            Pattern pattern = Pattern.compile(
                    "(?x) (?<key> [\\w-]+?) \\s* = \\s* (?<value> .*)");
            Matcher matcher = pattern.matcher(text);
            while (matcher.find())
            {
                properties.put(matcher.group("key"), matcher.group("value"));
            }
        }
        catch (Exception ignored)
        {
        }
        return properties;
    }

    static LocalDate todaysLocalDate()
    {
        return LocalDateTime.now().atZone(ZoneId.of(ZoneOffset.UTC.getId()))
                .toLocalDate();
    }

    /**
     * The type of metadata. PROJECT specifies normal project metadata. CURRENT
     * specifies the metadata based on the current time.
     *
     * @author jonathanl (shibo)
     */
    public enum Type
    {
        PROJECT,
        CURRENT
    }

    /**
     * A class in the caller's project for loading resources
     */
    private final Class<?> type;

    /**
     * The type of metadata
     */
    private final Type metadataType;

    /**
     * Build property map
     */
    Map<String, String> buildProperties;

    /**
     * Project property map
     */
    private Map<String, String> projectProperties;

    /**
     * Additional properties for testing purposes
     */
    private final Map<String, String> additionalProperties;

    BuildMetadata(Class<?> type, Type metadataType,
            Map<String, String> additionalProperties)
    {
        this.type = type;
        this.metadataType = metadataType;
        this.additionalProperties = additionalProperties;
    }

    /**
     * Retrieves the properties in the /build.properties resource, similar to
     * this:
     *
     * <pre>
     * build-number = 104
     * build-date = 2021.03.18
     * build-name = sparkling piglet
     * </pre>
     *
     * @return The contents of the maven metadata file
     */
    public Map<String, String> buildProperties()
    {
        if (buildProperties == null)
        {
            // If we are metadata for the current build,
            if (metadataType == Type.CURRENT)
            {
                // then use current build metadata based on the time
                Map<String, String> properties = new TreeMap<String, String>();
                properties.put(KEY_BUILD_NUMBER, Integer.toString(
                        currentBuildNumber()));
                properties.put(KEY_BUILD_DATE, DateTimeFormatter.ofPattern(
                        "yyyy.MM.dd").format(currentBuildDate()));
                properties.put(KEY_BUILD_NAME, BuildName.name(
                        currentBuildNumber()));
                properties.putAll(additionalProperties);
                buildProperties = properties;
            }
            else
            {
                // otherwise, use the project's metadata.
                buildProperties = properties(metadata(type,
                        "/" + type.getSimpleName() + "-build.properties"));
                buildProperties.putAll(additionalProperties);
            }
        }

        return buildProperties;
    }

    /**
     * Returns the build number for the given date in days since
     * {@link BuildName#TELENAV_EPOCH_DAY}
     */
    public int currentBuildNumber()
    {
        return toBuildNumber(currentBuildDate());
    }

    /**
     * Get the git commit hash at the time the code was built, if present.
     *
     * @return A hash if one is available in the metadata
     */
    public Optional<String> gitCommitHash()
    {
        return Optional.ofNullable(buildProperties().get(KEY_GIT_COMMIT_HASH));
    }

    /**
     * Get the timestamp of the git commit that originated the library this
     * metadata is for, if recorded.
     *
     * @return A git timestamp, if present.
     */
    public Optional<ZonedDateTime> gitCommitTimestamp()
    {
        // We can be called while computing the properties, before buildProperties
        // is set, in which case we need to look in additional instead.
        Map<String, String> map = buildProperties == null
                                  ? additionalProperties
                                  : buildProperties;
        return Optional.ofNullable(map.get(KEY_GIT_COMMIT_TIMESTAMP))
                .map(dateString -> ZonedDateTime.parse(dateString,
                ISO_DATE_TIME));
    }

    /**
     * Determine whether or not the library was build against locally modified
     * sources, or if you can trust that building the git commit hash indicated
     * by this metadata will get you the same bits (assuming other libraries are
     * also the same bits).
     *
     * @return True if the repo was definitely clean at build time.
     */
    public boolean isCleanRepository()
    {
        Map<String, String> map = buildProperties == null
                                  ? additionalProperties
                                  : buildProperties;
        return "true".equals(map.get(KEY_GIT_REPO_CLEAN));
    }

    /**
     * Retrieves the properties in the /project.properties resource, similar to
     * this:
     *
     * <pre>
     * project-name        = KivaKit - Application
     * project-version     = 1.3.5
     * project-group-id    = com.telenav.kivakit
     * project-artifact-id = kivakit-application
     * </pre>
     * <p>
     * This properties file should be generated by the maven build.
     *
     * @return The contents of the maven metadata file
     */
    public synchronized Map<String, String> projectProperties()
    {
        if (projectProperties == null || projectProperties.isEmpty())
        {
            projectProperties = properties(metadata(type,
                    "/" + type.getSimpleName() + "-project.properties"));
        }

        return projectProperties;
    }

    /**
     * Get the short 7-character version of the git commit hash, which git
     * itself emits in some cases.
     *
     * @return A short commit hash if present
     */
    public Optional<String> shortGitCommitHash()
    {
        return gitCommitHash().map(hash -> hash.substring(0, 7));
    }

    /**
     * Returns the contents of the metadata resource at the given path
     */
    private static String metadata(Class<?> project, String path)
    {
        try (InputStream input = project.getResourceAsStream(path))
        {
            if (input != null)
            {
                return new BufferedReader(new InputStreamReader(input))
                        .lines()
                        .collect(Collectors.joining("\n"))
                        .trim();
            }
            throw new IllegalStateException("No metadata found relative to "
                    + project.getName() + " at " + path + " using classloader "
                    + project.getClassLoader()
                    + " (" + project.getClassLoader().getClass().getName()
                    + ")");
        }
        catch (Exception cause)
        {
            throw new IllegalStateException("Unable to read: " + path, cause);
        }
    }

    LocalDate currentBuildDate()
    {
        return gitCommitTimestamp()
                .filter(zdt -> isCleanRepository())
                .map(ZonedDateTime::toLocalDate)
                .orElse(todaysLocalDate());
    }
}
