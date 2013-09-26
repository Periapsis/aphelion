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

/**
 *
 * @author Joris
 */
public enum WEAPON_SLOT
{
        GUN(0, "weapon-slot-gun"),
        GUN_MULTI(1, "weapon-slot-gun-multi"),
        BOMB(2, "weapon-slot-bomb"),
        MINE(3, "weapon-slot-mine"),
        BURST(4, "weapon-slot-burst"),
        REPEL(5, "weapon-slot-repel"),
        DECOY(6, "weapon-slot-decoy"),
        THOR(7, "weapon-slot-thor"),
        BRICK(8, "weapon-slot-brick"),
        ROCKET(9, "weapon-slot-rocket"),
        ;
        
        public final int id; // can be used as an array key
        public final String settingName;

        private WEAPON_SLOT(int id, String settingName)
        {
                this.settingName = settingName;
                this.id = id;
        }
        
        public static WEAPON_SLOT byId(int id)
        {
                return values()[id];
        }
        
        public static boolean isValidId(int id)
        {
                return id >= 0 && id < values().length;
        }
}
