/*
 * Aphelion
 * Copyright (c) 2013  Joris van der Wel
 * 
 * This file is part of Aphelion
 * 
 * Aphelion is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, version 3 of the License.
 * 
 * Aphelion is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU Affero General Public License
 * along with Aphelion.  If not, see <http://www.gnu.org/licenses/>.
 * 
 * In addition, the following supplemental terms apply, based on section 7 of
 * the GNU Affero General Public License (version 3):
 * a) Preservation of all legal notices and author attributions
 * b) Prohibition of misrepresentation of the origin of this material, and
 * modified versions are required to be marked in reasonable ways as
 * different from the original version (for example by appending a copyright notice).
 * 
 * Linking this library statically or dynamically with other modules is making a
 * combined work based on this library. Thus, the terms and conditions of the
 * GNU Affero General Public License cover the whole combination.
 * 
 * As a special exception, the copyright holders of this library give you 
 * permission to link this library with independent modules to produce an 
 * executable, regardless of the license terms of these independent modules,
 * and to copy and distribute the resulting executable under terms of your 
 * choice, provided that you also meet, for each linked independent module,
 * the terms and conditions of the license of that module. An independent
 * module is a module which is not derived from or based on this library.
 */
package aphelion.shared.swissarmyknife;

import de.lessvoid.nifty.elements.Element;
import de.lessvoid.nifty.screen.Screen;
import java.io.*;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedList;
import java.util.Map;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.newdawn.slick.Animation;
import org.newdawn.slick.Image;
import org.newdawn.slick.SpriteSheet;

/**
 *
 * @author Joris
 */
public final class SwissArmyKnife
{
        public static Random random = new Random();
        public static final boolean assertEnabled;
        public static final Pattern validNickname = Pattern.compile("^[a-zA-Z][a-zA-Z0-9\\-\\[\\]\\\\`^{}_ ]*$");
        
        static
        {
                boolean hasAssert;
                try {
                        assert false;
                        hasAssert = false;
                } catch (AssertionError e) {
                        hasAssert = true;
                }
                
                assertEnabled = hasAssert;
        }

        private SwissArmyKnife()
        {
        }
        
        private static long randomishSeed = System.nanoTime();
        
        
        
        @ThreadSafe
        public static long fastRandomIsh()
        {
                // for stuff that needs random bits fast, but does not care
                // about generating good random numbers.
                // https://dmurphy747.wordpress.com/2011/03/23/xorshift-vs-random-performance-in-java/
                randomishSeed ^= (randomishSeed << 21); 
                randomishSeed ^= (randomishSeed >>> 35);
                randomishSeed ^= (randomishSeed << 4);
                return randomishSeed;
        }
        
        @ThreadSafe
        public static boolean isValidNickname(@Nullable CharSequence nick)
        {
                if (nick == null) { return false; }
                // Pattern is safe to be shared by threads (Matcher is not)
                return validNickname.matcher(nick).matches();
        }
        
        /** Compares two nicknames to each other, ignoring the case.
         * @param x 
         * @param y 
         * @return Same kind of value as left.compareTo(right)
         */
        public static int nicknameCompare(@Nullable String x, @Nullable String y)
        {
                if (x == null)
                {
                        if (y == null)
                        {
                                return 0;
                        }
                        
                        return -1;
                }
                
                if (y == null)
                {
                        return 1;
                }
                
                int len1 = x.length();
                int len2 = y.length();
                int lim = Math.min(len1, len2);

                int k = 0;
                while (k < lim)
                {
                        char c1 = x.charAt(k);
                        char c2 = y.charAt(k);
                        
                             if (c1 ==  '[') c1 = '{';
                        else if (c1 ==  ']') c1 = '}';
                        else if (c1 == '\\') c1 = '|';
                        else if (c1 ==  ' ') c1 = '_';
                        else if (c1 >= 'A' && c1 <= 'Z') c1 += 'a' - 'A';
                        
                             if (c2 ==  '[') c2 = '{';
                        else if (c2 ==  ']') c2 = '}';
                        else if (c2 == '\\') c2 = '|';
                        else if (c2 ==  ' ') c2 = '_';
                        else if (c2 >= 'A' && c2 <= 'Z') c2 += 'a' - 'A';
                        
                        if (c1 != c2)
                        {
                                return c1 - c2;
                        }
                        
                        k++;
                }
                
                return len1 - len2;
        }

