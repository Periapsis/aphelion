/*
 * Aphelion
 * Copyright (c) 2014  Joris van der Wel
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
 * different from the original version
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
 * If you modify this library, you may extend this exception to your version
 * of the library, but you are not obliged to do so. If you do not wish to do
 * so, delete this exception statement from your version.
 */

package aphelion.shared.physics;

import aphelion.shared.gameconfig.*;
import aphelion.shared.net.protobuf.GameOperation;
import aphelion.shared.physics.entities.*;
import aphelion.shared.physics.events.pub.EventPublic;
import aphelion.shared.physics.valueobjects.PhysicsMovement;
import aphelion.shared.resource.ResourceDB;
import aphelion.shared.swissarmyknife.ThreadSafe;
import java.util.Iterator;
import java.util.List;

/** .
 * Using this interface you can only get data about state 0.
 * This is because implementors might decide to do other states in threads.
 * Implementations of this interface might expose ways to access other states.
 * @author Joris
 */
public interface PhysicsEnvironment
{
        /** Returns the primary environment configuration used.
         * In case multiple inner environment are used, the returned
         * value represents the config of the environment that has the final say
         * in inconsistencies (the one other environments reset to)
         * @return This value never changes
         */
        public EnvironmentConf getConfig();
        
        /** Used to sync up the internal tick count with the server tick count.
         * @param tick
         * @throws IllegalStateException If this environment has already began ticking
         */
        public void skipForward(long tick);

        /** The number of times tick() has been called.
         * Tick values are sent over the network in order to synchronize events.
         *
         * @return
         */
        public long getTick();
        
        /** The System.nanoTime() at which the current tick has started.
         *
         * @return nano time
         */
        public long getTickedAt();

        /** Make sure this is called in such a way ticks are synchronized between peers */
        public void tick();
        
         /**
         * Look up an actor by pid. The returned value is a wrapper (PhysicsActor) around the real actor class
         * (PhysicsActorPrivate). This wrapper is unaffected by things such as temporary removal of the actor.
         * @param pid
         * @return  
         */
        public ActorPublic getActor(int pid);
        
        /**
         * Look up an actor by pid. The returned value is a wrapper (PhysicsActor) around the real actor class
         * (PhysicsActorPrivate). This wrapper is unaffected by things such as temporary removal of the actor.
         *
         * @param pid 
         * @param nofail if set, an actor wrapper is always returned, even if the actor does not exist at the moment.
         * This lets you get a wrapper before the actual actor creation operation has been executed (which may take a
         * while, depending on the timestamp).
         * @return  
         */
        public ActorPublic getActor(int pid, boolean nofail);
        
        
        public Iterator<ActorPublic> actorIterator();
        public Iterable<ActorPublic> actorIterable();
        
        /** Return the actor count, including those who have been soft deleted.
         * This is equal to the number of actors iterated by actorIterator()
         * @return  
         */
        public int getActorCount();
        
        
        public Iterator<ProjectilePublic> projectileIterator();
        
        public Iterable<ProjectilePublic> projectileIterable();
        
        /** Return the projectile count, including those who have been soft deleted.
         * This is equal to the number of projectiles iterated by projectileIterator()
         * This method is O(n) time and primarily intended for test cases.
         * @return  
         */
        public int calculateProjectileCount();
        
        public ConfigSelection newConfigSelection();
        
        @ThreadSafe
        public void loadConfig(long tick, String fileIdentifier, List yamlDocuments);
        
        @ThreadSafe
        public void unloadConfig(long tick, String fileIdentifier);
        
        @ThreadSafe
        public void actorNew(long tick, int pid, long seed, String ship);
        
        @ThreadSafe
        public void actorSync(GameOperation.ActorSync sync);
        
        @ThreadSafe
        public void actorModification(long tick, int pid, String ship);

        @ThreadSafe
        public void actorRemove(long tick, int pid);

         /** .
         * 
         * @return false if the operation was too old and has been ignored
         */
        @ThreadSafe
        public boolean actorWarp(long tick, int pid, boolean hint, int x, int y, int x_vel, int y_vel, int rotation);

        /** .
         * 
         * @return false if the operation was too old and has been ignored
         */
        @ThreadSafe
        public boolean actorWarp(
                long tick, int pid, 
                boolean hint, 
                int x, int y, int x_vel, int y_vel, int rotation, 
                boolean has_x, boolean has_y, boolean has_x_vel, boolean has_y_vel, boolean has_rotation);
        
        /** .
         * 
         * @return false if the operation was too old and has been ignored
         */
        @ThreadSafe
        public boolean actorMove(long tick, int pid, PhysicsMovement move);
        
        @ThreadSafe
        public boolean actorWeapon(
                long tick, int pid, WEAPON_SLOT weapon_slot,
                boolean hint_set, 
                int hint_x, int hint_y, 
                int hint_x_vel, int hint_y_vel, 
                int hint_snapped_rotation);
        
        @ThreadSafe
        public boolean actorWeapon(long tick, int pid, WEAPON_SLOT weapon_slot);
        
        @ThreadSafe
        public boolean weaponSync(long tick, 
                int owner_pid, 
                String weaponKey, 
                GameOperation.WeaponSync.Projectile[] projectiles,
                long syncKey);
        
        

        /**
         * @return the map
         */
        public PhysicsMap getMap();
                
        /** Iterate over all events that have not yet been discarded.
         * An event may be discarded if it has been executed in every state.
         * (see Event.isOld())
         * @return 
         */
        public Iterator<EventPublic> eventIterator();
        
        /** Iterate over all events that have not yet been discarded.
         * An event may be discarded if it has been executed in every state.
         * (see Event.isOld())
         * @return 
         */
        public Iterable<EventPublic> eventIterable();
        
        public GCInteger getGlobalConfigInteger(String name);
        public GCString getGlobalConfigString(String name);
        public GCBoolean getGlobalConfigBoolean(String name);
        public GCIntegerList getGlobalConfigIntegerList(String name);
        public GCStringList getGlobalConfigStringList(String name);
        public GCBooleanList getGlobalConfigBooleanList(String name);
        public GCImage getGlobalConfigImage(String name, ResourceDB db);
}
