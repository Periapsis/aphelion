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


import aphelion.shared.physics.operations.pub.ActorWeaponFirePublic;
import aphelion.shared.physics.entities.Actor;
import aphelion.shared.physics.PhysicsEnvironment;
import aphelion.shared.physics.entities.MapEntity;
import aphelion.shared.physics.entities.Projectile;
import aphelion.shared.physics.State;
import aphelion.shared.physics.valueobjects.PhysicsPositionVector;
import aphelion.shared.physics.valueobjects.PhysicsShipPosition;
import aphelion.shared.physics.WEAPON_SLOT;
import aphelion.shared.swissarmyknife.LinkedListEntry;
import aphelion.shared.swissarmyknife.SwissArmyKnife;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Joris
 */
public class ActorWeaponFire extends Operation implements ActorWeaponFirePublic
{
        private static final Logger log = Logger.getLogger("Aphelion.Shared.Physics");
        
        private boolean[] usedHint = new boolean[PhysicsEnvironment.MAX_TRAILING_STATES];
        
        // this list is only kept her temporary.
        // it will be set as an attribute in every projectile.
        private ArrayList<MapEntity[]> crossStateLists; // projectile index -> state id -> map entity

        public WEAPON_SLOT weapon_slot;
        
        
        // These 4 values are only used as a hint.
        public boolean hint_set;
        public int hint_x;
	public int hint_y;
	public int hint_x_vel;
	public int hint_y_vel;
        public int hint_snapped_rot;
        
        // projectile index -> state id -> map entity
        private ArrayList<PhysicsPositionVector[]> fireHistories;
        
        public ActorWeaponFire()
        {
                super(true, PRIORITY.ACTOR_WEAPON_FIRE);
        }
        
        @Override
        public int comparePriority(Operation other_)
        {
                int c = super.comparePriority(other_);
                
                if (c == 0 && other_ instanceof ActorWeaponFire)
                {
                        ActorWeaponFire other = (ActorWeaponFire) other_;
                        
                        return Integer.compare(weapon_slot.id, other.weapon_slot.id);
                }
                
                // Duplicate operations with the same slot id, tick, pid is okay
                // those operations are completely equal to each other and should
                // cause no inconsistencies
                // (even if the hint is different)
                
                return c;
        }
        
        @Override
        public boolean execute(State state, long ticks_late)
        {
                Actor actor;
                Projectile projectile;
                
                actor = state.actors.get(pid);
                
                if (actor == null) // ignore operation
                {
                        return true;
                }
                
                if (!actor.canFireWeapon(weapon_slot, this.tick))
                {
                        return true;
                }
                
                Actor.WeaponConfig config = actor.weaponSlots[weapon_slot.id].config;
                
                // limit to 1024, lots of projectiles is expensive
                int projectile_count = SwissArmyKnife.clip(config.projectiles.get(), 1, 1024); 
                
                
                if (crossStateLists == null)
                {
                        crossStateLists = new ArrayList(projectile_count);
                }
                else
                {
                        crossStateLists.ensureCapacity(projectile_count);
                }
                
                if (fireHistories == null)
                {
                        fireHistories = new ArrayList(projectile_count);
                }
                else
                {
                        fireHistories.ensureCapacity(projectile_count); 
                }
                
                PhysicsShipPosition actorPos = new PhysicsShipPosition();
                
                if (state.allowHints && this.hint_set && !usedHint[state.id])
                {
                        // Only use the hint once per state
                        // The hint might be wrong so a timewarp must be able to resolve it.
                        usedHint[state.id] = true;
                        actorPos.x = this.hint_x;
                        actorPos.y = this.hint_y;
                        actorPos.x_vel = this.hint_x_vel;
                        actorPos.y_vel = this.hint_y_vel;
                        actorPos.rot_snapped = this.hint_snapped_rot;
                        // note: rot is not set!
                }
                else
                {
                        if (!actor.getHistoricPosition(actorPos, this.tick, false))
                        {
                                log.log(Level.WARNING, "History for actor not found!");
                                return true;
                        }
                }
                
                
                
                
                LinkedListEntry<Projectile> coupled_last = null;
                for (int p = 0; p < projectile_count; ++p)
                {
                        MapEntity[] crossStateList;
                        if (p < crossStateLists.size())
                        {
                                crossStateList = crossStateLists.get(p);
                        }
                        else
                        {
                                crossStateList = new MapEntity[PhysicsEnvironment.MAX_TRAILING_STATES];
                                crossStateLists.add(crossStateList);
                                assert p == crossStateLists.size() - 1;
                        }
                        
                        PhysicsPositionVector[] fireHistory;
                        
                        if (p < fireHistories.size())
                        {
                                fireHistory = fireHistories.get(p);
                        }
                        else
                        {
                                fireHistory = new PhysicsPositionVector[PhysicsEnvironment.MAX_TRAILING_STATES];
                                fireHistories.add(fireHistory);
                                assert p == fireHistories.size() - 1;
                                
                                for (int s = 0; s < fireHistory.length; ++s)
                                {
                                        fireHistory[s] = new PhysicsPositionVector();
                                }
                        }                      
                        
                        projectile = new Projectile(
                                state, 
                                crossStateList, 
                                actor, 
                                this.tick, 
                                config,
                                p);
                        
                        if (coupled_last == null)
                        {
                                projectile.coupled.beginCircular();
                        }
                        else
                        {
                                coupled_last.append(projectile.coupled);
                        }
                        coupled_last = projectile.coupled;
                       
                        
                        projectile.initFire(tick, actorPos);
                        
                        fireHistory[state.id].set(projectile.pos);
                        
                        state.projectiles.append(projectile.projectileListLink_state);
                        projectile.owner.projectiles.append(projectile.projectileListLink_actor);
                        
                        
                        projectile.updatePositionHistory(tick);
                        
                        // dead reckon current position so that it is no longer late
                        // the position at the tick of this operation should not be dead reckoned, therefor +1
                        projectile.performDeadReckoning(state.env.getMap(), this.tick + 1, ticks_late);
                }
                
                // this is consistent with reordered weapons thanks to the canFireWeapon() check above
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
                
                actor.energy.addRelativeValue(
                        Actor.ENERGY_SETTER.OTHER.id, 
                        this.tick, 
                        -config.fireEnergy.get() * 1024);
                
                // energy has its own consistency check, no need to store history here
                return true;
        }
        
        @Override
        public void resetExecutionHistory(State state, State resetTo)
        {
                if (fireHistories != null)
                {
                        for (int p = 0; p < fireHistories.size(); ++p)
                        {
                                PhysicsPositionVector[] fireHistory = fireHistories.get(p);
                                fireHistory[state.id].set(fireHistory[resetTo.id]);
                        }
                }
        }

        @Override
        public boolean isConsistent(State older, State newer)
        {
                if (this.tick > older.tick_now)
                {
                        return true; // This operation has not had the chance to execute on both states (yet)
                }
                
                if (fireHistories != null)
                {
                        for (int p = 0; p < fireHistories.size(); ++p)
                        {
                                PhysicsPositionVector[] fireHistory = fireHistories.get(p);

                                if (!fireHistory[older.id].equals(fireHistory[newer.id]))
                                {
                                        return false;
                                }
                        }
                }
                
                return true;
        }

        @Override
        public WEAPON_SLOT getWeaponSlot()
        {
                return this.weapon_slot;
        }

        
        
        
}
