package aphelion.client;

import aphelion.client.graphics.Graph;
import java.io.InputStream;

import org.newdawn.slick.util.ResourceLoader;

import java.awt.FontFormatException;
import java.awt.GraphicsEnvironment;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.newdawn.slick.SlickException;
import org.newdawn.slick.UnicodeFont;
import org.newdawn.slick.font.effects.ColorEffect;


public class Fonts 
{
        private static final Logger log = Logger.getLogger(Fonts.class.getName());
	public static UnicodeFont concielian_jet_14; // http://iconian.com/ TODO: credit
        public static UnicodeFont verdana_bold_14;
        public static UnicodeFont monospace_bold_16;
	
	public static void initialize()
	{    
		try
                {
                        java.awt.Font awtFont = null;
                        
                        InputStream inputStream	= new FileInputStream("assets/UbuntuMono-B.ttf");
                        awtFont = java.awt.Font.createFont(java.awt.Font.TRUETYPE_FONT, inputStream);
                        inputStream.close();
                        awtFont = awtFont.deriveFont(16f); // set font size
                        monospace_bold_16 = new UnicodeFont(awtFont);
                        monospace_bold_16.getEffects().add(new ColorEffect());
                        loadAllGlyphs(monospace_bold_16);
                        
                        inputStream = ResourceLoader.getResourceAsStream("assets/concielian.ttf");
                        awtFont = java.awt.Font.createFont(java.awt.Font.TRUETYPE_FONT, inputStream);
                        inputStream.close();
                        awtFont = awtFont.deriveFont(14f); // set font size
                        concielian_jet_14 = new UnicodeFont(awtFont);
                        concielian_jet_14.getEffects().add(new ColorEffect());
                        loadLatinGlyphs(concielian_jet_14);

                        awtFont = null;
                        if (hasFontFamily("Verdana"))
                        {
                                awtFont = new java.awt.Font("Verdana", java.awt.Font.BOLD, 14);
                        }
                        else
                        {
                                awtFont = new java.awt.Font(java.awt.Font.SANS_SERIF, java.awt.Font.BOLD, 14);
                        }
                        
                        verdana_bold_14 = new UnicodeFont(awtFont);
                        verdana_bold_14.getEffects().add(new ColorEffect());
                        loadLatinGlyphs(verdana_bold_14);
                }
                catch (FontFormatException | IOException e)
                {
                        log.log(Level.SEVERE, "Unable to load font!", e);
                }	
	}
        
        public static void setDefault()
        {
                Graph.g.setFont(monospace_bold_16);
        }
        
        private static void loadLatinGlyphs(UnicodeFont font)
        {
                // for use in game (menu's etc)
                
                
                font.addGlyphs(0x0020, 0x007F); // Basic latin
                font.addGlyphs(0x00A0, 0x00FF); // Latin-1 supplement (includes upper range of ISO 8859-1)
                try
                {
                        font.loadGlyphs();
                }
                catch (SlickException ex)
                {
                        log.log(Level.SEVERE, "Unable to load font glyphs!", ex);
                }
        }
        
        private static void loadAllGlyphs(UnicodeFont font)
        {
                // adds all kinds of funky glyphs players may want to use in chat
                
                // Load as much glyphs as possible at the same time
                // this ensures they all end up in the same texture instead of many
                // small textures
                
                font.addGlyphs(0x0020, 0x007F); // Basic latin
                font.addGlyphs(0x00A0, 0x00FF); // Latin-1 supplement (includes upper range of ISO 8859-1)
                font.addGlyphs(0x20A0, 0x20CF); // Currency Symbols
                font.addGlyphs(0x2150, 0x215F); // Fractions in "Number forms "
                font.addGlyphs(0x2190, 0x2199); // Some useful arrows
                // some funky symbols
                font.addGlyphs(0x2600, 0x2606);
                font.addGlyphs(0x260E, 0x2615);
                font.addGlyphs(0x2620, 0x2623);
                font.addGlyphs(0x262D, 0x262F);
                font.addGlyphs(0x2639, 0x2642);
                font.addGlyphs(0x2654, 0x266F);
                try
                {
                        font.loadGlyphs();
                }
                catch (SlickException ex)
                {
                        log.log(Level.SEVERE, "Unable to load font glyphs!", ex);
                }
        }
        
        public static boolean hasFontFamily(String name)
        {
                GraphicsEnvironment genv = GraphicsEnvironment.getLocalGraphicsEnvironment();
                for (String ffname : genv.getAvailableFontFamilyNames())
                {
                        if (name.equals(ffname))
                        {
                                return true;
                        }
                }
                
                return false;
        }
}
