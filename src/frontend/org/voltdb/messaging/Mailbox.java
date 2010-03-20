/* This file is part of VoltDB.
 * Copyright (C) 2008-2010 VoltDB L.L.C.
 *
 * VoltDB is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * VoltDB is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with VoltDB.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.voltdb.messaging;

/**
 * A <code>Mailbox</code> represents a single destination for
 * messages in the messaging system.
 *
 */
public interface Mailbox {

    /**
     * Send a serializable object to a specific site and mailbox. This just
     * forwards to the Messenger's send method of the instance that generated
     * this mailbox for now.
     *
     * @param siteId The id of the destination site.
     * @param mailboxId The id of the destination mailbox.
     * @param message A serializable object to be sent to another mailbox.
     * @throws MessagingException Throws an exception if the destination
     * mailbox cannot be reached (maybe) or if an exception is thrown during
     * serialization.
     */
    public void send(int siteId, int mailboxId, VoltMessage message) throws MessagingException;

    /**
     * Send a serializable object to a specific mailbox at a list of sites.
     * This also just forwards to the Messenger's send method of the instance
     * that generated this mailbox for now. It's assumed that most "to-all"
     * sending will be done to a common mailbox id.
     *
     * @param siteIds The ids of the destination sites.
     * @param mailboxId The id of the destination mailbox.
     * @param message A serializable object to be sent to other mailboxes.
     * @throws MessagingException Throws an exception if a destination
     * mailbox cannot be reached (maybe) or if an exception is thrown during
     * serialization.
     */
    public void send(int[] siteIds, int mailboxId, VoltMessage message)
        throws MessagingException;

    /**
     * Get the number of messages waiting to be delivered for this mailbox.
     *
     * @return An integer representing the number of waiting messages.
     */
    public int getWaitingCount();

    /**
     * Get the next Object from this messaging queue from the default subject.
     *
     * @return A message object on success or null if no object is waiting.
     */
    public VoltMessage recv();

    /**
     * Get the next Object from this messaging queue from the default subject. Blocks if no messages
     * are available.
     *
     * @return A message object on success or null if interrupted
     */
    public VoltMessage recvBlocking();

    /**
     * Get the next Object from this messaging queue from the default subject.. Blocks if no messages
     * are available.
     * @param  timeout Number of milliseconds to wait for work
     * @return A message object on success or null if interrupted
     */
    public VoltMessage recvBlocking(long timeout);

    /**
     * Get the next Object from this messaging queue from the default subject.
     *
     * @return A message object on success or null if no object is waiting.
     */
    public VoltMessage recv(Subject s);

    /**
     * Get the next Object from this messaging queue from the default subject. Blocks if no messages
     * are available.
     *
     * @return A message object on success or null if interrupted
     */
    public VoltMessage recvBlocking(Subject s);

    /**
     * Get the next Object from this messaging queue from the default subject.. Blocks if no messages
     * are available.
     * @param  timeout Number of milliseconds to wait for work
     * @return A message object on success or null if interrupted
     */
    public VoltMessage recvBlocking(Subject s, long timeout);
}
