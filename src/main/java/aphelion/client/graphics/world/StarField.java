/*
 * Aphelion
 * Copyright (c) 2013  Joris van der Wel & Joshua Edwards
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
package aphelion.client.graphics.world;

import java.util.Random;

import aphelion.client.Client;
import aphelion.client.graphics.Graph;
import aphelion.client.graphics.screen.Camera;
import aphelion.client.resource.AsyncTexture;
import aphelion.shared.physics.entities.ActorPublic;
import aphelion.shared.resource.ResourceDB;
import aphelion.shared.swissarmyknife.Point;
import aphelion.shared.swissarmyknife.SwissArmyKnife;

import org.newdawn.slick.Color;

/**
 * Starfield with depth.
 *
 * @author Joris
 */
public class StarField
{
        private int seed;
        private ActorShip localShip;
        private static final int STAR_TILE_SIZE = 512;
        private Color layer1Color;
        private Color layer2Color;
        private Color layer3Color;
        private Color layer4Color;
        private Color layer5Color;
        private ResourceDB db;
        //Background objects
        private AsyncTexture bg01, bg02, bg03, bg04, bg05,
                bg06, bg07, bg08, bg09, bg10,
                bg11, bg12, bg13, bg14;
        //Star objects
        private AsyncTexture star01, star02, star03, star04,
                star05, star06, star07;

        public StarField(int seed, ActorShip actorShip, ResourceDB db)
        {
                this.db = db;
                this.localShip = actorShip;
                loadBackgroundObjects();
                this.seed = seed;
                layer1Color = new Color(.7f, .7f, .7f);
                layer2Color = new Color(.5f, .5f, .5f);
                layer3Color = new Color(.3f, .3f, .3f);
                layer4Color = new Color(.2f, .2f, .2f);
                layer5Color = new Color(.15f, .15f, .15f);

        }

        private void loadBackgroundObjects()
        {
                if (db != null)
                {
                        //Load background objects
                        bg01 = db.getTextureLoader().getTexture("classic.background.bg01");
                        bg02 = db.getTextureLoader().getTexture("classic.background.bg02");
                        bg03 = db.getTextureLoader().getTexture("classic.background.bg03");
                        bg04 = db.getTextureLoader().getTexture("classic.background.bg04");
                        bg05 = db.getTextureLoader().getTexture("classic.background.bg05");
                        bg06 = db.getTextureLoader().getTexture("classic.background.bg06");
                        bg07 = db.getTextureLoader().getTexture("classic.background.bg07");
                        bg08 = db.getTextureLoader().getTexture("classic.background.bg08");
                        bg09 = db.getTextureLoader().getTexture("classic.background.bg09");
                        bg10 = db.getTextureLoader().getTexture("classic.background.bg10");
                        bg11 = db.getTextureLoader().getTexture("classic.background.bg11");
                        bg12 = db.getTextureLoader().getTexture("classic.background.bg12");
                        bg13 = db.getTextureLoader().getTexture("classic.background.bg13");
                        bg14 = db.getTextureLoader().getTexture("classic.background.bg14");

                        //Load star objects
                        star01 = db.getTextureLoader().getTexture("classic.background.star01");
                        star02 = db.getTextureLoader().getTexture("classic.background.star02");
                        star03 = db.getTextureLoader().getTexture("classic.background.star03");
                        star04 = db.getTextureLoader().getTexture("classic.background.star04");
                        star05 = db.getTextureLoader().getTexture("classic.background.star05");
                        star06 = db.getTextureLoader().getTexture("classic.background.star06");
                        star07 = db.getTextureLoader().getTexture("classic.background.star07");
                }
        }

        public Color colorWithZoom(Color color, float cameraZoomFactor)
        {
                float subInt = 0;
                if (cameraZoomFactor < 1)
                {
                        subInt = 1 - cameraZoomFactor;
                }
                else
                {
                        subInt = 0;
                }
                return new Color(color.r - 2 * subInt, color.g - 2 * subInt, color.b - 2 * subInt);
        }
        Random random = new Random();