        @ThreadSafe
        public static float ceil(float a)
        {
                return floorOrCeil(a, -0.0f, 1.0f, 1.0f);
        }

        @ThreadSafe
        public static float floor(float a)
        {
                return floorOrCeil(a, -1.0f, 0.0f, -1.0f);
        }

        @ThreadSafe
        public static int clip(int n, int min, int max)
        {
                if (n < min)
                {
                        return min;
                }

                if (n > max)
                {
                        return max;
                }

                return n;
        }

        @ThreadSafe
        public static long clip(long n, long min, long max)
        {
                if (n < min)
                {
                        return min;
                }

                if (n > max)
                {
                        return max;
                }

                return n;
        }

        @ThreadSafe
        public static double clip(double n, double min, double max)
        {
                if (n < min)
                {
                        return min;
                }

                if (n > max)
                {
                        return max;
                }

                return n;
        }

        @ThreadSafe
        public static float clip(float n, float min, float max)
        {
                if (n < min)
                {
                        return min;
                }

                if (n > max)
                {
                        return max;
                }

                return n;
        }
        private static final int FLOAT_SIGNIF_BIT_MASK = 8388607;

        /**
         * @author unascribed
         * @author Joseph D. Darcy
         * @author Joris
         * @version %I%, %G%
         * @since 1.3
         */
        private static float floorOrCeil(float a,
                float negativeBoundary,
                float positiveBoundary,
                float sign)
        {
                /* Copyright (c) 2006, Oracle and/or its affiliates. All rights reserved.
                 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms. */

                // Based on StrictMath
                int exponent = Math.getExponent(a);

                if (exponent < 0)
                {
                        /*
                         * Absolute value of argument is less than 1.
                         * floorOrceil(-0.0) => -0.0
                         * floorOrceil(+0.0) => +0.0
                         */
                        return ((a == 0.0) ? a
                                : ((a < 0.0) ? negativeBoundary : positiveBoundary));
                }
                else if (exponent >= 52)
                {
                        /*
                         * Infinity, NaN, or a value so large it must be integral.
                         */
                        return a;
                }
                // Else the argument is either an integral value already XOR it
                // has to be rounded to one.
                assert exponent >= 0 && exponent <= 51;

                int doppel = Float.floatToRawIntBits(a);
                int mask = FLOAT_SIGNIF_BIT_MASK >> exponent;

                if ((mask & doppel) == 0L)
                {
                        return a; // integral value
                }
                else
                {
                        float result = Float.intBitsToFloat(doppel & (~mask));
                        if (sign * a > 0.0)
                        {
                                result = result + sign;
                        }
                        return result;
                }
        }

        @ThreadSafe
        public static int unsigned(byte b)
        {
                return b & 0xFF;
        }

        @ThreadSafe
        public static long safeAddClipped(long a, long b)
        {
                if (a > 0 && b > 0 && Long.MAX_VALUE - b < a)
                {
                        return Long.MAX_VALUE;
                }

                if (a < 0 && b < 0 && Long.MIN_VALUE - b > a)
                {
                        return Long.MIN_VALUE;
                }

                return a + b;
        }

        @ThreadSafe
        public static int safeAddClipped(int a, int b)
        {
                if (a > 0 && b > 0 && Integer.MAX_VALUE - b < a)
                {
                        return Integer.MAX_VALUE;
                }

                if (a < 0 && b < 0 && Integer.MIN_VALUE - b > a)
                {
                        return Integer.MIN_VALUE;
                }

                return a + b;
        }

        @ThreadSafe
        public static long safeSubClipped(long a, long b)
        {
                if (a > 0 && b < 0 && Long.MIN_VALUE + b < a)
                {
                        return Long.MAX_VALUE;
                }

                if (a < 0 && b > 0 && Long.MIN_VALUE + b > a)
                {
                        return Long.MIN_VALUE;
                }

                return a - b;
        }

        @ThreadSafe
        public static int safeSubClipped(int a, int b)
        {
                if (a > 0 && b < 0 && Integer.MIN_VALUE + b < a)
                {
                        return Integer.MAX_VALUE;
                }

                if (a < 0 && b > 0 && Integer.MIN_VALUE + b > a)
                {
                        return Integer.MIN_VALUE;
                }

                return a - b;
        }

