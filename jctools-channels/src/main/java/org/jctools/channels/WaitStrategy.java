package org.jctools.channels;

public interface WaitStrategy {
    /**
     * This method can implement static or dynamic backoff. Dynamic backoff will rely on the counter for
     * estimating how long the caller has been idling. The expected usage is:
     * 
     * <pre>
     * <code>
     * int ic = 0;
     * while(true) {
     *   if(!isGodotArrived()) {
     *     ic = w.idle(ic);
     *     continue;
     *   }
     *   ic = 0;
     *   // party with Godot until he goes again
     * }
     * </code>
     * </pre>
     * 
     * @param idleCounter idle calls counter, managed by the idle method until reset
     * @return new counter value to be used on subsequent idle cycle
     */
    int idle(int idleCounter);
}