/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package aphelion.server;

/**
 *
 * @author Joris
 */
public class ServerConfigException extends Exception
{
        public ServerConfigException()
        {
                super("Invalid server configuration");
        }

        public ServerConfigException(String message)
        {
                super(message);
        }

        public ServerConfigException(String message, Throwable cause)
        {
                super(message, cause);
        }

        public ServerConfigException(Throwable cause)
        {
                super("Invalid server configuration", cause);
        }
}
