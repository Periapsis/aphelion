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
package aphelion.client.graphics.world;

import aphelion.client.graphics.screen.Camera;
import aphelion.client.RENDER_LAYER;
import aphelion.shared.resource.ResourceDB;
import aphelion.shared.swissarmyknife.LinkedListEntry;
import aphelion.shared.swissarmyknife.LinkedListHead;

/**
 * Reusable list of animations. 
 * When an animation is done, it is added back to thist list
 * Useful for things like engine trails.
 * @param <T> 
 * @author Joris
 */
public class ReusableAnimationList<T extends MapAnimation>
{
        private LinkedListHead<T> animations = new LinkedListHead<>();
        private final ResourceDB db;
        private final Animator animator;
        private final Factory<T> factory;

        public ReusableAnimationList(ResourceDB db, Animator animator, Factory<T> factory)
        {
                this.db = db;
                this.animator = animator;
                this.factory = factory;
        }
        
        public T register(RENDER_LAYER layer, Camera camera)
        {
                for (LinkedListEntry<T> entry = animations.first; entry != null; entry = entry.next)
                {
                        if (entry.data.isAnimating())
                        {
                                continue;
                        }
                        
                        // move to the end of the list because we are going to use it
                        entry.remove();
                        animations.append(entry);
                        entry.data.reset();
                        animator.addAnimation(layer, entry.data, camera);
                        return entry.data;
                }
                
                T anim = factory.create(db);
                animations.appendData(anim);
                animator.addAnimation(layer, anim, camera);
                return anim;
        }
        
        public static interface Factory<T>
        {
                T create(ResourceDB db);
        }
}
