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
package aphelion.client.graphics;

import java.util.ArrayList;
import org.newdawn.slick.Color;

/**
 *
 * @author Joris
 */
public class AnimatedColour
{
        private final ArrayList<Frame> frames;
        private int currentFrame = 0;
        private long lastUpdate;
        private boolean firstUpdate = true;
        private long nextChange = 0;

        public AnimatedColour(int frames)
        {
                this.frames = new ArrayList<>(frames);
        }
        
        public void addFrame(Color color, int duration)
        {
                long dur = duration * 1_000_000L;
                if (frames.isEmpty())
                {
                        nextChange = dur;
                }
                
                frames.add(new Frame(color, dur));
        }
        
        public Color get()
        {
                if (frames.isEmpty())
                {
			return null;
		}
                
                long now = Graph.nanoTime();
                long delta;
                
                if (firstUpdate)
                {
                        delta = 0;
                        firstUpdate = false;
                }
                else
                {
                        delta = now - lastUpdate;
                }
                
                lastUpdate = now;
                nextFrame(delta);
                
                return frames.get(currentFrame).color;
        }
        
        public void bind()
        {
                Color color = get();
                if (color == null)
                {
                        Color.magenta.bind();
                }
                else
                {
                        color.bind();
                }
        }
        
        private void nextFrame(long delta)
        {
		if (frames.isEmpty())
                {
			return;
		}
		
		nextChange -= delta;
		
		while (nextChange < 0)
                {
			currentFrame = (currentFrame + 1) % frames.size();
			nextChange = nextChange + frames.get(currentFrame).duration;
		}
	}
        
        private static class Frame
        {
                public final Color color;
                public final long duration;

                Frame(Color color, long duration)
                {
                        this.color = color;
                        this.duration = duration;
                        
                        if (duration < 1) { throw new IllegalArgumentException(); }
                }
        }
}
