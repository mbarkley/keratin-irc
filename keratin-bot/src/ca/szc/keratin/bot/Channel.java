/**
 * Copyright (C) 2013 Alexander Szczuczko
 *
 * This file may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */
package ca.szc.keratin.bot;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import net.engio.mbassy.listener.Handler;

import org.pmw.tinylog.Logger;

import ca.szc.keratin.bot.User.PrivLevel;
import ca.szc.keratin.core.event.message.recieve.ReceiveChannelMode;
import ca.szc.keratin.core.event.message.recieve.ReceiveJoin;
import ca.szc.keratin.core.event.message.recieve.ReceivePart;
import ca.szc.keratin.core.event.message.recieve.ReceiveReply;
import ca.szc.keratin.core.net.message.IrcMessage;

/**
 * Holds data for one channel
 */
public class Channel
{
    private static final String OP_PREFIX = "@";

    private static final String CODE_RPL_NAMREPLY = "353";

    private final String name;

    private final String key;

    /**
     * Map with key of IRC nickname to corresponding User
     */
    private final ConcurrentHashMap<String, User> nicks;

    private final Object nicksMutex = new Object();

    /**
     * @param channelName The channel's name. Including the #. Cannot be null.
     * @param channelKey The channel's key. May be null if there is no key.
     */
    public Channel( String channelName, String channelKey )
    {
        this.name = channelName;
        this.key = channelKey;
        nicks = new ConcurrentHashMap<String, User>();
    }

    /**
     * @return The channel's name. Including the #. Cannot be null.
     */
    public String getName()
    {
        return name;
    }

    /**
     * @return The channel's key. May be null if there is no key.
     */
    public String getKey()
    {
        return key;
    }

    /**
     * Get all of the nicks in the channel, regardless of privilege level. Requires an active connection.
     * 
     * @return The NAMES list of this channel, or null on error.
     */
    public List<String> getNicks()
    {
        LinkedList<String> nickList = new LinkedList<String>();

        synchronized ( nicksMutex )
        {
            for ( User user : nicks.values() )
            {
                nickList.add( user.getNick() );
            }
        }

        return nickList;
    }

    /**
     * Get the nicks of all regular users in the channel. Requires an active connection.
     * 
     * @return The filtered NAMES list of this channel, or null on error.
     */
    public List<String> getRegularNicks()
    {
        LinkedList<String> filteredList = new LinkedList<String>();

        synchronized ( nicksMutex )
        {
            for ( User user : nicks.values() )
            {
                if ( user.getPrivLevel().equals( PrivLevel.Regular ) )
                    filteredList.add( user.getNick() );
            }
        }

        return filteredList;
    }

    /**
     * Get the nicks of all operator users in the channel. Requires an active connection.
     * 
     * @return The filtered NAMES list of this channel, or null on error.
     */
    public List<String> getOperatorNicks()
    {
        LinkedList<String> filteredList = new LinkedList<String>();

        synchronized ( nicksMutex )
        {
            for ( User user : nicks.values() )
            {
                if ( user.getPrivLevel().equals( PrivLevel.Op ) )
                    filteredList.add( user.getNick() );
            }
        }

        return filteredList;
    }

    /**
     * @return true iff the nick is in the channel and is an op in the channel
     */
    public boolean isOp( String nick )
    {
        synchronized ( nicksMutex )
        {
            if ( nicks.containsKey( nick ) )
            {
                User user = nicks.get( nick );
                if ( user.getPrivLevel().equals( PrivLevel.Op ) )
                    return true;
            }

            return false;
        }
    }

    /**
     * @return true iff the nick is in the channel and is a regular user (non-op) in the channel
     */
    public boolean isRegular( String nick )
    {
        synchronized ( nicksMutex )
        {
            if ( nicks.containsKey( nick ) )
            {
                User user = nicks.get( nick );
                if ( user.getPrivLevel().equals( PrivLevel.Regular ) )
                    return true;
            }

            return false;
        }
    }