        @ThreadSafe
        public static long safeMultiplyClipped(long a, long b)
        {
                if (a > 0 && b > 0)
                {
                        if (a > Long.MAX_VALUE / b)
                        {
                                return Long.MAX_VALUE;
                        }
                }
                else if (a < 0 && b < 0)
                {
                        if (a < Long.MAX_VALUE / b)
                        {
                                return Long.MAX_VALUE;
                        }
                }
                else if (a < 0 && b > 0)
                {
                        if (a < Long.MIN_VALUE / b)
                        {
                                return Long.MIN_VALUE;
                        }
                }
                else if (a > 0 && b < 0)
                {
                        if (a > Long.MIN_VALUE / b)
                        {
                                return Long.MIN_VALUE;
                        }
                }

                return a * b;
        }
        
        public static boolean isMultiplySafe(long a, long b)
        {
                if (a > 0 && b > 0)
                {
                        if (a > Long.MAX_VALUE / b)
                        {
                                return false;
                        }
                }
                else if (a < 0 && b < 0)
                {
                        if (a < Long.MAX_VALUE / b)
                        {
                                return false;
                        }
                }
                else if (a < 0 && b > 0)
                {
                        if (a < Long.MIN_VALUE / b)
                        {
                                return false;
                        }
                }
                else if (a > 0 && b < 0)
                {
                        if (a > Long.MIN_VALUE / b)
                        {
                                return false;
                        }
                }
                
                return true;
        }

        @ThreadSafe
        public static int safeMultiplyClipped(int a, int b)
        {
                if (a > 0 && b > 0)
                {
                        if (a > Integer.MAX_VALUE / b)
                        {
                                return Integer.MAX_VALUE;
                        }
                }
                else if (a < 0 && b < 0)
                {
                        if (a < Integer.MAX_VALUE / b)
                        {
                                return Integer.MAX_VALUE;
                        }
                }
                else if (a < 0 && b > 0)
                {
                        if (a < Integer.MIN_VALUE / b)
                        {
                                return Integer.MIN_VALUE;
                        }
                }
                else if (a > 0 && b < 0)
                {
                        if (a > Integer.MIN_VALUE / b)
                        {
                                return Integer.MIN_VALUE;
                        }
                }

                return a * b;
        }
        
        public static boolean isMultiplySafe(int a, int b)
        {
                if (a > 0 && b > 0)
                {
                        if (a > Integer.MAX_VALUE / b)
                        {
                                return false;
                        }
                }
                else if (a < 0 && b < 0)
                {
                        if (a < Integer.MAX_VALUE / b)
                        {
                                return false;
                        }
                }
                else if (a < 0 && b > 0)
                {
                        if (a < Integer.MIN_VALUE / b)
                        {
                                return false;
                        }
                }
                else if (a > 0 && b < 0)
                {
                        if (a > Integer.MIN_VALUE / b)
                        {
                                return false;
                        }
                }
                
                return true;
        }
        
        @ThreadSafe
        public static long safeDivideClipped(long a, long b)
        {
                // note: exception when dividing by zero!
                if ((a == Long.MIN_VALUE) && (b == -1))
                {
                        return Integer.MAX_VALUE;
                }

                return a - b;
        }
        
        @ThreadSafe
        public static int safeDivideClipped(int a, int b)
        {
                // note: exception when dividing by zero!
                if ((a == Integer.MIN_VALUE) && (b == -1))
                {
                        return Integer.MAX_VALUE;
                }

                return a - b;
        }
        
        @ThreadSafe
        public static int safeNegateClipped(int a)
        {
                if (a == Integer.MIN_VALUE)
                {
                        return Integer.MAX_VALUE;
                }
                
                return -a;
        }
        
        @ThreadSafe
        public static long safeNegateClipped(long a)
        {
                if (a == Long.MIN_VALUE)
                {
                        return Long.MAX_VALUE;
                }
                
                return -a;
        }

        @ThreadSafe
        public static long divideFloor(long a, long b)
        {
                long d;

                d = a / b;

                if (d >= 0)
                {
                        return d;
                }

                // could have used modulo instead ( % ), multiplication is probably faster
                if (d * b == a)
                {
                        return d;
                }

                return d - 1;
        }

        @ThreadSafe
        public static int divideFloor(int a, int b)
        {
                int d;

                d = a / b;

                if (d >= 0)
                {
                        return d;
                }

                if (d * b == a)
                {
                        return d;
                }

                return d - 1;
        }

        @ThreadSafe
        public static int divideCeil(int a, int b)
        {
                int d;

                d = a / b;

                if (d <= 0)
                {
                        return d;
                }

                if (d * b == a)
                {
                        return d;
                }

                return d + 1;
        }