        public void render(Camera camera)
        {
                Color layer1Color_modified,
                        layer2Color_modified,
                        layer3Color_modified,
                        layer4Color_modified,
                        layer6Color_modified,
                        layer5Color_modified;

                int layer1_starscale = 2,
                        layer2_starscale = 4,
                        layer3_starscale = 4,
                        layer4_starscale = 4,
                        layer5_starscale = 4,
                        layer6_starscale = 2;

                float layer1_depth_factor = 0.8F,
                        layer2_depth_factor = 0.5F,
                        layer3_depth_factor = 0.4F,
                        layer4_depth_factor = 0.3F,
                        layer5_depth_factor = 0.2F,
                        layer6_depth_factor = 0.1F;

                float sparkle_factor = 6F;

                layer1Color_modified = new Color(
                        layer1Color.r + (SwissArmyKnife.random.nextFloat() / (layer1_depth_factor + sparkle_factor)),
                        layer1Color.g + (SwissArmyKnife.random.nextFloat() / (layer1_depth_factor + sparkle_factor)),
                        layer1Color.b + (SwissArmyKnife.random.nextFloat() / (layer1_depth_factor + sparkle_factor)));
                layer2Color_modified = new Color(
                        layer1Color.r + (SwissArmyKnife.random.nextFloat() / (layer2_depth_factor + sparkle_factor)),
                        layer1Color.g + (SwissArmyKnife.random.nextFloat() / (layer2_depth_factor + sparkle_factor)),
                        layer1Color.b + (SwissArmyKnife.random.nextFloat() / (layer2_depth_factor + sparkle_factor)));
                layer3Color_modified = new Color(
                        layer2Color.r + (SwissArmyKnife.random.nextFloat() / (layer3_depth_factor + sparkle_factor)),
                        layer2Color.g + (SwissArmyKnife.random.nextFloat() / (layer3_depth_factor + sparkle_factor)),
                        layer2Color.b + (SwissArmyKnife.random.nextFloat() / (layer3_depth_factor + sparkle_factor)));
                layer4Color_modified = new Color(
                        layer3Color.r + (SwissArmyKnife.random.nextFloat() / (layer4_depth_factor + sparkle_factor)),
                        layer3Color.g + (SwissArmyKnife.random.nextFloat() / (layer4_depth_factor + sparkle_factor)),
                        layer3Color.b + (SwissArmyKnife.random.nextFloat() / (layer4_depth_factor + sparkle_factor)));
                layer5Color_modified = new Color(
                        layer4Color.r + (SwissArmyKnife.random.nextFloat() / (layer5_depth_factor + sparkle_factor)),
                        layer4Color.g + (SwissArmyKnife.random.nextFloat() / (layer5_depth_factor + sparkle_factor)),
                        layer4Color.b + (SwissArmyKnife.random.nextFloat() / (layer5_depth_factor + sparkle_factor)));
                layer6Color_modified = new Color(
                        layer5Color.r + (SwissArmyKnife.random.nextFloat() / (layer6_depth_factor + sparkle_factor)),
                        layer5Color.g + (SwissArmyKnife.random.nextFloat() / (layer6_depth_factor + sparkle_factor)),
                        layer5Color.b + (SwissArmyKnife.random.nextFloat() / (layer6_depth_factor + sparkle_factor)));

                Graph.g.setAntiAlias(true);
                drawStars(camera, colorWithZoom(layer1Color_modified, camera.zoom), layer1_depth_factor, layer1_starscale, 0);
                drawStars(camera, colorWithZoom(layer2Color_modified, camera.zoom), layer2_depth_factor, layer2_starscale, 1);
                drawStars(camera, colorWithZoom(layer3Color_modified, camera.zoom), layer3_depth_factor, layer3_starscale, 2);
                drawStars(camera, colorWithZoom(layer4Color_modified, camera.zoom), layer4_depth_factor, layer4_starscale, 3);
                drawStars(camera, colorWithZoom(layer5Color_modified, camera.zoom), layer5_depth_factor, layer5_starscale, 4);
                drawStars(camera, colorWithZoom(layer6Color_modified, camera.zoom), layer6_depth_factor, layer6_starscale, 5);
                Graph.g.setAntiAlias(false);
                last_position = camera.pos;
        }
        public boolean enable_line_effect = false;
        Point last_position;

