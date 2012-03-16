/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jvnet.ws.wadl2java;

/**
 * A public class that allows an implementor to deal with messages output
 * by the generator. Also allow us to remove a direct API dependency
 * on the internals of generator.
 * @author gdavison
 **/
public interface MessageListener {

    /**
     * Report a warning
     * @param message The message to display to the user.
     * @param throwable The exception that triggered this message, can be null.
     */
    public void warning(String message, Throwable throwable);

    /**
     * Report informative message
     * @param message The message to display to the user.
     */
    public void info(String message);

    /**
     * Report an error.
     * @param message The message to display to the user.
     * @param throwable The exception that triggered this message, can be null.
     */
    public void error(String message, Throwable throwable);
    
}
