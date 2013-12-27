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

import aphelion.client.graphics.world.ActorShip;
import aphelion.client.graphics.world.MapEntities;
import aphelion.shared.event.TickEvent;
import aphelion.shared.gameconfig.ConfigSelection;
import aphelion.shared.gameconfig.GameConfig;
import aphelion.shared.net.game.NetworkedActor;
import aphelion.shared.physics.PhysicsEnvironment;
import aphelion.shared.physics.WEAPON_SLOT;
import aphelion.shared.physics.valueobjects.PhysicsMovement;
import aphelion.shared.resource.ResourceDB;
import java.io.InputStream;
import java.util.Iterator;
import java.util.List;
import java.util.TreeSet;

/**
 *
 * @author Joris
 */
public abstract class Scenario implements TickEvent
{
        protected PhysicsEnvironment env;
        protected MapEntities mapEntities;
        private ResourceDB resourceDB;
        protected ConfigSelection config;
        private final TreeSet<Todo> todo = new TreeSet<>();
        
        public static final int ACTOR_LOCAL = 1;
        public static final int ACTOR_2 = 2;
        public static final int ACTOR_3 = 3;

        public Scenario()
        {
        }
        
        protected abstract String getConfig();
        protected abstract void setup();
        
        public final void doSetup(PhysicsEnvironment env, MapEntities mapEntities, ResourceDB resourceDB)
        {
                this.env = env;
                this.mapEntities = mapEntities;
                this.resourceDB = resourceDB;
                
                List<Object> yamlDocuments;
                try
                {
                        // default config
                        
                        InputStream in = Scenario.class.getResourceAsStream("/client/graphics/scenario.game.yaml");
                        assert in != null;
                        yamlDocuments = GameConfig.loadYaml(in);
                }
                catch (Exception ex)
                {
                        throw new Error(ex);
                }
                
                env.loadConfig(env.getTick() - env.econfig.HIGHEST_DELAY, "scenario.game.yaml", yamlDocuments);
                
                String implConfig = this.getConfig();
                if (implConfig != null)
                {
                        try
                        {
                                yamlDocuments = GameConfig.loadYaml(implConfig);
                        }
                        catch (Exception ex)
                        {
                                throw new Error(ex);
                        }
                        
                        env.loadConfig(env.getTick() - env.econfig.HIGHEST_DELAY, "impl", yamlDocuments);
                }
                
                this.config = env.newConfigSelection(0);
                
                setup();
        }
        
        @Override
        public void tick(long unused)
        {
                Iterator<Todo> it = todo.iterator();
                while (it.hasNext())
                {
                        Todo t = it.next();
                        if (t.arriveAt_tick > env.getTick())
                        {
                                break; // done for now
                        }
                        
                        it.remove();
                        t.run();
                }
        }
        
        public final void actorNew(long tick, int pid, long seed, String ship)
        {
                env.actorNew(tick, pid, seed, ship);
                
                ActorShip actorShip = new ActorShip(
                        resourceDB, new NetworkedActor(pid, pid == ACTOR_LOCAL, "Player " + pid), 
                        env.getActor(pid, true), 
                        mapEntities);
                
                mapEntities.addShip(actorShip);
        }
        
        public final void actorWarp(long executeAt_tick, final long tick, final int pid, final boolean hint, final int x, final int y, final int x_vel, final int y_vel, final int rotation)
        {
                if (executeAt_tick <= env.getTick())
                {
                        env.actorWarp(tick, pid, hint, x, y, x_vel, y_vel, rotation);
                        return;
                }
                
                todo.add(new Todo(executeAt_tick)
                {
                        @Override
                        void run()
                        {
                                env.actorWarp(tick, pid, hint, x, y, x_vel, y_vel, rotation);
                        }
                });
        }
        
        public final void actorMove(long executeAt_tick, final long tick, final int pid, final PhysicsMovement move)
        {
                if (executeAt_tick <= env.getTick())
                {
                        env.actorMove(tick, pid, move);
                        
                        RenderDelay renderDelayHandler = mapEntities.getRenderDelay();
                        if (renderDelayHandler != null)
                        {
                                renderDelayHandler.receivedMove(pid, tick);
                        }
                        return;
                }
                
                todo.add(new Todo(executeAt_tick)
                {
                        @Override
                        void run()
                        {
                                env.actorMove(tick, pid, move);
                                
                                RenderDelay renderDelayHandler = mapEntities.getRenderDelay();
                                if (renderDelayHandler != null)
                                {
                                        renderDelayHandler.receivedMove(pid, tick);
                                }
                        }
                });
        }
        
        public final void actorWeapon(
                long executeAt_tick, 
                final long tick, final int pid, final WEAPON_SLOT weapon_slot)
        {
                if (executeAt_tick <= env.getTick())
                {
                        env.actorWeapon(tick, pid, weapon_slot, false, 0, 0, 0, 0, 0);
                        return;
                }
                
                todo.add(new Todo(executeAt_tick)
                {
                        @Override
                        void run()
                        {
                                env.actorWeapon(tick, pid, weapon_slot, false, 0, 0, 0, 0, 0);
                        }
                });
        }
        
        public final void actorWeapon(
                long executeAt_tick, 
                final long tick, final int pid, final WEAPON_SLOT weapon_slot,
                final boolean hint_set, 
                final int hint_x, final int hint_y, 
                final int hint_x_vel, final int hint_y_vel, 
                final int hint_snapped_rotation)
        {
                if (executeAt_tick <= env.getTick())
                {
                        env.actorWeapon(tick, pid, weapon_slot, hint_set, hint_x, hint_y, hint_x_vel, hint_y_vel, hint_snapped_rotation);
                        return;
                }
                
                todo.add(new Todo(executeAt_tick)
                {
                        @Override
                        void run()
                        {
                                env.actorWeapon(tick, pid, weapon_slot, hint_set, hint_x, hint_y, hint_x_vel, hint_y_vel, hint_snapped_rotation);
                        }
                });
        }
        
        private static abstract class Todo implements Comparable<Todo>
        {
                private static long next_seq;
                        
                final long arriveAt_tick;
                private final long seq;

                Todo(long arriveAt_tick)
                {
                        this.arriveAt_tick = arriveAt_tick;
                        seq = ++next_seq;
                }
                
                abstract void run();

                @Override
                public int compareTo(Todo o)
                {
                        int c = Long.compare(arriveAt_tick, o.arriveAt_tick);
                        if (c == 0)
                        {
                                return Long.compare(seq, o.seq);
                        }
                        return c;
                }
        }
}