        private void drawStars(Camera camera, Color color, float depthFactor, int starscale, int seedOffset)
        {
                if (last_position == null)
                {
                        last_position = camera.pos;
                }
                float dx = (last_position.x - camera.pos.x) / (6 + depthFactor);
                float dy = (last_position.y - camera.pos.y) / (6 + depthFactor);


                // http://nullprogram.com/blog/2011/06/13/
                int size = STAR_TILE_SIZE / starscale;

                int fieldSeed = this.seed + seedOffset;

                int mapX = (int) ((camera.pos.x) * depthFactor);
                int mapY = (int) ((camera.pos.y) * depthFactor);

                int startX = ((mapX - camera.dimensionX / 2) / size) * size - size;
                int startY = ((mapY - camera.dimensionY / 2) / size) * size - size;

                int endX = camera.dimensionX + startX + size * 3;
                int endY = camera.dimensionY + startY + size * 3;

                if (color != null)
                {
                        Graph.g.setColor(color);
                }

                for (int x = startX; x <= endX; x += size)
                {
                        for (int y = startY; y <= endY; y += size)
                        {
                                int hash = SwissArmyKnife.jenkinMix(fieldSeed, x, y);
                                for (int n = 0; n < 3; n++)
                                {
                                        int px = (hash % size) + (x - mapX);
                                        hash >>= 3;
                                        int py = (hash % size) + (y - mapY);
                                        hash >>= 3;

                                        float pxf = px + camera.dimensionHalf.x;
                                        float pyf = py + camera.dimensionHalf.y;
                                        byte backgroundObjectType = getBackgroundType(px, py, hash, seedOffset);
                                        float pxi = SwissArmyKnife.floor(pxf);
                                        float pyi = SwissArmyKnife.floor(pyf);
                                        if (backgroundObjectType == 0)
                                        {
                                                if (enable_line_effect)
                                                {
                                                        Graph.g.drawLine(pxf, pyf, SwissArmyKnife.floor(pxf + dx), SwissArmyKnife.floor(pyf + dy));
                                                }
                                                else
                                                {
                                                        Graph.g.drawLine(pxf, pyf, pxf, pyf);
                                                }
                                        }
                                        else if (backgroundObjectType == 1)
                                        {
                                                star01.getImage().draw(pxi, pyi);
                                        }
                                        else if (backgroundObjectType == 2)
                                        {
                                                star02.getImage().draw(pxi, pyi);
                                        }
                                        else if (backgroundObjectType == 3)
                                        {
                                                star03.getImage().draw(pxi, pyi);
                                        }
                                        else if (backgroundObjectType == 4)
                                        {
                                                star04.getImage().draw(pxi, pyi);
                                        }
                                        else if (backgroundObjectType == 5)
                                        {
                                                star05.getImage().draw(pxi, pyi);
                                        }
                                        else if (backgroundObjectType == 6)
                                        {
                                                star06.getImage().draw(pxi, pyi);
                                        }
                                        else if (backgroundObjectType == 7)
                                        {
                                                star07.getImage().draw(pxi, pyi);
                                        }
                                        else if (backgroundObjectType == -1)
                                        {
                                                bg01.getImage().draw(pxi, pyi);
                                        }
                                        else if (backgroundObjectType == -2)
                                        {
                                                bg02.getImage().draw(pxi, pyi);
                                        }
                                        else if (backgroundObjectType == -3)
                                        {
                                                bg03.getImage().draw(pxi, pyi);
                                        }
                                        else if (backgroundObjectType == -4)
                                        {
                                                bg04.getImage().draw(pxi, pyi);
                                        }
                                        else if (backgroundObjectType == -5)
                                        {
                                                bg05.getImage().draw(pxi, pyi);
                                        }
                                        else if (backgroundObjectType == -6)
                                        {
                                                bg06.getImage().draw(pxi, pyi);
                                        }
                                        else if (backgroundObjectType == -7)
                                        {
                                                bg07.getImage().draw(pxi, pyi);
                                        }
                                        else if (backgroundObjectType == -8)
                                        {
                                                bg08.getImage().draw(pxi, pyi);
                                        }
                                        else if (backgroundObjectType == -9)
                                        {
                                                bg09.getImage().draw(pxi, pyi);
                                        }
                                        else if (backgroundObjectType == -10)
                                        {
                                                bg10.getImage().draw(pxi, pyi);
                                        }
                                        else if (backgroundObjectType == -11)
                                        {
                                                bg11.getImage().draw(pxi, pyi);
                                        }
                                        else if (backgroundObjectType == -12)
                                        {
                                                bg12.getImage().draw(pxi, pyi);
                                        }
                                        else if (backgroundObjectType == -13)
                                        {
                                                bg13.getImage().draw(pxi, pyi);
                                        }
                                        else if (backgroundObjectType == -14)
                                        {
                                                bg14.getImage().draw(pxi, pyi);
                                        }

                                }
                        }
                }
        }

