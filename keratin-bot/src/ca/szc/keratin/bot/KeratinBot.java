/**
 * Copyright (C) 2013 Alexander Szczuczko
 *
 * This file may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */
package ca.szc.keratin.bot;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.net.UnknownHostException;
import java.util.HashSet;
import java.util.Set;

import net.engio.mbassy.bus.MBassador;

import org.pmw.tinylog.Logger;

import ca.szc.keratin.bot.annotation.AssignedBot;
import ca.szc.keratin.bot.annotation.HandlerContainerDetector;
import ca.szc.keratin.bot.handlers.ConnectionPreamble;
import ca.szc.keratin.bot.handlers.ManageChannels;
import ca.szc.keratin.core.event.IrcEvent;
import ca.szc.keratin.core.event.message.send.SendJoin;
import ca.szc.keratin.core.event.message.send.SendNick;
import ca.szc.keratin.core.event.message.send.SendPart;
import ca.szc.keratin.core.net.InvalidPortException;
import ca.szc.keratin.core.net.IrcConnection;
import ca.szc.keratin.core.net.message.InvalidMessageCommandException;
import ca.szc.keratin.core.net.message.InvalidMessageParamException;
import ca.szc.keratin.core.net.message.InvalidMessagePrefixException;

/**
 * A class for supporting IRC bots.
 */
public class KeratinBot
{
    private String user;

    private String nick;

    private String realName;

    private String serverAddress;

    private int serverPort;

    private boolean sslEnabled;

    private Set<Channel> channels;

    private boolean initialConnectionMade;

    private MBassador<IrcEvent> connectionBus;

    /**
     * Make a KeratinBot with no fields predefined. Must have fields set before calling connect().
     */
    public KeratinBot()
    {
        initialConnectionMade = false;
    }

    /**
     * Make a KeratinBot with all fields needed for a connection predefined.
     * 
     * @param user IRC username
     * @param nick IRC nick. This is the unique ID of a client on IRC
     * @param realName IRC full/real name
     * @param serverAddress The address of the server to connect to when connect() is called.
     * @param serverPort The port on the server to connect to when connect() is called.
     * @param sslEnabled Iff true, use SSL sockets.
     * @param initialChannels The channels to connect to initially, can be empty, but not null.
     */
    public KeratinBot( String user, String nick, String realName, String serverAddress, int serverPort,
                       boolean sslEnabled, Channel[] initialChannels )
    {
        this();
        setUser( user );
        setNick( nick );
        setRealName( realName );
        setServerAddress( serverAddress );
        setServerPort( serverPort );
        setSslEnabled( sslEnabled );
        for ( Channel channel : initialChannels )
        {
            addChannel( channel.getName(), channel.getKey() );
        }
    }

    /**
     * Perform the connection. All fields must be defined before calling this, if using the blank constructor.
     */
    public void connect()
    {
        IrcConnection conn = null;
        try
        {
            conn = new IrcConnection( serverAddress, serverPort, sslEnabled );
        }
        catch ( UnknownHostException | InvalidPortException e )
        {
            Logger.error( e, "Could not make IrcConnection" );
        }

        connectionBus = conn.getEventBus();

        connectionBus.subscribe( new ConnectionPreamble( user, nick, realName ) );
        connectionBus.subscribe( new ManageChannels( this, channels ) );

        for ( Class<?> handlerContainer : HandlerContainerDetector.getContainers() )
        {
            try
            {
                // Create an instance of the annotated class
                Object listener = handlerContainer.getConstructor().newInstance();

                // Set @AssignedBot annotated fields in the listener instance to a reference to this KeratinBot.
                for ( Field field : handlerContainer.getDeclaredFields() )
                {
                    if ( field.getType().equals( this.getClass() ) )
                    {
                        if ( field.getAnnotation( AssignedBot.class ) != null )
                        {
                            field.setAccessible( true );
                            field.set( listener, this );
                        }
                    }
                }

                // Subscribe the instance, with references injected to the message bus
                connectionBus.subscribe( listener );
            }
            catch ( InstantiationException | IllegalAccessException | IllegalArgumentException
                            | InvocationTargetException | NoSuchMethodException | SecurityException e )
            {
                Logger.error( e, "Could not create instance of handler container '{0}'", handlerContainer );
            }
        }

        conn.connect();
        initialConnectionMade = true;
    }

    /**
     * Get the current nick ID of the bot on the server.
     * 
     * @return Nick string
     */
    public String getNick()
    {
        return nick;
    }

    /**
     * Get the full/real name of the bot on the server.
     * 
     * @return Real name string, can contain spaces.
     */
    public String getRealName()
    {
        return realName;
    }

    /**
     * Get the address of the server the bot is connected to.
     * 
     * @return Server address, can be a domain name or IP address.
     */
    public String getServerAddress()
    {
        return serverAddress;
    }

    /**
     * Get the port of the server the bot is connected to.
     * 
     * @return Port number
     */
    public int getServerPort()
    {
        return serverPort;
    }

    /**
     * Get the user name of the bot on the server.
     * 
     * @return User string
     */
    public String getUser()
    {
        return user;
    }

    /**
     * Get if SSL sockets are in use (or are going to be used).
     * 
     * @return true iff SSL sockets are in use
     */
    public boolean isSslEnabled()
    {
        return sslEnabled;
    }

