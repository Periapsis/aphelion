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

package aphelion.client;


import aphelion.client.net.NetworkedGame;
import aphelion.shared.event.TickEvent;
import aphelion.shared.gameconfig.GCStringList;
import aphelion.shared.physics.PhysicsEnvironment;
import aphelion.shared.physics.WEAPON_SLOT;
import aphelion.shared.physics.entities.ActorPublic;
import aphelion.shared.physics.valueobjects.PhysicsMovement;
import aphelion.shared.physics.valueobjects.PhysicsShipPosition;
import de.lessvoid.nifty.elements.Element;
import de.lessvoid.nifty.input.keyboard.KeyboardInputEvent;
import de.lessvoid.nifty.renderer.lwjgl.input.LwjglInputSystem;
import de.lessvoid.nifty.screen.Screen;
import java.util.logging.Logger;
import javax.annotation.Nonnull;
import org.lwjgl.input.Keyboard;

/**
 *
 * @author Joris
 */
public class MyKeyboard implements TickEvent
{
        private static final Logger log = Logger.getLogger("aphelion.client");
        
        private final LwjglInputSystem input;
        private final Screen mainScreen;
        private final NetworkedGame networkedGame;
        private final PhysicsEnvironment physicsEnv;
        /** The actor this keyboard input is for */
        private final ActorPublic controllingActor;
        private final GCStringList ships;
        private final Element gameMenuPopup;
        
        private final Element bigRadar;
        private final Element smallRadar;
        
        
        private long tick;
        
        // Key states:
        private boolean up, down, left, right, boost;
        private boolean multiFireGun;
        private boolean fireGun;
        private boolean fireBomb;
        private boolean fireMine;
        private boolean fireThor;
        private boolean fireBurst;
        private boolean fireRepel;
        private boolean fireDecoy;
        private boolean fireRocket;
        private boolean fireBrick;

        public MyKeyboard(@Nonnull LwjglInputSystem input, 
                          @Nonnull Screen mainScreen, 
                          @Nonnull NetworkedGame networkedGame,
                          @Nonnull PhysicsEnvironment physicsEnv,
                          @Nonnull ActorPublic controllingActor, 
                          @Nonnull GCStringList ships,
                          @Nonnull Element gameMenuPopup)
        {
                this.input = input;
                this.mainScreen = mainScreen;
                this.networkedGame = networkedGame;
                this.physicsEnv = physicsEnv;
                this.controllingActor = controllingActor;
                this.ships = ships;
                this.gameMenuPopup = gameMenuPopup;
                
                this.bigRadar =  mainScreen.findElementByName("radar-big");
                this.smallRadar = mainScreen.findElementByName("radar-small");
                
        }

