/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package aphelion.shared.net;

/**
 *
 * @author Joris
 */
public enum COMMAND_SOURCE
{
        UNSPECIFIED(0),
        USER_MANUAL(1),
        USER_FROM_GUI(2)
        ;
        
        public final int id;

        private COMMAND_SOURCE(int id)
        {
                this.id = id;
        }
}
