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
package aphelion.client.graphics.screen;

import aphelion.client.graphics.Graph;
import aphelion.client.resource.AsyncTexture;
import aphelion.client.graphics.world.ActorShip;
import aphelion.shared.resource.ResourceDB;
import aphelion.shared.swissarmyknife.SwissArmyKnife;

import org.newdawn.slick.Animation;
import org.newdawn.slick.Color;
import org.newdawn.slick.Image;

/**
 *
 * @author Joris
 */
public class EnergyBar
{
        private ResourceDB db;
        private ActorShip localShip;
        private AsyncTexture textureEnergyBar;
        private AsyncTexture textureEnergyGradients;
        private Animation anim;
        private int gradientXOffset = 0;
        private int delayTickOffset = 0;
        private int delayTickMax = 8;

        public EnergyBar(ResourceDB db, ActorShip localShip)
        {
                this.db = db;
                this.localShip = localShip;
                
                textureEnergyBar = db.getTextureLoader().getTexture("gui.healthbar");
                textureEnergyGradients = db.getTextureLoader().getTexture("gui.healthbar.gradients");
        }

        public void render(Camera camera)
        {
                if (localShip.maxEnergy == null)
                {
                        return;
                }

                int energy = SwissArmyKnife.clip(localShip.getActor().getEnergy() / 1024, 0, localShip.maxEnergy.get());

                if (!textureEnergyBar.isLoaded() || !textureEnergyGradients.isLoaded())
                {
                        return;
                }
                
                Image energyBar = textureEnergyBar.getImage();
                Image energyColor = textureEnergyGradients.getImage();
                
                
                
                if (delayTickOffset == delayTickMax)
                {
                        delayTickOffset = 0;
                        if (gradientXOffset == energyColor.getWidth() - 1)
                        {
                                gradientXOffset = 0;
                        }
                        else
                        {
                                gradientXOffset++;
                        }
                }
                else
                {
                        delayTickOffset++;
                }

                int gradientYOffset = 0;
                if (localShip.getActor().getEnergy() / 1024 < localShip.maxEnergy.get() / 2)
                {
                        gradientYOffset++;
                        if (localShip.getActor().getEnergy() / 1024 < localShip.maxEnergy.get() / 4)
                        {
                                gradientYOffset++;
                        }
                }



                float maxWidth = 256;
                float width = energy / (float) localShip.maxEnergy.get() * maxWidth;
                Graph.g.setColor(energyColor.getColor(0, 0));
                Graph.g.fillRect(camera.screenPos.x + camera.dimensionHalf.x - maxWidth, 9, maxWidth * 2, 2);

                Graph.g.setColor(energyColor.getColor(gradientXOffset, gradientYOffset));
                //Graph.g.drawRect(camera.screenPos.x + camera.dimensionHalf.x - maxWidth, 12, maxWidth * 2, 6);
                Graph.g.fillRect(camera.screenPos.x + camera.dimensionHalf.x - width, 13, width * 2, 6);

                if (anim == null)
                {
                        anim = SwissArmyKnife.spriteToAnimation(energyBar, 2, 1, 1000);
                        anim.setAutoUpdate(false);
                }

                if (gradientYOffset > 0)
                {
                        anim.update(30);
                }
                else
                {
                        anim.setCurrentFrame(0);
                }

                anim.draw(
                        (camera.dimensionX / 2) - (anim.getWidth() / 2),
                        0,
                        anim.getWidth(),
                        anim.getHeight());
                
        }
}