        @ThreadSafe
        public static long divideCeil(long a, long b)
        {
                long d;

                d = a / b;

                if (d <= 0)
                {
                        return d;
                }

                if (d * b == a)
                {
                        return d;
                }

                return d + 1;
        }
        
        /** Divides by rounding up. In such a way that:
         * d = a / b
         * d = (d < 0) ? floor(d) : ceil(d)
         * 
         */
        @ThreadSafe
        public static int divideUp(int a, int b)
        {
                int d;
                
                d = a / b;
                
                if (d * b == a)
                {
                        return d;
                }
                
                if (a < 0 != b < 0) // a xor b is negative
                {
                        return d - 1;
                }
                
                return d + 1;
        }
        
        /** Divides by rounding up. In such a way that:
         * d = a / b
         * d = (d < 0) ? floor(d) : ceil(d)
         * 
         */
        @ThreadSafe
        public static long divideUp(long a, long b)
        {
                long d;
                
                d = a / b;
                
                if (d * b == a)
                {
                        return d;
                }
                
                if (a < 0 != b < 0) // a xor b is negative
                {
                        return d - 1;
                }
                
                return d + 1;
        }

        @ThreadSafe
        public static int abs(int a)
        {
                // Math.abs may return a negative value (-2^31)

                if (a == Integer.MIN_VALUE)
                {
                        return Integer.MAX_VALUE;
                }

                return Math.abs(a);
        }

        @ThreadSafe
        public static long abs(long a)
        {
                if (a == Long.MIN_VALUE)
                {
                        return Long.MAX_VALUE;
                }

                return Math.abs(a);
        }

        @ThreadSafe
        public static int max(int... args)
        {
                int highest = Integer.MIN_VALUE;
                for (int n : args)
                {
                        if (n > highest)
                        {
                                highest = n;
                        }
                }

                return highest;
        }

        @ThreadSafe
        public static int min(int... args)
        {
                int lowest = Integer.MAX_VALUE;
                for (int n : args)
                {
                        if (n < lowest)
                        {
                                lowest = n;
                        }
                }

                return lowest;
        }
        

        /** Integer Hash Function.
         * Robert Jenkins' 96 bit Mix Function.
         * http://www.concentric.net/~ttwang/tech/inthash.htm
         */
        @ThreadSafe
        public static int jenkinMix(int a, int b, int c)
        {
                a = a - b;
                a = a - c;
                a = a ^ (c >>> 13);
                b = b - c;
                b = b - a;
                b = b ^ (a << 8);
                c = c - a;
                c = c - b;
                c = c ^ (b >>> 13);
                a = a - b;
                a = a - c;
                a = a ^ (c >>> 12);
                b = b - c;
                b = b - a;
                b = b ^ (a << 16);
                c = c - a;
                c = c - b;
                c = c ^ (b >>> 5);
                a = a - b;
                a = a - c;
                a = a ^ (c >>> 3);
                b = b - c;
                b = b - a;
                b = b ^ (a << 10);
                c = c - a;
                c = c - b;
                c = c ^ (b >>> 15);
                return c;
        }
        
        @ThreadSafe
        public static @Nonnull String bytesToHex(@Nonnull byte[] arr)
        {
                if (arr == null) { return null; }
                
                StringBuilder ret = new StringBuilder(arr.length * 2);
                
                for (int a = 0; a < arr.length; ++a)
                {
                        String x = Integer.toString(arr[a] & 0xFF, 16);
                        if (x.length() < 2)
                        {
                                ret.append("0");
                        }
                        ret.append(x);
                }
                
                return ret.toString();
        }

        public static @Nonnull byte[] inputStreamToBytes(@Nonnull InputStream input) throws IOException
        {
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                byte buffer[] = new byte[1024 * 4];
                int n = 0;
                while (-1 != (n = input.read(buffer)))
                {
                        output.write(buffer, 0, n);
                }

                return output.toByteArray();
        }
        
        public static long unsignedDivision(long unsignedA, long unsignedB) throws ArithmeticException
        {
                // taken from ivmaidns (Ivan Maidanski)
                long unsignedRes = 0L;
                if (unsignedA >= 0L)
                {
                        if (unsignedB >= 0L)
                        {
                                unsignedRes = unsignedA / unsignedB;
                        }
                }
                else if (unsignedB >= 0L && (unsignedA -= (unsignedRes =
                        ((unsignedA >>> 1) / unsignedB) << 1)
                        * unsignedB) < 0L || unsignedA >= unsignedB)
                {
                        unsignedRes++;
                }
                return unsignedRes;
        }
        
