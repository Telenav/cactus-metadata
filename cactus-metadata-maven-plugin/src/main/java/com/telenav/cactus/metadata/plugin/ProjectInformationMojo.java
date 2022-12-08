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

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import static org.apache.maven.plugins.annotations.InstantiationStrategy.PER_LOOKUP;
import static org.apache.maven.plugins.annotations.LifecyclePhase.VERIFY;
import static org.apache.maven.plugins.annotations.ResolutionScope.NONE;

/**
 * A mojo that simply pretty-prints what a build is going to build.
 *
 * @author Tim Boudreau
 * @author jonathanl (shibo)
 */
@SuppressWarnings("unused")
@org.apache.maven.plugins.annotations.Mojo(defaultPhase = VERIFY,
        requiresDependencyResolution = NONE,
        instantiationStrategy = PER_LOOKUP,
        name = "project-information", threadSafe = true)
public class ProjectInformationMojo extends AbstractMojo
{
    private static final Set<String> EMITTED = ConcurrentHashMap.newKeySet();

    /**
     * Provide the verb to prefix the project name with (by default, it's
     * "Building").
     */
    @Parameter(property = "cactus.verb")
    private String verb;

    @Parameter(defaultValue = "${project}", readonly = true)
    private MavenProject project;

    private String verb()
    {
        return verb == null
               ? "Building"
               : capitalize(verb);
    }

    private void emit(MavenProject project)
    {
        String info = generateInfo(project).toString();
        if (EMITTED.add(info))
        {
            emitMessage(info);
        }
    }

    protected void emitMessage(Object message)
    {
        if (message != null)
        {
            for (String line : Objects.toString(message).split("\n"))
            {
                System.out.println("┋ " + line);
            }
        }
    }

    private CharSequence generateInfo(MavenProject project)
    {
        return verb() + " " + project.getName();
    }

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException
    {
        emit(project);
    }

    private String capitalize(String what)
    {
        if (!what.isEmpty() && !Character.isUpperCase(what.charAt(0)))
        {
            char[] c = what.toCharArray();
            c[0] = Character.toUpperCase(c[0]);
            return new String(what);
        }
        return what;
    }

}
