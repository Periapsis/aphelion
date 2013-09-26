/*
 * Aphelion
 * Copyright (c) 2013  Joshua Edwards
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
 
package aphelion.client.graphics.screen;

import org.newdawn.slick.Animation;
import org.newdawn.slick.Image;
import org.newdawn.slick.SpriteSheet;

import aphelion.client.resource.AsyncTexture;
import aphelion.client.graphics.world.ActorShip;
import aphelion.shared.gameconfig.GCInteger;
import aphelion.shared.resource.ResourceDB;

public class StatusDisplay 
{
	private ResourceDB db;
	private ActorShip localShip;
	private AsyncTexture textureDisplay;
	private Image imageDisplay,
				  imageEnergy;
	private SpriteSheet spriteSheetEnergy,
						spriteSheetNumbers;
	private Animation anim;
	
	
	public StatusDisplay(ResourceDB db, ActorShip localShip)
	{
		this.db = db;
		this.localShip = localShip;
		if(db != null)
		{
			textureDisplay = db.getTextureLoader().getTexture("gui.display");
			imageEnergy = db.getTextureLoader().getTexture("gui.display.energy").getImage();
			//spriteSheetEnergy = new SpriteSheet(imageEnergy,imageEnergy.getWidth() / 10,imageEnergy.getHeight());
			spriteSheetNumbers = new SpriteSheet(db.getTextureLoader().getTexture("gui.display.numbers").getImage(),10,1);
		}
	}

	 public void render(Camera camera)
     {
         if (localShip.maxEnergy == null || textureDisplay == null)
         {
        	 return;
         }
         if (imageDisplay == null)
     	 {
     		imageDisplay = textureDisplay.getImage();
     	 }
         imageDisplay.draw(
     			(camera.dimensionX - imageDisplay.getWidth()), 
                 6, 
                 imageDisplay.getWidth(),
                 imageDisplay.getHeight());
         int energy = localShip.getActor().getEnergy()  / 1024;
         String energyString = "" + energy;
         int energyFigures = (byte) energyString.length();
         short energyWidth = (short) (imageEnergy.getWidth() / 10);
         int offset = energyWidth - 2;
         for(int x = 0; x < energyFigures; x++)
         {
        	 int energyNumber = Integer.parseInt(energyString.charAt(x) + "");
        	 imageEnergy.getSubImage((energyNumber) * energyWidth, 0, energyWidth, imageEnergy.getHeight()).draw(((camera.dimensionX - (energyWidth * energyFigures)) + (x * energyWidth)) - offset,1);
         }
     }

}