        public static int unsignedDivision(int unsignedA, int unsignedB) throws ArithmeticException
        {
                // taken from ivmaidns (Ivan Maidanski)
                int unsignedRes = 0;
                if (unsignedA >= 0)
                {
                        if (unsignedB >= 0)
                        {
                                unsignedRes = unsignedA / unsignedB;
                        }
                }
                else if (unsignedB >= 0 && (unsignedA -= (unsignedRes =
                        ((unsignedA >>> 1) / unsignedB) << 1)
                        * unsignedB) < 0 || unsignedA >= unsignedB)
                {
                        unsignedRes++;
                }
                return unsignedRes;
        }
        
        public static @Nonnull Animation spriteToAnimation(@Nonnull Image img, int horizontalTiles, int verticalTiles, int frameDuration)
        {
                SpriteSheet sheet = new SpriteSheet(img, img.getWidth() / horizontalTiles, img.getHeight() / verticalTiles);
                Animation anim = new Animation();

                anim.setAutoUpdate(true);
                for (int y = 0; y < sheet.getVerticalCount(); y++)
                {
                        for (int x = 0; x < sheet.getHorizontalCount(); x++)
                        {
                                anim.addFrame(sheet.getSprite(x, y), frameDuration);
                        }
                }
                
                return anim;
        }
        
        /** Find the next number that is greater than v and is a factor of 2.
         * 
         * @param v An integer between 0 and 2^30 inclusive
         * @return A factor of 2. Or 0 if v is also 0.
         */
        public static int nextHighestPowerOf2(int v)
        {
                // http://graphics.stanford.edu/~seander/bithacks.html#RoundUpPowerOf2
                
                assert v >= 0;
                assert v <= 1073741824;
                
                v--;
                v |= v >> 1;
                v |= v >> 2;
                v |= v >> 4;
                v |= v >> 8;
                v |= v >> 16;
                v++;
                
                // note that 0 returns 0
                
                return v;
        }
        
        /** Find the next number that is greater than v and is a factor of 2.
         * 
         * @param v An integer between 0 and 2^62 inclusive
         * @return A factor of 2. Or 0 if v is also 0.
         */
        public static long nextHighestPowerOf2(long v)
        {
                // http://graphics.stanford.edu/~seander/bithacks.html#RoundUpPowerOf2
                
                assert v >= 0;
                assert v <= 4611686018427387904L;
                
                v--;
                v |= v >> 1;
                v |= v >> 2;
                v |= v >> 4;
                v |= v >> 8;
                v |= v >> 16;
                v |= v >> 32;
                v++;
                
                // note that 0 returns 0
                
                return v;
        }
        
        /** Deterministic hypot() using only integers.
         * 
         * @param x Any 32 bit int value. Except Integer.MIN_VALUE
         * @param y Any 32 bit int value. Except Integer.MIN_VALUE
         * @return The length of the vector [x, y]
         */
        public static long hypot(int x, int y)
        {
                return hypot(x, y, (long) x * x + (long) y * y);
        }
        
        /** Deterministic hypot() using only integers.
         * 
         * @param x Any 32 bit int value. Except Integer.MIN_VALUE
         * @param y Any 32 bit int value. Except Integer.MIN_VALUE
         * @param xySquared x * x + y * y
         * @return The length of the vector [x, y]
         */
        public static long hypot(int x, int y, long xySquared)
        {
                long r;
                long dx = x, dy = y;

                dx = Math.abs(dx);
                dy = Math.abs(dy);
                
                r = (dx > dy) ? (dx + (dy >> 1)) : (dy + (dx >> 1));

                if (r == 0)
                {
                        return r;
                }
                
                r = (xySquared / r + r) >> 1;
                r = (xySquared / r + r) >> 1;
                r = (xySquared / r + r) >> 1;

                return r;
        }
        
        public static boolean isPointInsideRectangle(@Nonnull Point low, @Nonnull Point high, @Nonnull Point point)
        {
                return point.x >= low.x && point.x <= high.x &&
                       point.y >= low.y && point.y <= high.y ;
        }
        public static boolean rectangleIntersectsRectangle(@Nonnull Point low1, @Nonnull Point high1, @Nonnull Point low2, @Nonnull Point high2)
        {
                if (low1.x > high2.x || high1.x < low2.x)
                {
    			return false;
    		}
                
    		if (low1.y > high2.y || high1.y < low2.y)
                {
    			return false;
    		}
                return true;
        }
        
