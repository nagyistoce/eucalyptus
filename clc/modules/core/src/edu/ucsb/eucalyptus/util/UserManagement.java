/*
 * Software License Agreement (BSD License)
 *
 * Copyright (c) 2008, Regents of the University of California
 * All rights reserved.
 *
 * Redistribution and use of this software in source and binary forms, with or
 * without modification, are permitted provided that the following conditions
 * are met:
 *
 * * Redistributions of source code must retain the above
 *   copyright notice, this list of conditions and the
 *   following disclaimer.
 *
 * * Redistributions in binary form must reproduce the above
 *   copyright notice, this list of conditions and the
 *   following disclaimer in the documentation and/or other
 *   materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 *
 * Author: Chris Grzegorczyk grze@cs.ucsb.edu
 */

package edu.ucsb.eucalyptus.util;

import edu.ucsb.eucalyptus.cloud.entities.*;
import edu.ucsb.eucalyptus.keys.Hashes;
import org.apache.log4j.Logger;

import java.util.List;
import java.security.NoSuchAlgorithmException;

public class UserManagement {

  private static Logger LOG = Logger.getLogger( UserManagement.class );
  private static String keyPath = SubDirectory.KEYS.toString();

  public static UserInfo generateAdmin()
  {
    UserInfo admin = new UserInfo( "admin" );
    admin.setUserName( admin.getUserName() );
    admin.setEmail( "" );
    admin.setRealName( "" );
    admin.setTelephoneNumber( "" );

    admin.setAffiliation( "" );
    admin.setProjectDescription( "" );
    admin.setProjectPIName( "" );

    admin.setPasswordExpires( 0L );  /* must be changed upon login */
    try
    {
      admin.setBCryptedPassword( Hashes.hashPassword( admin.getUserName() ) );
    }
    catch ( NoSuchAlgorithmException e )
    {
    }

    admin.setConfirmationCode( UserManagement.generateConfirmationCode( admin.getUserName() ) );
    admin.setCertificateCode( UserManagement.generateCertificateCode( admin.getUserName() ) );

    admin.setSecretKey( UserManagement.generateSecretKey( admin.getUserName() ) );
    admin.setQueryId( UserManagement.generateQueryId( admin.getUserName() ) );

    admin.setReservationId( 0l );

    admin.setIsApproved( true );
    admin.setIsConfirmed( true );
    admin.setIsEnabled( true );
    admin.setIsAdministrator( true );

    admin.getNetworkRulesGroup().add( NetworkRulesGroup.getDefaultGroup() );

    return admin;
  }

  public static String generateConfirmationCode( String userName )
  {
    return Hashes.getDigestBase64( userName, Hashes.Digest.SHA512, true ).replaceAll( "\\.", "" );
  }

  public static String generateCertificateCode( String userName )
  {
    return Hashes.getDigestBase64( userName, Hashes.Digest.SHA512, true ).replaceAll( "\\.", "" );
  }

  public static String generateSecretKey( String userName )
  {
    return Hashes.getDigestBase64( userName, Hashes.Digest.SHA224, true ).replaceAll( "\\.", "" );
  }

  public static String generateQueryId( String userName )
  {
    return Hashes.getDigestBase64( userName, Hashes.Digest.MD5, false ).replaceAll( "\\.", "" );
  }

  public static boolean isAdministrator( String userId )
  {
    if( EucalyptusProperties.NAME.equals( userId ) || WalrusProperties.ADMIN.equals( userId ) ) return true;
    if ( userId != null )
    {
      EntityWrapper<UserInfo> db = new EntityWrapper<UserInfo>();
      UserInfo searchUser = new UserInfo( userId );
      List<UserInfo> userInfoList = db.query( searchUser );
      if ( userInfoList.size() > 0 )
      {
        UserInfo foundUser = userInfoList.get( 0 );
        if ( foundUser.isAdministrator() )
        {
          return true;
        }
      }
    }
    return false;
  }
}
