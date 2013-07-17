/**
 * Copyright (C) 2013 Alexander Szczuczko
 *
 * This file may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */
package ca.szc.keratin.core.event.message.send;

import net.engio.mbassy.bus.MBassador;
import ca.szc.keratin.core.event.IrcEvent;
import ca.szc.keratin.core.event.message.MessageSend;
import ca.szc.keratin.core.net.message.InvalidMessageCommandException;
import ca.szc.keratin.core.net.message.InvalidMessageParamException;
import ca.szc.keratin.core.net.message.InvalidMessagePrefixException;
import ca.szc.keratin.core.net.message.IrcMessage;

public class SendPing
    extends MessageSend
{
    public static final String COMMAND = "PING";

    public SendPing( MBassador<IrcEvent> bus, String[] params )
        throws InvalidMessagePrefixException, InvalidMessageCommandException, InvalidMessageParamException
    {
        // TODO validation
        super( bus, new IrcMessage( null, COMMAND, params ) );
    }

    public SendPing( MBassador<IrcEvent> bus, String server1, String server2 )
        throws InvalidMessagePrefixException, InvalidMessageCommandException, InvalidMessageParamException
    {
        // TODO validation
        super( bus, new IrcMessage( null, COMMAND, server1, server2 ) );
    }

    public SendPing( MBassador<IrcEvent> bus, String server1 )
        throws InvalidMessagePrefixException, InvalidMessageCommandException, InvalidMessageParamException
    {
        super( bus, new IrcMessage( null, COMMAND, server1 ) );
    }
}