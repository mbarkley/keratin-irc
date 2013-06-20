/**
 * Copyright (C) 2013 Alexander Szczuczko
 *
 * This file may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */
package ca.szc.keratin.core.net.io;

import java.nio.charset.Charset;

/**
 * Contains static input/output configuration
 */
public class IoConfig
{

    private IoConfig()
    {
    }

    public static final Charset CHARSET = Charset.forName( "UTF-8" );

    public static final int WAIT_TIME = 5000;

}