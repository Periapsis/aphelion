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

package aphelion.shared.physics.operations;


import aphelion.shared.net.protobuf.GameOperation;
import aphelion.shared.physics.SimpleEnvironment;
import aphelion.shared.physics.operations.pub.WeaponSyncPublic;
import aphelion.shared.physics.entities.Actor;
import aphelion.shared.physics.entities.Projectile;
import aphelion.shared.physics.State;
import aphelion.shared.physics.entities.*;
import java.util.logging.Logger;

/**
 *
 * @author Joris
 */
public class WeaponSync extends Operation implements WeaponSyncPublic
{
        private static final Logger log = Logger.getLogger("aphelion.shared.physics");
        
        private final ProjectileFactory factory = new ProjectileFactory();
        
        
        public String weaponKey;
        public GameOperation.WeaponSync.Projectile[] syncProjectiles;
        public long syncKey;
        
        public WeaponSync(SimpleEnvironment env, OperationKey key)
        {
                super(env, true, PRIORITY.ACTOR_WEAPON_SYNC, key);
        }

        @Override
        public boolean execute(State state, boolean late, long ticks_late)
        {
                Actor actor = state.actors.get(new ActorKey(pid));
                
                if (actor == null) // ignore operation
                {
                        return true;
                }
                
                assert syncKey != 0;
                
                int projectile_count = syncProjectiles.length;
                factory.hintProjectileCount(projectile_count);
                WeaponConfig config = actor.config.getWeaponConfig(weaponKey);
                
                Projectile[] projectiles = factory.constructProjectiles(state, actor, tick, config, projectile_count, null, syncKey);
                assert projectiles.length == projectile_count;
                
                
                for (int p = 0; p < projectile_count; ++p)
                {
                        Projectile projectile = projectiles[p];
                        projectile.initFromSync(syncProjectiles[p], this.tick);
                        
                        if (projectile.expiresAt_tick <= state.tick_now)
                        {
                                projectile.softRemove(tick);
                        }
                        
                        projectile.register();
                        projectile.updatedPosition(tick);
                        
                        // dead reckon current position so that it is no longer late
                        // the position at the tick of this operation should not be dead reckoned, therefor +1
                        projectile.performDeadReckoning(state.env.getMap(), this.tick + 1, ticks_late);
                        
                        if (projectile.isForceEmitter())
                        {
                                // however a force emitter should emit for the current tick also ( <= )
                                for (long t = 0; t <= ticks_late; ++t)
                                {
                                        projectile.emitForce(tick + t);
                                }
                        }
                }
                
                long next = tick + config.fireDelay.get();
                if (next > config.nextWeaponFire_tick)
                {
                        config.nextWeaponFire_tick = next;
                }
                
                actor.lastWeaponFire = config;
                
                next = tick + config.switchDelay.get();
                if (next > actor.nextSwitchedWeaponFire_tick)
                {
                        actor.nextSwitchedWeaponFire_tick = next;
                }
                return true;
        }

        @Override
        public boolean isConsistent(State older, State newer)
        {
                // this operation is only used in such a way a timewarp would not solve anything
                return true;
        }
        
        @Override
        public void resetExecutionHistory(State state, State resetTo, Operation resetToOperation)
        {        
        }
        
        @Override
        public void placedBackOnTodo(State state)
        {
        }
        
        
}