        public static int stringCompare(@Nullable String x, @Nullable String y)
        {
                // with null values
                if (x == null)
                {
                        if (y == null)
                        {
                                return 0;
                        }
                        
                        return -1;
                }
                
                if (y == null)
                {
                        return 1;
                }
                
                return x.compareTo(y);
        }
        
        public static void logTraceOfAllThreads(@Nonnull Logger log)
        {
                if (!log.isLoggable(Level.SEVERE))
                {
                        return;
                }
                
                Map<Thread, StackTraceElement[]> traces = Thread.getAllStackTraces();
                for (Map.Entry<Thread, StackTraceElement[]> e : traces.entrySet())
                {
                        Thread thread = e.getKey();
                        StackTraceElement[] trace = e.getValue();


                        String text = "";

                        for (StackTraceElement stack : trace)
                        {
                                text += stack.toString() + "\n";
                        }

                        log.log(Level.SEVERE, "Thread {0} is alive {1} and daemon {2} and interrupted {3}. Trace: {4} ", 
                                new Object[]
                                {
                                        thread.getName(),
                                        thread.isAlive(),
                                        thread.isDaemon(),
                                        thread.isInterrupted(),
                                        text
                                }
                        );
                }
        }
        
        /** Read a file and calculate its hash.
         * 
         * @param algorithm The MessageDigest algorithm, such as "SHA-256"
         * @param file
         * @return 
         * @throws java.io.IOException File not found or unreadable
         * @throws RuntimeException if no Provider supports a MessageDigestSpi implementation for the specified algorithm.
         */
        public static @Nonnull byte[] fileHash(@Nonnull String algorithm, @Nonnull File file) throws IOException
        {
                MessageDigest md;
                try
                {
                        md =  MessageDigest.getInstance("SHA-256");
                }
                catch (NoSuchAlgorithmException ex)
                {
                        throw new RuntimeException(ex);
                }
                
                
                byte[] buf = new byte[131072];
                
                try(InputStream f = new FileInputStream(file))
                {
                        while (true)
                        {
                                int read = f.read(buf);
                                if (read < 0)
                                {
                                        break;
                                }

                                md.update(buf, 0, read);
                        }
                }
                
                return md.digest();
        }
        
        public static @Nonnull URL websocketURItoHTTP(@Nonnull URI uri) throws MalformedURLException
        {
                String scheme;
                switch (uri.getScheme())
                {
                        case "ws":
                                scheme = "http";
                                break;
                        case "wss":
                                scheme = "https";
                                break;
                        case "http":
                        case "https":
                                scheme = uri.getScheme();
                                break;
                        default:
                                throw new MalformedURLException("Unknown scheme " + uri.getScheme() + " for uri " + uri);
                }
                
                return new URL(
                        scheme + 
                        "://" + 
                        uri.getHost() + 
                        (uri.getPort() > 0 ? ":" + uri.getPort() : "") +
                        uri.getRawPath());
        }
        
        public static @Nonnull Element[] findNiftyElementsByIdPrefix(@Nonnull Screen screen, @Nonnull String elementNamePrefix)
        {
                LinkedList<Element> ret = new LinkedList<>();
                
                Element element = screen.findElementByName(elementNamePrefix);
                
                if (element != null)
                {
                        ret.add(element);
                }
                
                int i = 0;
                while (true)
                {
                        element = screen.findElementByName(elementNamePrefix + "-" + i);
                        ++i;
                        
                        if (element == null)
                        {
                                break;
                        }
                        
                        ret.add(element);
                }
                
                return ret.toArray(new Element[]{});
        }
        
        public static @Nonnull Element[] findNiftyElementsByIdPrefix(@Nonnull Element parent, @Nonnull String elementNamePrefix)
        {
                LinkedList<Element> ret = new LinkedList<>();
                
                Element element = parent.findElementByName(elementNamePrefix);
                
                if (element != null)
                {
                        ret.add(element);
                }
                
                int i = 0;
                while (true)
                {
                        element = parent.findElementByName(elementNamePrefix + "-" + i);
                        ++i;
                        
                        if (element == null)
                        {
                                break;
                        }
                        
                        ret.add(element);
                }
                
                return ret.toArray(new Element[]{});
        }
}
