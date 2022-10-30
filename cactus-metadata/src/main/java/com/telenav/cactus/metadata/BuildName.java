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

import java.time.LocalDate;
import java.time.ZonedDateTime;

/**
 * This class provides memorable names for build numbers through the method {@link #name(int)}. Build numbers are
 * measured in days since the start of the KivaKit epoch on December 5, 2020.
 *
 * @author jonathanl (shibo)
 */
public class BuildName
{
    /** Start of Telenav epoch is December 5, 2020 (blue monkey) */
    public static final int TELENAV_EPOCH_DAY = 18_601;

    private static final String[] nouns = new String[]
            {
                    "monkey", "gorilla", "tornado", "rhino", "rabbit", "dog", "turtle", "goat", "dinosaur",
                    "shark", "snake", "bunny", "marmot", "star", "alpaca", "panda", "hamster",
                    "hedgehog", "kangaroo", "crocodile", "duckling", "hippo", "dolphin", "owl", "seal",
                    "piglet", "penguin", "truck", "sneakers", "dracula", "trebuchet", "chameleon", "lizard",
                    "donkey", "koala", "otter", "cat", "wombat", "beachball", "capybara", "buffalo",
                    "frog", "mouse", "telephone", "laptop", "toaster", "waffle", "bobblehead", "crayon",
                    "sunglasses", "light-bulb", "water-wings", "shoes", "bongos", "goldfish", "legos", "tulips",
                    "dune-buggy", "torpedo", "rocket", "diorama", "beanbag", "radio", "banana"
            };

    private static final String[] adjectives = new String[]
            {
                    "blue", "sparkling", "orange", "puffy", "beryllium", "plutonium", "mango", "cobalt", "purple",
                    "tungsten", "yellow", "happy", "transparent", "pink", "aqua", "lavender", "alabaster", "laughing",
                    "lemon", "tangerine", "golden", "silver", "bronze", "amber", "ruby", "goldenrod", "khaki", "violet",
                    "lime", "steel", "red", "ceramic", "platinum", "carbon", "navy", "stretchy", "nickel", "copper",
                    "funky", "aluminum", "zinc", "chrome", "lead", "radium", "zinc", "iron", "charcoal", "titanium",
                    "angry", "chocolate", "turquoise", "cerulean", "apricot", "green", "maroon", "blasé",
                    "grumpy", "cornflower", "chartreuse", "neon", "mustard", "rubber", "paper", "plastic"
            };


    public static int toBuildNumber(LocalDate date) {
        return (int) (date.toEpochDay() - TELENAV_EPOCH_DAY);
    }

    public static String name(ZonedDateTime when) {
        return name(when.toLocalDate());
    }

    public static String name(LocalDate when) {
        return name(toBuildNumber(when));
    }

    /**
     * Returns the name for the given build number, like "sparkling piglet"
     */
    public static String name(int buildNumber)
    {
        var noun = nouns[buildNumber % nouns.length];
        var adjective = adjectives[(buildNumber / nouns.length) % adjectives.length];
        return adjective + " " + noun;
    }
}
