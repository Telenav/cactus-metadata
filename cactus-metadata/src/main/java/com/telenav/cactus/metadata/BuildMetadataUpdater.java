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

import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static com.telenav.cactus.metadata.BuildMetadata.KEY_GIT_COMMIT_HASH;
import static com.telenav.cactus.metadata.BuildMetadata.KEY_GIT_COMMIT_TIMESTAMP;
import static com.telenav.cactus.metadata.BuildMetadata.KEY_GIT_REPO_CLEAN;
import static java.time.format.DateTimeFormatter.ISO_DATE_TIME;
import static java.util.Arrays.copyOfRange;
import static java.util.Collections.emptyMap;

/**
 * This application is run from Maven builds to produce a <i>build.properties</i> file in <i>src/main/java</i>
 * containing the build number, date and name.
 *
 * @author jonathanl (shibo)
 * @see BuildMetadata
 */
public class BuildMetadataUpdater
{
    /**
     * Writes a build.properties file out to the given output file with the following entries:
     *
     * <ul>
     *     <li>build-number - The current build number since the start of the KivaKit epoch</li>
     *     <li>build-date - The current build date as [year].[month].[day-of-month]</li>
     *     <li>build-name - The current build name</li>
     * </ul>
     *
     * <p>
     * Some KivaKit scripts read this information, as well as kivakit-core.
     * </p>
     * <p>
     * Additional properties may be passed in key...value...key...value order, and will
     * be incorporated into the build metadata.  The following are understood and used
     * by this library if present:
     * </p>
     * <ul>
     *     <li>commit-timestamp - the timestamp of the last git commit</li>
     *     <li>commit-long-hash - the long git commit hash of the last git commit</li>
     *     <li>no-local-modifications - whether or not the git checkout a library
     *         was built from contained local modifications.</li>
     * </ul>
     * <p>
     * The build date and build number are determined by the git commit timestamp
     * <i>if the repository the library was built from is unmodified</i>.
     * </p>
     *
     * @param arguments Output folder to write metadata to
     */
    public static void main(String[] arguments)
    {
        if (arguments.length >= 1)
        {
            try
            {
                // Get output path and ensure it exists,
                Path outputPath = Paths.get(arguments[0]);
                if (!Files.isDirectory(outputPath.getParent()))
                {
                    Files.createDirectory(outputPath.getParent());
                }
                Map<String, String> additionalArguments
                        = collectAdditionalArguments(arguments);

                // formulate the lines of the build.properties file,
                Map<String, String> properties = new BuildMetadata(null,
                        BuildMetadata.Type.CURRENT, additionalArguments).buildProperties();
                List<String> lines = new ArrayList<String>();
                for (String key : properties.keySet())
                {
                    lines.add(key + " = " + properties.get(key));
                }

                // and write them to the output folder.
                try (PrintStream out = new PrintStream(outputPath.toFile()))
                {
                    out.println(String.join("\n", lines));
                }
            }
            catch (RuntimeException ex)
            {
                // Don't swallow errors that actually describe what's wrong
                throw ex;
            }
            catch (Exception cause)
            {
                throw new IllegalStateException("Unable to write metadata: "
                        + Arrays.toString(arguments), cause);
            }
        }
        else
        {
            System.err.println("Usage: kivakit-metadata [output-folder] ([key] [value])*");
        }
    }

    private static Map<String, String> collectAdditionalArguments(String[] arguments)
    {
        if (arguments.length > 1)
        {
            Map<String, String> additionalArguments;
            if (arguments.length % 2 == 0)
            {
                throw new IllegalArgumentException(
                        "Following key/value pairs must be balanced,"
                                + " but " + (arguments.length - 1)
                                + " passed: "
                                + Arrays.toString(copyOfRange(arguments,
                                1, arguments.length)));
            }
            additionalArguments = new TreeMap<>();
            for (int i = 1; i < arguments.length; i += 2)
            {
                switch (arguments[i])
                {
                    case KEY_GIT_COMMIT_TIMESTAMP:
                        try
                        {
                            ZonedDateTime.parse(arguments[i + 1], ISO_DATE_TIME);
                        }
                        catch (DateTimeParseException ex)
                        {
                            throw new IllegalArgumentException(arguments[i]
                                    + " must be in ISO 8601 instant format,"
                                    + " but got " + arguments[i + 1]);
                        }
                        break;
                    case KEY_GIT_REPO_CLEAN:
                        switch (arguments[i + 1])
                        {
                            case "true":
                            case "false":
                                break;
                            default:
                                throw new IllegalArgumentException(arguments[i]
                                        + " must be either 'true' or 'false' "
                                        + "but got '" + arguments[i + 1]);
                        }
                        break;
                    case KEY_GIT_COMMIT_HASH:
                        for (int j = 0; j < arguments[i + 1].length(); j++)
                        {
                            char c = arguments[i + 1].charAt(j);
                            if (!(c >= 'a' && c <= 'f') && !(c >= '0' && c <= '9'))
                            {
                                throw new IllegalArgumentException("Valid characters in a git "
                                        + " hash are 0-9 a-f");
                            }
                        }
                }
                additionalArguments.put(arguments[i], arguments[i + 1]);
            }
            return additionalArguments;
        }
        else
        {
            return emptyMap();
        }
    }
}
