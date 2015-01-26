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
 */

package aphelion.server.game;

import aphelion.shared.event.TickEvent;
import aphelion.shared.net.game.NetworkedActor;
import aphelion.shared.net.protobuf.GameOperation;
import aphelion.shared.net.protobuf.GameS2C;
import aphelion.shared.physics.PhysicsEnvironment;
import aphelion.shared.physics.WEAPON_SLOT;
import aphelion.shared.physics.valueobjects.PhysicsMovement;
import aphelion.shared.swissarmyknife.SwissArmyKnife;
import com.google.protobuf.ByteString;
import java.util.Collections;



/**
 *
 * @author Joris
 */
public class Dummies implements TickEvent
{
        private static final int DUMMIES_PID_START = -10000;
        private static final int GROUPS = 20;
        
        private final PhysicsEnvironment env;
        private final ServerGame serverGame;
        
        private final DummyData[] dummies;
        private final int GROUP_SIZE;
        
        private int index = 0;
        
        

        public Dummies(int DUMMIES, PhysicsEnvironment env, ServerGame serverGame)
        {
                this.env = env;
                this.serverGame = serverGame;
                
                dummies = new DummyData[DUMMIES];
                GROUP_SIZE = (int) Math.ceil(dummies.length / (double) GROUPS);
        }
        
        public void setup()
        {
                for (int p = 0; p < dummies.length; ++p)
                {
                        DummyData dummy = new DummyData(DUMMIES_PID_START + p, "Dummy " + p);
                        dummies[p] = dummy;
                        dummy.spawn();
                }
        }

        @Override
        public void tick(long tick)
        {
                final int end = Math.min(index + GROUP_SIZE, dummies.length);
                
                for (; index < end; ++index)
                {
                        DummyData dummy = dummies[index];
                        if (dummy == null) { continue; }
                        dummy.pickAction();
                        dummy.doAction(GROUP_SIZE);
                }
                
                if (index == dummies.length)
                {
                        index = 0;
                }

                
        }
        
        private class DummyData
        {
                final int pid;
                final String name;
                DUMMY_ACTION action = DUMMY_ACTION.NOTHING;
                PhysicsMovement move = PhysicsMovement.NONE;
                final int FIRE_DELAY = 5;

                DummyData(int pid, String name)
                {
                        this.pid = pid;
                        this.name = name;
                }
                
                void spawn()
                {
                        env.actorNew(env.getTick() + 1, pid, SwissArmyKnife.random.nextLong(), "superspider");
                        serverGame.addActor(new NetworkedActor(pid, true, name));
                        
                        // physics will start of with a random(ish) spawn location
                }
                
                void pickAction()
                {
                        float rnd = SwissArmyKnife.random.nextFloat();
                        
                        if (rnd < DUMMY_ACTION.CHANGE_ACTION_CHANCE)
                        {
                                rnd = SwissArmyKnife.random.nextFloat();
                                
                                for (DUMMY_ACTION possibleAction : DUMMY_ACTION.values)
                                {
                                        if (rnd < possibleAction.chance)
                                        {
                                                this.action = possibleAction;
                                                break;
                                        }
                                        
                                        rnd -= possibleAction.chance;
                                }
                        }
                        rnd -= DUMMY_ACTION.CHANGE_ACTION_CHANCE;
                        
                        if (rnd < DUMMY_ACTION.CHANGE_MOVE_CHANCE)
                        {
                                move = PhysicsMovement.get(SwissArmyKnife.random.nextInt(16));
                        }
                }
                
                void doAction(int tickAmount)
                {
                        GameS2C.S2C.Builder s2c = GameS2C.S2C.newBuilder();

                        long tick = env.getTick() - tickAmount;

                        switch (action)
                        {
                                case NOTHING:
                                        break;
                                        
                                case FIRE:
                                        for (long t = tick; t < tick + tickAmount; ++t)
                                        {
                                                if (t % FIRE_DELAY == 0)
                                                {
                                                        fire(tick + t, s2c);
                                                }
                                        }
                                        break;
                                        
                                case MOVE:
                                        move(tick, s2c, tickAmount);
                                        break;
                                        
                                case FIRE_MOVE:
                                        move(tick, s2c, tickAmount);
                                        
                                        for (long t = tick; t < tick + tickAmount; ++t)
                                        {
                                                if (t % FIRE_DELAY == 0)
                                                {
                                                        fire(tick + t, s2c);
                                                }
                                        }
                                        break;
                        }
                        
                        serverGame.broadcast(s2c);
                }
                
                private void move(long tick, GameS2C.S2C.Builder s2c, int tickAmount)
                {
                        GameOperation.ActorMove.Builder moveBuilder = s2c.addActorMoveBuilder();
                        moveBuilder.setTick(tick);
                        moveBuilder.setPid(pid);
                        moveBuilder.setDirect(true);
                        
                        for (long t = tick; t < tick + tickAmount; ++t)
                        {
                                env.actorMove(t, pid, move);
                        }

                        moveBuilder.setMove(ByteString.copyFrom(PhysicsMovement.serializeListLE(Collections.nCopies(tickAmount, move))));
                }
                
                private void fire(long tick, GameS2C.S2C.Builder s2c)
                {
                        env.actorWeapon(tick, pid, WEAPON_SLOT.GUN, false, 0, 0, 0, 0, 0);
                        GameOperation.ActorWeapon.Builder weaponBuilder = s2c.addActorWeaponBuilder();
                        weaponBuilder.setTick(tick);
                        weaponBuilder.setPid(pid);
                        weaponBuilder.setSlot(WEAPON_SLOT.GUN.id);
                }
        }
        
        private static enum DUMMY_ACTION
        {       
                // All chances should add up to 1 (at most)
                NOTHING    (0.05f),
                FIRE       (0.05f),
                MOVE       (0.6f),
                FIRE_MOVE  (0.3f);
                
                static final float CHANGE_ACTION_CHANCE = 0.2f;
                static final float CHANGE_MOVE_CHANCE = 0.01f;
                
                float chance;
                
                static final DUMMY_ACTION[] values = values();

                private DUMMY_ACTION(float chance)
                {
                        this.chance = chance;
                }
        }
}