    @Override
    public String toString()
    {
        synchronized ( nicksMutex )
        {
            return "Channel [name=" + name + ", key=" + key + ", nicks=" + nicks + "]";
        }
    }

    private void setNickAs( String nick, PrivLevel privLevel )
    {
        synchronized ( nicksMutex )
        {
            if ( nicks.containsKey( nick ) )
            {
                User user = nicks.get( nick );
                if ( user.getPrivLevel().equals( privLevel ) )
                {
                    Logger.trace( getName() + " Nick already " + privLevel + ", doing nothing: " + nick );
                }
                else
                {
                    Logger.trace( getName() + " Changing nick privLevel to " + privLevel + ": " + nick );
                    user.setPrivLevel( privLevel );
                }
            }
            else
            {
                Logger.trace( getName() + " New user, adding as " + privLevel + ": " + nick );
                nicks.put( nick, new User( nick, privLevel ) );
            }
        }
    }

    @Handler
    private void namesListing( ReceiveReply event )
    {
        IrcMessage msg = event.getMessage();
        String[] params = msg.getParams();
        String replyNum = msg.getCommand();

        if ( CODE_RPL_NAMREPLY.equals( replyNum ) )
        {
            String channelName = params[2];
            if ( name.equals( channelName ) )
            {
                String nicksBlob = params[3];
                if ( nicksBlob.startsWith( ":" ) )
                    nicksBlob = nicksBlob.substring( 1 );

                String[] nicksArray = nicksBlob.split( " " );

                synchronized ( nicksMutex )
                {
                    Logger.trace( "Processing names reply for channel: " + getName() );
                    for ( String nick : nicksArray )
                    {
                        // Treating the NAMES listing as authoritative, may override existing values
                        if ( nick.startsWith( OP_PREFIX ) )
                        {
                            nick = nick.substring( 1 );
                            setNickAs( nick, PrivLevel.Op );
                        }
                        else
                        {
                            setNickAs( nick, PrivLevel.Regular );
                        }
                    }
                }
            }
        }
    }

    @Handler
    private void updateOnJoin( ReceiveJoin event )
    {
        String nick = event.getJoiner();

        // Only do something if the mode change is for this channel
        if ( name.equals( event.getChannel() ) )
        {
            synchronized ( nicksMutex )
            {
                Logger.trace( "Processing join in channel: " + getName() );
                // Treating JOINs as non-authoritative, may not override existing values
                if ( !nicks.containsKey( nick ) )
                {
                    setNickAs( nick, PrivLevel.Regular );
                }
            }
        }
    }

    @Handler
    private void updateOnPart( ReceivePart event )
    {
        String nick = event.getParter();

        // Only do something if the mode change is for this channel
        if ( name.equals( event.getChannel() ) )
        {
            synchronized ( nicksMutex )
            {
                Logger.trace( "Processing part in channel: " + getName() );
                Logger.trace( "Removing nick: " + nick );
                if ( nicks.remove( nick ) == null )
                {
                    Logger.trace( "Nick to be removed was not in the nicks collection: " + nick );
                }
            }
        }
    }

    // TODO handle nick changes (transfer user data to new nick)

    @Handler
    private void updateOnMode( ReceiveChannelMode event )
    {
        String flags = event.getFlags();

        // Only do something if the mode change is for this channel
        if ( name.equals( event.getTarget() ) )
        {
            boolean op = flags.startsWith( "+o" );
            boolean deop = flags.startsWith( "-o" );

            if ( op || deop )
            {
                List<String> affectedNicks = event.getFlagParams();

                synchronized ( nicksMutex )
                {
                    Logger.trace( "Processing mode update in channel: " + getName() );
                    for ( String nick : affectedNicks )
                    {
                        if ( nick.startsWith( ":" ) )
                            nick = nick.substring( 1 );

                        if ( nicks.containsKey( nick ) )
                        {
                            if ( op )
                            {
                                setNickAs( nick, PrivLevel.Op );
                            }
                            else if ( deop )
                            {
                                setNickAs( nick, PrivLevel.Regular );
                            }
                        }
                    }
                }
            }
        }
    }
}