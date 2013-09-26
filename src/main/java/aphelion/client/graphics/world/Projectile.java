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
package aphelion.client.graphics.world;

import aphelion.client.graphics.AnimatedColour;
import aphelion.client.graphics.Graph;
import aphelion.client.graphics.screen.Camera;
import aphelion.shared.event.TickEvent;
import aphelion.shared.gameconfig.GCBoolean;
import aphelion.shared.gameconfig.GCColour;
import aphelion.shared.gameconfig.GCImage;
import aphelion.shared.gameconfig.WrappedValueAbstract;
import aphelion.shared.physics.entities.ProjectilePublic;
import aphelion.shared.physics.valueobjects.PhysicsPoint;
import aphelion.shared.resource.ResourceDB;
import aphelion.shared.swissarmyknife.Point;
import aphelion.shared.swissarmyknife.SwissArmyKnife;
import org.newdawn.slick.Animation;
import org.newdawn.slick.Color;
import org.newdawn.slick.Image;
import org.newdawn.slick.SpriteSheetCounted;

/**
 *
 * @author Joris
 */
public class Projectile extends MapEntity implements TickEvent, WrappedValueAbstract.ChangeListener
{
        final ProjectilePublic physicsProjectile;
        public final RenderDelay renderDelay = new RenderDelay(2); // todo move the "2" to settings
        public final Point shadowPosition = new Point(0, 0);
        public long renderingAt_ticks;
        
        private GCImage imageNoBounce;
        private GCImage imageBounces;
        private GCImage imageInactive;
        private GCImage imageTrail;
        private GCBoolean imageTrailRandomized;
        private GCColour colourRadar;
        
        private Animation animNoBounce;
        private Animation animBounces;
        private Animation animInactive;
        private AnimatedColour animRadar;

        public Projectile(ResourceDB db, ProjectilePublic physicsProjectile)
        {
                super(db);
                this.physicsProjectile = physicsProjectile;
                imageNoBounce = physicsProjectile.getWeaponConfigImage("projectile-image", db);
                imageBounces = physicsProjectile.getWeaponConfigImage("projectile-image-bounces", db);
                imageInactive = physicsProjectile.getWeaponConfigImage("projectile-image-inactive", db);
                imageTrail = physicsProjectile.getWeaponConfigImage("projectile-image-trail", db);
                imageTrailRandomized = physicsProjectile.getWeaponConfigBoolean("projectile-image-trail-randomized");
                colourRadar = physicsProjectile.getWeaponConfigColour("projectile-radar-colour");
        }
        
        public ProjectilePublic getPhysicsProjectile()
        {
                return physicsProjectile;
        }
        
        public void setShadowPositionFromPhysics(int x, int y)
        {
                shadowPosition.x = x / 1024f;
                shadowPosition.y = y / 1024f;
        }
        
        private void updateAnimObjects()
        {
                if (animNoBounce == null)
                {
                        animNoBounce = imageNoBounce.newAnimation();
                        if (animNoBounce != null)
                        {
                                animNoBounce.setLooping(true);
                        }
                }
                
                if (animBounces == null)
                {
                        animBounces = imageBounces.newAnimation();
                        if (animBounces != null)
                        {
                                animBounces.setLooping(true);
                        }       
                }
                
                if (animInactive == null)
                {
                        animInactive = imageInactive.newAnimation();
                        if (animInactive != null)
                        {
                                animInactive.setLooping(true);
                        }       
                }
                
                if (animRadar == null)
                {
                        animRadar = colourRadar.getAnimation();
                }
        }
        
        @Override
        public void gameConfigValueChanged(WrappedValueAbstract val)
        {
                if (val == this.imageNoBounce)
                {
                        this.animNoBounce = null;
                }
                
                if (val == this.imageBounces)
                {
                        this.animBounces = null;
                }
                
                if (val == this.imageInactive)
                {
                        this.animInactive = null;
                }
                
                if (val == this.colourRadar)
                {
                        this.animRadar = null;
                }
        }
        
        @Override
        public boolean render(Camera camera, int iteration)
        {
                if (!exists)
                {
                        return false;
                }
                
                if (iteration > 1)
                {
                        return false;
                }
                
                updateAnimObjects();
                
                Point screenPos = new Point();
                camera.mapToScreenPosition(pos, screenPos);
                
                
                if (camera.radarRendering)
                {
                        if (iteration == 0 && this.animRadar != null)
                        {
                                Graph.g.setColor(this.animRadar.get());
                                Graph.g.fillRect(screenPos.x - 0.5f, screenPos.y - 0.5f, 1, 1);
                        }
                        
                        return false;
                }
                
                if (iteration == 0)
                {
                        // render the trail
                        SpriteSheetCounted trail = this.imageTrail.getSpriteSheet();
                        if (trail != null)
                        {
                                Point trailPos = new Point();
                                PhysicsPoint phyPos = new PhysicsPoint();

                                long rand = imageTrailRandomized.get() || true ? SwissArmyKnife.fastRandomIsh() : 0;

                                long tick = this.renderingAt_ticks - (rand & 0b11); // 0, 1, 2, 3
                                rand >>= 2;

                                for (int tile = 0; 
                                        tile < trail.getTilesCount(); 
                                        tile += (rand & 0b1) + 1, rand >>= 1)
                                {
                                        physicsProjectile.getHistoricPosition(
                                                phyPos, 
                                                tick - 2 * tile, 
                                                true);

                                        if (phyPos.set)
                                        {
                                                trailPos.set(phyPos.x, phyPos.y);
                                                trailPos.divide(1024);
                                                camera.mapToScreenPosition(trailPos, trailPos);

                                                Image img = trail.getSubImage(tile);
                                                img.drawCentered(trailPos.x, trailPos.y);
                                        }



                                }
                        }
                }
                else if (iteration == 1)
                {
                        Animation anim;

                        if (imageInactive.isSet() && !this.physicsProjectile.isActive())
                        {
                                anim = animInactive;
                        }
                        else if (imageBounces.isSet() && this.physicsProjectile.getBouncesLeft() != 0)
                        {
                                anim = animBounces;
                        }
                        else
                        {
                                anim = animNoBounce; // also fallback
                        }

                        if (anim != null)
                        {       
                                anim.draw(
                                        screenPos.x - anim.getWidth() / 2 * camera.zoom, 
                                        screenPos.y - anim.getHeight() / 2 * camera.zoom, 
                                        anim.getWidth() * camera.zoom,
                                        anim.getHeight() * camera.zoom);
                        }
                }

                // Graph.g.drawString(renderDelay.get() + "", screenPos.x + w, screenPos.y + h);
                
                return iteration < 1;
        }

        @Override
        public void tick(long tick)
        {
                renderDelay.tick(tick);
        }
}