    /**
     * Set the stored nick value, and send an update message to the server if connected.
     * 
     * @param nick A spaceless string, must start with an alphabetic character
     */
    public void setNick( String nick )
    {
        if ( initialConnectionMade )
        {
            try
            {
                connectionBus.publish( new SendNick( connectionBus, nick ) );
            }
            catch ( InvalidMessagePrefixException | InvalidMessageCommandException | InvalidMessageParamException e )
            {
                Logger.error( e, "Could not send nick message for nick '{0}'", nick );
            }
        }
        this.nick = nick;
    }

    /**
     * Set the full/real name of the bot on the server. No effect after connect() has been called.
     * 
     * @param realName Real name string, can contain spaces.
     */
    public void setRealName( String realName )
    {
        this.realName = realName;
    }

    /**
     * Set the address of the server the bot going to connect to. No effect after connect() has been called.
     * 
     * @param serverAddress Server address, can be a domain name or IP address.
     */
    public void setServerAddress( String serverAddress )
    {
        this.serverAddress = serverAddress;
    }

    /**
     * Set the port of the server the bot going to connect to. No effect after connect() has been called.
     * 
     * @param serverAddress Server port, has to be a valid port number.
     */
    public void setServerPort( int serverPort )
    {
        this.serverPort = serverPort;
    }

    /**
     * Set if SSL sockets are going to be used. No effect after connect() has been called.
     * 
     * @param sslEnabled true iff SSL sockets are to be used
     */
    public void setSslEnabled( boolean sslEnabled )
    {
        this.sslEnabled = sslEnabled;
    }

    /**
     * Set the user name the bot will use on the server. No effect after connect() has been called.
     * 
     * @param user User string
     */
    public void setUser( String user )
    {
        this.user = user;
    }

    /**
     * Get the set of current channels the bot is joined to.
     * 
     * @return Set of channel strings
     */
    public Set<Channel> getChannels()
    {
        return channels;
    }

    /**
     * Add a channel to the list of current channels the bot is joined to. Send an update message to the server if
     * connected.
     * 
     * @param channel The channel to add
     */
    public void addChannel( Channel channel )
    {
        if ( channels == null )
        {
            channels = new HashSet<Channel>();
        }

        if ( initialConnectionMade )
        {
            try
            {
                if ( channel.getKey() == null )
                    connectionBus.publish( new SendJoin( connectionBus, channel.getName() ) );
                else
                    connectionBus.publish( new SendJoin( connectionBus, channel.getName(), channel.getKey() ) );
            }
            catch ( InvalidMessagePrefixException | InvalidMessageCommandException | InvalidMessageParamException e )
            {
                Logger.error( e, "Could not send join message for channel '{0}'", channel );
            }
        }

        this.channels.add( channel );
    }

    /**
     * Add a channel to the list of current channels the bot is joined to. Send an update message to the server
     * immediately if connected. If a channel was added previously with a key, the key will be looked up automatically
     * when being re-added.
     * 
     * @param name Channel's name
     */
    public void addChannel( String name )
    {
        addChannel( name, null );
    }

    /**
     * Add a channel to the list of current channels the bot is joined to. Send an update message to the server
     * immediately if connected. If a channel was added previously with a key, and the key given is null, the key will
     * be looked up automatically when being re-added.
     * 
     * @param name Channel's name
     * @param key Channel's key if it exists, otherwise null
     */
    public void addChannel( String name, String key )
    {
        if ( channels == null )
        {
            channels = new HashSet<Channel>();
        }

        if ( key == null )
        {
            for ( Channel channel : channels )
            {
                if ( channel.getKey() != null && channel.getKey().equals( name ) )
                {
                    key = channel.getKey();
                    break;
                }
            }
        }

        addChannel( new Channel( name, key ) );
    }

    /**
     * Remove a channel from the list of current channels the bot is joined to. Send an update message to the server if
     * connected.
     * 
     * @param channel Channel to part from
     */
    public void remChannel( Channel channel )
    {
        remChannel( channel.getName() );
    }

    /**
     * Remove a channel from the list of current channels the bot is joined to. Send an update message to the server if
     * connected.
     * 
     * @param name Name of the channel to part from
     */
    public void remChannel( String name )
    {
        if ( initialConnectionMade )
        {
            try
            {
                connectionBus.publish( new SendPart( connectionBus, name ) );
            }
            catch ( InvalidMessagePrefixException | InvalidMessageCommandException | InvalidMessageParamException e )
            {
                Logger.error( e, "Could not send part message for channel '{0}'", name );
            }
        }
        channels.remove( name );
    }

    /**
     * Holds data for one channel
     */
    public static class Channel
    {
        private final String name;

        private final String key;

        /**
         * @param name The channel's name. Including the #. Cannot be null.
         * @param key The channel's key. May be null if there is no key.
         */
        public Channel( String name, String key )
        {
            this.name = name;
            this.key = key;
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
         * Compares Channel objects based on their names and keys. Two channels must have matching non-null names, the
         * keys may be both null, or equal. Comparing to a String will compare the channel name to the String. They must
         * be equal to match.
         */
        @Override
        public boolean equals( Object obj )
        {
            if ( obj instanceof Channel )
            {
                Channel otherChannel = (Channel) obj;
                if ( getName().equals( otherChannel.getName() ) )
                    if ( getKey() == null && otherChannel.getKey() == null )
                        return true;
                    else if ( getKey() == null || otherChannel.getKey() == null )
                        return false;
                    else if ( getKey().equals( otherChannel.getKey() ) )
                        return true;
            }
            else if ( obj instanceof String )
            {
                String channelName = (String) obj;
                if ( getName().equals( channelName ) )
                    return true;
            }

            return false;
        }

        @Override
        public String toString()
        {
            return "Channel [name=" + name + ", key=" + key + "]";
        }
    }
}