        private byte getBackgroundType(int px, int py, int hash, int layer)
        {
                int value = 0,
                        offset = 75000;
                if (hash > 100000)
                {
                        value = 100000;
                        //planets layer
                        if (layer == 1)
                        {
                                offset = 4000;
                                if (hash > value && hash < value + (offset))
                                {
                                        return -8;
                                }
                                else if (hash > value + (offset) && hash < value + (offset * 2))
                                {
                                        return -1;
                                }
                                else if (hash > value + (offset * 2) && hash < value + (offset * 3))
                                {
                                        return -9;
                                }
                                else if (hash > value + (offset * 3) && hash < value + (offset * 4))
                                {
                                        return -2;
                                }
                                else if (hash > value + (offset * 4) && hash < value + (offset) * 5)
                                {
                                        return -10;
                                }
                                else if (hash > value + (offset * 5) && hash < value + (offset * 6))
                                {
                                        return -3;
                                }
                                else if (hash > value + (offset * 6) && hash < value + (offset * 7))
                                {
                                        return -12;
                                }
                                else if (hash > value + (offset * 7) && hash < value + (offset * 8))
                                {
                                        return -4;
                                }
                                else if (hash > value + (offset * 8) && hash < value + (offset * 9))
                                {
                                        return -13;
                                }
                                else if (hash > value + (offset * 9) && hash < value + (offset * 10))
                                {
                                        return -5;
                                }
                        }
                        else if (layer == 4)
                        {
                                value = 2000;
                                offset = 10000;
                                if (hash > value && hash < value + (offset))
                                {
                                        return -6;
                                }
                                else if (hash > value && hash < value + (offset * 2))
                                {
                                        return -7;
                                }
                                else if (hash > value && hash < value + (offset * 3))
                                {
                                        return -14;
                                }
                        }

                }
                else
                {
                        if (layer == 0)
                        {
                                value = 7000;
                                offset = 100;
                                if (hash > value && hash < value + (offset))
                                {
                                        return 1;
                                }
                                else if (hash > value + (offset) && hash < value + (offset * 2))
                                {
                                        return 2;
                                }
                        }
                        else if (layer > 0 && layer < 3)
                        {
                                value = 4000;
                                offset = 250;
                                if (hash > value && hash < value + (offset))
                                {
                                        return 3;
                                }
                                else if (hash > value + (offset) && hash < value + (offset * 2))
                                {
                                        return 4;
                                }
                                else if (hash > value + (offset * 2) && hash < value + (offset * 3))
                                {
                                        return 5;
                                }
                        }
                        else if (layer == 3)
                        {
                                value = 10000;
                                offset = 1000;
                                if (hash > value && hash < value + (offset))
                                {
                                        return 6;
                                }
                                else if (hash > value + (offset) && hash < value + (offset * 2))
                                {
                                        return 7;
                                }
                        }
                }
                return 0;
        }
}
