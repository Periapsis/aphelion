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
package aphelion.shared.physics.entities;

import aphelion.shared.gameconfig.GCBoolean;
import aphelion.shared.gameconfig.GCBooleanList;
import aphelion.shared.gameconfig.GCColour;
import aphelion.shared.gameconfig.GCImage;
import aphelion.shared.gameconfig.GCInteger;
import aphelion.shared.gameconfig.GCIntegerList;
import aphelion.shared.gameconfig.GCString;
import aphelion.shared.gameconfig.GCStringList;
import aphelion.shared.net.protobuf.GameOperation;
import aphelion.shared.physics.valueobjects.PhysicsMoveable;
import aphelion.shared.physics.valueobjects.PhysicsMovement;
import aphelion.shared.physics.valueobjects.PhysicsPoint;
import aphelion.shared.physics.valueobjects.PhysicsShipPosition;
import aphelion.shared.physics.WEAPON_SLOT;
import aphelion.shared.resource.ResourceDB;
import java.util.Iterator;

/**
 *
 * @author Joris
 */
public interface ActorPublic
{
        boolean hasReference();
        boolean isRemoved();
        boolean isRemoved(long tick);
        long getCreatedAt();
        int getStateId();
        int getPid();
        boolean getPosition(PhysicsShipPosition pos);
        boolean getHistoricPosition(PhysicsShipPosition pos, long tick, boolean lookAtOtherStates);
        PhysicsMoveable getHistoricMovement(long tick, boolean lookAtOtherStates);
        String getName();
        long getSeed();
        String getShip(); // as used in settings
        Iterator<ProjectilePublic> projectileIterator();
        boolean canFireWeapon(WEAPON_SLOT weapon); 
        int getRadius();
        void findSpawnPoint(PhysicsPoint result, long tick); // consistent & randomish
        int randomRotation(long tick); // consistent & randomish
        int getEnergy(); // * 1024
        int getEnergy(long tick); // * 1024
        boolean isDead();
        boolean isDead(long tick);
        long getSpawnAt();
        boolean canChangeShip(); // can the player change its ship, freq, etc?
        boolean canBoost(); // can the player use boost right now (after burners)
        boolean isEmped();
        boolean getSync(GameOperation.ActorSync.Builder b);
        GCString getWeaponKey(WEAPON_SLOT slot);
        
        GCInteger getActorConfigInteger(String name);
        GCString getActorConfigString(String name);
        GCBoolean getActorConfigBoolean(String name);
        GCIntegerList getActorConfigIntegerList(String name);
        GCStringList getActorConfigStringList(String name);
        GCBooleanList getActorConfigBooleanList(String name);
        GCImage getActorConfigImage(String name, ResourceDB db);
        GCColour getActorConfigColour(String name);
        
        GCInteger getActorConfigInteger(String weapon, String name);
        GCString getActorConfigString(String weapon, String name);
        GCBoolean getActorConfigBoolean(String weapon, String name);
        GCIntegerList getActorConfigIntegerList(String weapon, String name);
        GCStringList getActorConfigStringList(String weapon, String name);
        GCBooleanList getActorConfigBooleanList(String weapon, String name);
        GCImage getActorConfigImage(String weapon, String name, ResourceDB db);
        GCColour getActorConfigColour(String weapon, String name);
}