        public boolean isMultiFireGun()
        {
                return multiFireGun;
        }
        
        
        public void poll()
        {
                // must come after nifty.update()
                
                /*if (!Display.isActive())
                {
                        up = false;
                        down = false;
                        left = false;
                        right = false;
                        boost = false;
                        fireGun = false;
                        fireBomb = false;
                        fireThor = false;
                        fireBurst = false;
                        fireRepel = false;
                        fireDecoy = false;
                        fireRocket = false;
                        fireBrick = false;
                        return;
                }*/

                boolean shift = Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) 
                        || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);

                boolean ctrl = Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) 
                        || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL);

                up    = Keyboard.isKeyDown(Keyboard.KEY_UP);
                down  = Keyboard.isKeyDown(Keyboard.KEY_DOWN);
                left  = Keyboard.isKeyDown(Keyboard.KEY_LEFT);
                right = Keyboard.isKeyDown(Keyboard.KEY_RIGHT);
                boost = shift;

                fireGun = !shift && ctrl;
                fireBomb = !shift && Keyboard.isKeyDown(Keyboard.KEY_TAB);
                fireMine = shift && Keyboard.isKeyDown(Keyboard.KEY_TAB);
                fireThor = Keyboard.isKeyDown(Keyboard.KEY_F6);
                fireBurst = Keyboard.isKeyDown(Keyboard.KEY_DELETE) && shift;
                fireRepel = shift && ctrl;
                fireDecoy = Keyboard.isKeyDown(Keyboard.KEY_F5);
                fireRocket = Keyboard.isKeyDown(Keyboard.KEY_F3);
                fireBrick = Keyboard.isKeyDown(Keyboard.KEY_F4);
                
                
                // Unparsed nifty keys:
                while (input.hasNextKeyboardEvent())
                {
                        KeyboardInputEvent event = input.nextKeyboardEvent();
                        
                        int key = event.getKey();
                        char chr = event.getCharacter();

                        if (key == KeyboardInputEvent.KEY_LMENU || key == KeyboardInputEvent.KEY_RMENU)
                        {
                                if (bigRadar != null && smallRadar != null)
                                {
                                        if (event.isKeyDown())
                                        {
                                                smallRadar.hide();
                                                bigRadar.show();
                                        }
                                        else
                                        {
                                                bigRadar.hide();
                                                smallRadar.show();
                                        }
                                }
                        }
                        
                        if (key == KeyboardInputEvent.KEY_ESCAPE)
                        {
                                if (!event.isKeyDown() && gameMenuPopup != null)
                                {
                                        gameMenuPopup.getNifty().showPopup(mainScreen, gameMenuPopup.getId(), null);
                                }
                        }

                        if (!event.isKeyDown()) // released a key
                        {
                                if (key == KeyboardInputEvent.KEY_DELETE)
                                {
                                        multiFireGun = !multiFireGun;
                                }
                        }
                }
        }

        @Override
        public void tick(long tick)
        {
                this.tick = tick;
                // PhysicsEnvironment should have ticked before this one.
                int localPid;

                if (!networkedGame.isReady())
                {
                        // esc to cancel connecting?
                        return;
                }

                localPid = networkedGame.getMyPid();

                if (controllingActor.isNonExistent())
                {
                        return;
                }

                if (up || down || left || right)
                {
                        boost = boost && (up || down);
                        // do not bother sending boost over the network if we can not use it 
                        // (to prevent unnecessary timewarps)
                        boost = boost && controllingActor.canBoost(); 
                        PhysicsMovement move = PhysicsMovement.get(up, down, left, right, boost);
                        physicsEnv.actorMove(physicsEnv.getTick(), localPid, move);
                        networkedGame.sendMove(physicsEnv.getTick(), move);
                }

                if (fireGun)   { tryWeapon(this.multiFireGun ? WEAPON_SLOT.GUN_MULTI : WEAPON_SLOT.GUN); }
                if (fireBomb)  { tryWeapon(WEAPON_SLOT.BOMB); }
                if (fireMine)  { tryWeapon(WEAPON_SLOT.MINE); }
                if (fireThor)  { tryWeapon(WEAPON_SLOT.THOR); }
                if (fireBurst) { tryWeapon(WEAPON_SLOT.BURST); }
                if (fireRepel) { tryWeapon(WEAPON_SLOT.REPEL); }
                if (fireDecoy) { tryWeapon(WEAPON_SLOT.DECOY); }
                if (fireRocket){ tryWeapon(WEAPON_SLOT.ROCKET); }
                if (fireBrick) { tryWeapon(WEAPON_SLOT.BRICK); }

        }

        private void tryWeapon(@Nonnull WEAPON_SLOT weapon)
        {
                if (!controllingActor.canFireWeapon(weapon))
                {
                        return;
                }

                PhysicsShipPosition weaponHint = new PhysicsShipPosition();
                controllingActor.getPosition(weaponHint);

                physicsEnv.actorWeapon(
                        physicsEnv.getTick(), 
                        networkedGame.getMyPid(), 
                        weapon, 
                        true, weaponHint.x, weaponHint.y,
                        weaponHint.x_vel, weaponHint.y_vel,
                        weaponHint.rot_snapped);

                networkedGame.sendActorWeapon(physicsEnv.getTick(), weapon, weaponHint);
        }
        
        
}
