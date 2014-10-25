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

import aphelion.client.Client;
import aphelion.client.graphics.AnimatedColour;
import aphelion.client.graphics.screen.Camera;
import aphelion.shared.event.TickEvent;
import aphelion.shared.gameconfig.GCBoolean;
import aphelion.shared.gameconfig.GCColour;
import aphelion.shared.gameconfig.GCImage;
import aphelion.shared.gameconfig.WrappedValueAbstract;
import aphelion.shared.physics.EnvironmentConf;
import aphelion.shared.physics.PhysicsEnvironment;
import aphelion.shared.physics.entities.ProjectilePublic;
import aphelion.shared.physics.valueobjects.PhysicsPoint;
import aphelion.shared.physics.valueobjects.PhysicsPositionVector;
import aphelion.shared.resource.ResourceDB;
import aphelion.shared.swissarmyknife.Point;
import aphelion.shared.swissarmyknife.SwissArmyKnife;
import de.lessvoid.nifty.tools.Color;
import java.lang.ref.WeakReference;
import javax.annotation.Nonnull;
import org.lwjgl.opengl.GL11;
import org.newdawn.slick.Animation;
import org.newdawn.slick.Image;
import org.newdawn.slick.SpriteSheetCounted;
import org.newdawn.slick.opengl.TextureImpl;
import org.newdawn.slick.opengl.renderer.SGL;

/**
 * The graphic counter part of physics.Projectile. 
 * @author Joris
 */
public class Projectile extends MapEntity implements WrappedValueAbstract.ChangeListener, TickEvent
{
        final ProjectilePublic physicsProjectile;
        public final Point shadowPosition = new Point(0, 0);
        public final Point shadowPosition_prev = new Point(0, 0);
        
        // RenderDelay:
        public final RenderDelayValue renderDelay_value = new RenderDelayValue(0);
        public long renderDelay_current;
        public WeakReference<ActorShip> renderDelay_basedOn;
        public long renderingAt_tick;
        
        private final GCImage imageNoBounce;
        private final GCImage imageBounces;
        private final GCImage imageInactive;
        private final GCImage imageTrail;
        private final GCBoolean imageTrailRandomized;
        private final GCColour colourRadar;
        
        private Animation animNoBounce;
        private Animation animBounces;
        private Animation animInactive;
        private AnimatedColour animRadar;
        
        public static final float TIMEWARP_ALPHA_VELOCITY_MIN = 0.003f; // 10 seconds to fully fade back in
        public static final float TIMEWARP_ALPHA_VELOCITY_MAX = 0.1f;
        public static final float TIMEWARP_ALPHA_VELOCITY_LOCAL_DIST_SMOOTHING = 500; // in pixels

        public Projectile(@Nonnull ResourceDB db, @Nonnull ProjectilePublic physicsProjectile)
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
        
        public @Nonnull ProjectilePublic getPhysicsProjectile()
        {
                return physicsProjectile;
        }
        
        public void calculateRenderAtTick(@Nonnull PhysicsEnvironment physicsEnv)
        {
                renderDelay_current = SwissArmyKnife.clip(
                        renderDelay_value.get(),
                        0, 
                        physicsEnv.getConfig().HIGHEST_DELAY);
                        
                this.renderingAt_tick = physicsEnv.getTick() - renderDelay_current;
        }
        
        public void setShadowPositionFromPhysics(int x, int y)
        {
                shadowPosition_prev.set(shadowPosition);
                shadowPosition.x = x / 1024f;
                shadowPosition.y = y / 1024f;
        }

        @Override
        public void tick(long tick)
        {
                super.tick(tick);
                renderDelay_value.tick(tick);
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
        public void gameConfigValueChanged(@Nonnull WrappedValueAbstract val)
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
        public boolean render(@Nonnull Camera camera, int iteration)
        {
                if (!isExists())
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
                                org.newdawn.slick.Color color = this.animRadar.get();
                                TextureImpl.bindNone();
                                GL11.glColor4f(color.r, color.g, color.b, color.a * this.alpha);
                                float x = screenPos.x - 0.5f;
                                float y = screenPos.y - 0.5f;
                                GL11.glBegin(SGL.GL_QUADS);
                                GL11.glVertex2f(x, y);
                                GL11.glVertex2f(x + 1, y);
                                GL11.glVertex2f(x + 1, y + 1);
                                GL11.glVertex2f(x, y + 1);
                                GL11.glEnd();
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
                                PhysicsPositionVector phyPos = new PhysicsPositionVector();

                                long rand = imageTrailRandomized.get() ? SwissArmyKnife.fastRandomIsh() : 0;

                                long tick = this.renderingAt_tick - (rand & 0b11); // 0, 1, 2, 3
                                rand >>= 2;
                                
                                for (int tile = 0; 
                                        tile < trail.getTilesCount(); 
                                        tile += (rand & 0b1) + 1, rand >>= 1)
                                {
                                        physicsProjectile.getHistoricPosition(
                                                phyPos, 
                                                tick - 2 * tile, 
                                                true);

                                        if (phyPos.pos.set)
                                        {
                                                trailPos.set(phyPos.pos);
                                                trailPos.divide(1024);
                                                camera.mapToScreenPosition(trailPos, trailPos);

                                                Image img = trail.getSubImage(tile);
                                                img.draw(trailPos.x - img.getWidth()/2, 
                                                         trailPos.y - img.getHeight()/2, 
                                                         this.alphaFilter);
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
                                        anim.getHeight() * camera.zoom,
                                        this.alphaFilter);
                        }
                        
                        if (Client.showDebug)
                        {
                                camera.renderPlayerText(this.renderDelay_current + "", screenPos.x + 5, screenPos.y + 5, Color.WHITE);
                        }
                }
                
                return iteration < 1;
        }
}