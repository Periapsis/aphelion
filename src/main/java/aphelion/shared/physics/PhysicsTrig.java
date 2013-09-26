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
package aphelion.shared.physics;
import static aphelion.shared.physics.PhysicsEnvironment.ROTATION_POINTS;
import static aphelion.shared.physics.PhysicsEnvironment.ROTATION_1_2TH;
import static aphelion.shared.physics.PhysicsEnvironment.ROTATION_1_4TH;
import aphelion.shared.swissarmyknife.ThreadSafe;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

/**
 *
 * @author Joris
 */
public class PhysicsTrig
{
        public static final int MAX_VALUE = 1 << 30;

        /** Calculates the sine of a rotation angle.
         * @param angle An angle in which ROTATION_POINTS = pi*2
         * @return A value between -2^30 and 2^30 (1073741824)
         */
        @ThreadSafe
        public static int sin(int angle)
        {
                return sin((long) angle);
        }
        
        /** Calculates the cosine of a rotation angle.
         * @param angle An angle in which ROTATION_POINTS = pi*2
         * @return A value between -2^30 and 2^30 (1073741824)
         */
        @ThreadSafe
        public static int cos(int angle)
        {
                return sin((long) angle + ROTATION_1_4TH);
        }
        
        /** Calculates the tangent of a rotation angle.
         * @param angle An angle in which ROTATION_POINTS = pi*2
         * @return A value between -2^30 and 2^30 (1073741824)
         */
        public static int tan(int angle)
        {
                return sin(angle) / cos(angle);
        }
        
        /** Calculates the cotangent of a rotation angle.
         * @param angle An angle in which ROTATION_POINTS = pi*2
         * @return A value between -2^30 and 2^30 (1073741824)
         */
        public static int cotan(int angle)
        {
                return cos(angle) / sin(angle);
        }
        
        private static int sin(long angle)
        {
                assert ROTATION_POINTS * 4L > Integer.MAX_VALUE;
                angle += ROTATION_POINTS * 4L; // instead of abs()
                angle %= ROTATION_POINTS;
                
                
                long i = angle / LOOKUP_GAP;
                long i_mod =  angle - LOOKUP_GAP * i;
                int y1 = LOOKUP_TABLE[(int) i];
                int y2 = LOOKUP_TABLE[(int)i+1];
                
                return (int) (y1 + (y2 - y1) *  i_mod / LOOKUP_GAP);
        }
        
        private static final int LOOKUP_SIZE = 65520; // about 256 KiB
        private static final long LOOKUP_GAP = ROTATION_POINTS / LOOKUP_SIZE;
        private static int[] LOOKUP_TABLE = new int[LOOKUP_SIZE+1];
        
        static
        {
                assert ROTATION_POINTS % LOOKUP_SIZE == 0;
                load();
        }
        
        private static void load()
        {
                try
                {
                        InputStream in = PhysicsTrig.class.getResourceAsStream("/aphelion/shared/physics/trig-lookup.bin");
                        if (in == null)
                        {
                                throw new Error("Lookup file missing");
                        }
                        
                        BufferedInputStream b = new BufferedInputStream(in);

                        int i = 0;
                        ByteBuffer entry = ByteBuffer.allocate(4);

                        while (true)
                        {
                                entry.clear();
                                int read = b.read(entry.array());
                                if (read < 0)
                                {
                                        break;
                                }

                                if (read < 4)
                                {
                                        throw new Error("Invalid lookup file");
                                }

                                LOOKUP_TABLE[i] = entry.getInt();

                                ++i;
                        }

                        if (i != LOOKUP_SIZE+1)
                        {
                                throw new Error("Invalid amount of entries in look up file. Expected: " + (LOOKUP_SIZE+1) + "; found: "+i+";");
                        }

                        b.close();
                        in.close();
                
                }
                catch(IOException ex)
                {
                        throw new Error(ex);
                }
        }
        
        /**
         * @param angle An angle in which ROTATION_POINTS = pi/2
         * @return A value between -2^30 and 2^30 (1073741824)
         */
        private static strictfp int phySin(double angle)
        {
                double s = StrictMath.sin(angle / ROTATION_1_2TH * Math.PI);
                s *= MAX_VALUE;
                return (int) s;
                
                // Why not use always use this method?:
                // It should be possible to support other platforms than java,
                // which may not support fdlibm. This way all they have to do 
                // is load trig-lookup.bin
        }
        
        private static void generate() throws IOException
        {
                BufferedOutputStream b = new BufferedOutputStream(new FileOutputStream("trig-lookup.bin"));
                
                ByteBuffer entry = ByteBuffer.allocate(4);
                for (int i = 0; i < LOOKUP_SIZE+1; ++i)
                {
                        int r = phySin(i * LOOKUP_GAP);
                        entry.clear();
                        entry.putInt(r);
                        b.write(entry.array());
                }
                
                b.close();
                System.out.println("done");
        }
        
        private static String test(int x)
        {
                return sin(x) + "\t" + phySin(x) + "\n";
        }
        
        public static void main(String[] args) throws Exception
        {
                /** /
                FileWriter b = new FileWriter("bla.csv");
                for (int x = -ROTATION_POINTS*2; x <= ROTATION_POINTS*2; x += 4000)
                {
                        b.write(test(x));
                }
                b.close();
                /**/
                
		//generate();

        }
        

}