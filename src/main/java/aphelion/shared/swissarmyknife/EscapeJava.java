/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package aphelion.shared.swissarmyknife;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.HashMap;
import java.util.Locale;

/**
 * Everything you need to escape java strings.
 * With code taken from the apache commons library
 * (without having to include the whole lib)
 *
 * @author Apache
 * @author Joris
 */
public class EscapeJava
{
        public static String escapeJava(String input)
        {
                return ESCAPE_JAVA.translate(input);
        }

        public static String[][] JAVA_CTRL_CHARS_ESCAPE()
        {
                return JAVA_CTRL_CHARS_ESCAPE.clone();
        }
        private static final String[][] JAVA_CTRL_CHARS_ESCAPE =
        {
                {
                        "\b", "\\b"
                },
                {
                        "\n", "\\n"
                },
                {
                        "\t", "\\t"
                },
                {
                        "\f", "\\f"
                },
                {
                        "\r", "\\r"
                }
        };

        private static abstract class CharSequenceTranslator
        {
                public abstract int translate(CharSequence input, int index, Writer out) throws IOException;

                public final String translate(CharSequence input)
                {
                        if (input == null)
                        {
                                return null;
                        }
                        try
                        {
                                StringWriter writer = new StringWriter(input.length() * 2);
                                translate(input, writer);
                                return writer.toString();
                        }
                        catch (IOException ioe)
                        {
                                // this should never ever happen while writing to a StringWriter
                                throw new RuntimeException(ioe);
                        }
                }

                public final void translate(CharSequence input, Writer out) throws IOException
                {
                        if (out == null)
                        {
                                throw new IllegalArgumentException("The Writer must not be null");
                        }
                        if (input == null)
                        {
                                return;
                        }
                        int pos = 0;
                        int len = input.length();
                        while (pos < len)
                        {
                                int consumed = translate(input, pos, out);
                                if (consumed == 0)
                                {
                                        char[] c = Character.toChars(Character.codePointAt(input, pos));
                                        out.write(c);
                                        pos += c.length;
                                        continue;
                                }
//          // contract with translators is that they have to understand codepoints 
//          // and they just took care of a surrogate pair
                                for (int pt = 0; pt < consumed; pt++)
                                {
                                        pos += Character.charCount(Character.codePointAt(input, pos));
                                }
                        }
                }

                public final CharSequenceTranslator with(CharSequenceTranslator... translators)
                {
                        CharSequenceTranslator[] newArray = new CharSequenceTranslator[translators.length + 1];
                        newArray[0] = this;
                        System.arraycopy(translators, 0, newArray, 1, translators.length);
                        return new AggregateTranslator(newArray);
                }

                public static String hex(int codepoint)
                {
                        return Integer.toHexString(codepoint).toUpperCase(Locale.ENGLISH);
                }
        }

        private static class AggregateTranslator extends CharSequenceTranslator
        {
                private final CharSequenceTranslator[] translators;

                public AggregateTranslator(CharSequenceTranslator... translators)
                {
                        this.translators = translators.clone();
                }

                @Override
                public int translate(CharSequence input, int index, Writer out) throws IOException
                {
                        for (CharSequenceTranslator translator : translators)
                        {
                                int consumed = translator.translate(input, index, out);
                                if (consumed != 0)
                                {
                                        return consumed;
                                }
                        }
                        return 0;
                }
        }

        private static class LookupTranslator extends CharSequenceTranslator
        {
                private final HashMap<CharSequence, CharSequence> lookupMap;
                private final int shortest;
                private final int longest;

                public LookupTranslator(CharSequence[]... lookup)
                {
                        lookupMap = new HashMap<CharSequence, CharSequence>();
                        int _shortest = Integer.MAX_VALUE;
                        int _longest = 0;
                        if (lookup != null)
                        {
                                for (CharSequence[] seq : lookup)
                                {
                                        this.lookupMap.put(seq[0], seq[1]);
                                        int sz = seq[0].length();
                                        if (sz < _shortest)
                                        {
                                                _shortest = sz;
                                        }
                                        if (sz > _longest)
                                        {
                                                _longest = sz;
                                        }
                                }
                        }
                        shortest = _shortest;
                        longest = _longest;
                }

                @Override
                public int translate(CharSequence input, int index, Writer out) throws IOException
                {
                        int max = longest;
                        if (index + longest > input.length())
                        {
                                max = input.length() - index;
                        }
                        // descend so as to get a greedy algorithm
                        for (int i = max; i >= shortest; i--)
                        {
                                CharSequence subSeq = input.subSequence(index, index + i);
                                CharSequence result = lookupMap.get(subSeq);
                                if (result != null)
                                {
                                        out.write(result.toString());
                                        return i;
                                }
                        }
                        return 0;
                }
        }

        private static abstract class CodePointTranslator extends CharSequenceTranslator
        {
                @Override
                public final int translate(CharSequence input, int index, Writer out) throws IOException
                {
                        int codepoint = Character.codePointAt(input, index);
                        boolean consumed = translate(codepoint, out);
                        if (consumed)
                        {
                                return 1;
                        }
                        else
                        {
                                return 0;
                        }
                }

                public abstract boolean translate(int codepoint, Writer out) throws IOException;
        }

        private static class UnicodeEscaper extends CodePointTranslator
        {
                private final int below;
                private final int above;
                private final boolean between;

                public UnicodeEscaper()
                {
                        this(0, Integer.MAX_VALUE, true);
                }

                private UnicodeEscaper(int below, int above, boolean between)
                {
                        this.below = below;
                        this.above = above;
                        this.between = between;
                }

                public static UnicodeEscaper below(int codepoint)
                {
                        return outsideOf(codepoint, Integer.MAX_VALUE);
                }

                public static UnicodeEscaper above(int codepoint)
                {
                        return outsideOf(0, codepoint);
                }

                public static UnicodeEscaper outsideOf(int codepointLow, int codepointHigh)
                {
                        return new UnicodeEscaper(codepointLow, codepointHigh, false);
                }

                public static UnicodeEscaper between(int codepointLow, int codepointHigh)
                {
                        return new UnicodeEscaper(codepointLow, codepointHigh, true);
                }

                @Override
                public boolean translate(int codepoint, Writer out) throws IOException
                {
                        if (between)
                        {
                                if (codepoint < below || codepoint > above)
                                {
                                        return false;
                                }
                        }
                        else
                        {
                                if (codepoint >= below && codepoint <= above)
                                {
                                        return false;
                                }
                        }

                        // TODO: Handle potential + sign per various Unicode escape implementations
                        if (codepoint > 0xffff)
                        {
                                // TODO: Figure out what to do. Output as two Unicodes?
                                //       Does this make this a Java-specific output class?
                                out.write("\\u" + hex(codepoint));
                        }
                        else if (codepoint > 0xfff)
                        {
                                out.write("\\u" + hex(codepoint));
                        }
                        else if (codepoint > 0xff)
                        {
                                out.write("\\u0" + hex(codepoint));
                        }
                        else if (codepoint > 0xf)
                        {
                                out.write("\\u00" + hex(codepoint));
                        }
                        else
                        {
                                out.write("\\u000" + hex(codepoint));
                        }
                        return true;
                }
        }
        public static final CharSequenceTranslator ESCAPE_JAVA =
                new LookupTranslator(
                new String[][]
        {
                {
                        "\"", "\\\""
                },
                {
                        "\\", "\\\\"
                },
        }).with(
                new LookupTranslator(JAVA_CTRL_CHARS_ESCAPE())).with(
                UnicodeEscaper.outsideOf(32, 0x7f));
}