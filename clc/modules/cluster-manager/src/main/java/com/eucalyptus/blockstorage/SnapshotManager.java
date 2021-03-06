/*************************************************************************
 * Copyright 2009-2015 Eucalyptus Systems, Inc.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; version 3 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see http://www.gnu.org/licenses/.
 *
 * Please contact Eucalyptus Systems, Inc., 6755 Hollister Ave., Goleta
 * CA 93117, USA or visit http://www.eucalyptus.com/licenses/ if you need
 * additional information or have any questions.
 *
 * This file may incorporate work covered under the following copyright
 * and permission notice:
 *
 *   Software License Agreement (BSD License)
 *
 *   Copyright (c) 2008, Regents of the University of California
 *   All rights reserved.
 *
 *   Redistribution and use of this software in source and binary forms,
 *   with or without modification, are permitted provided that the
 *   following conditions are met:
 *
 *     Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *
 *     Redistributions in binary form must reproduce the above copyright
 *     notice, this list of conditions and the following disclaimer
 *     in the documentation and/or other materials provided with the
 *     distribution.
 *
 *   THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 *   "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 *   LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS
 *   FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 *   COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
 *   INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
 *   BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 *   LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 *   CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
 *   LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN
 *   ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 *   POSSIBILITY OF SUCH DAMAGE. USERS OF THIS SOFTWARE ACKNOWLEDGE
 *   THE POSSIBLE PRESENCE OF OTHER OPEN SOURCE LICENSED MATERIAL,
 *   COPYRIGHTED MATERIAL OR PATENTED MATERIAL IN THIS SOFTWARE,
 *   AND IF ANY SUCH MATERIAL IS DISCOVERED THE PARTY DISCOVERING
 *   IT MAY INFORM DR. RICH WOLSKI AT THE UNIVERSITY OF CALIFORNIA,
 *   SANTA BARBARA WHO WILL THEN ASCERTAIN THE MOST APPROPRIATE REMEDY,
 *   WHICH IN THE REGENTS' DISCRETION MAY INCLUDE, WITHOUT LIMITATION,
 *   REPLACEMENT OF THE CODE SO IDENTIFIED, LICENSING OF THE CODE SO
 *   IDENTIFIED, OR WITHDRAWAL OF THE CODE CAPABILITY TO THE EXTENT
 *   NEEDED TO COMPLY WITH ANY SUCH LICENSES OR RIGHTS.
 ************************************************************************/

package com.eucalyptus.blockstorage;

import static com.eucalyptus.compute.common.ImageMetadata.State.available;
import static com.eucalyptus.compute.common.ImageMetadata.State.pending;
import static com.eucalyptus.images.Images.inState;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;

import javax.persistence.EntityTransaction;
import javax.persistence.PersistenceException;

import com.eucalyptus.auth.Accounts;
import com.eucalyptus.compute.ClientUnauthorizedComputeException;
import com.eucalyptus.compute.ComputeException;
import com.eucalyptus.compute.common.CreateVolumePermissionItemType;
import com.eucalyptus.compute.common.ResourceTag;
import com.eucalyptus.compute.common.backend.CreateSnapshotResponseType;
import com.eucalyptus.compute.common.backend.CreateSnapshotType;
import com.eucalyptus.compute.common.backend.DeleteSnapshotResponseType;
import com.eucalyptus.compute.common.backend.DeleteSnapshotType;
import com.eucalyptus.compute.common.backend.DescribeSnapshotAttributeResponseType;
import com.eucalyptus.compute.common.backend.DescribeSnapshotAttributeType;
import com.eucalyptus.compute.common.backend.DescribeSnapshotsResponseType;
import com.eucalyptus.compute.common.backend.DescribeSnapshotsType;
import com.eucalyptus.compute.common.backend.ModifySnapshotAttributeResponseType;
import com.eucalyptus.compute.common.backend.ModifySnapshotAttributeType;
import com.eucalyptus.compute.common.backend.ResetSnapshotAttributeResponseType;
import com.eucalyptus.compute.common.backend.ResetSnapshotAttributeType;
import com.eucalyptus.entities.TransactionResource;
import com.eucalyptus.ws.EucalyptusWebServiceException;
import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.base.Objects;
import com.google.common.collect.Lists;

import org.apache.log4j.Logger;

import com.eucalyptus.auth.AuthException;
import com.eucalyptus.auth.principal.AccountFullName;
import com.eucalyptus.blockstorage.exceptions.SnapshotTooLargeException;
import com.eucalyptus.blockstorage.msgs.DeleteStorageSnapshotResponseType;
import com.eucalyptus.blockstorage.msgs.DeleteStorageSnapshotType;
import com.eucalyptus.compute.common.CloudMetadatas;
import com.eucalyptus.cloud.util.DuplicateMetadataException;
import com.eucalyptus.component.NoSuchComponentException;
import com.eucalyptus.component.Partitions;
import com.eucalyptus.component.ServiceConfiguration;
import com.eucalyptus.component.Topology;
import com.eucalyptus.component.id.Eucalyptus;
import com.eucalyptus.compute.ClientComputeException;
import com.eucalyptus.compute.identifier.InvalidResourceIdentifier;
import com.eucalyptus.compute.identifier.ResourceIdentifiers;
import com.eucalyptus.context.Context;
import com.eucalyptus.context.Contexts;
import com.eucalyptus.context.IllegalContextAccessException;
import com.eucalyptus.entities.Entities;
import com.eucalyptus.entities.TransactionException;
import com.eucalyptus.entities.Transactions;
import com.eucalyptus.event.ListenerRegistry;
import com.eucalyptus.images.Images;
import com.eucalyptus.records.Logs;
import com.eucalyptus.reporting.event.EventActionInfo;
import com.eucalyptus.reporting.event.SnapShotEvent;
import com.eucalyptus.reporting.event.SnapShotEvent.SnapShotAction;
import com.eucalyptus.system.Threads;
import com.eucalyptus.tags.Filter;
import com.eucalyptus.tags.Filters;
import com.eucalyptus.tags.Tag;
import com.eucalyptus.tags.TagSupport;
import com.eucalyptus.tags.Tags;
import com.eucalyptus.util.EucalyptusCloudException;
import com.eucalyptus.util.Exceptions;
import com.eucalyptus.util.RestrictedTypes;
import com.eucalyptus.util.async.AsyncRequests;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.base.Strings;
import com.google.common.base.Supplier;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;

public class SnapshotManager {
  
  static Logger LOG       = Logger.getLogger( SnapshotManager.class );
  static String ID_PREFIX = "snap";
  
  public CreateSnapshotResponseType create( final CreateSnapshotType request ) throws EucalyptusCloudException, NoSuchComponentException, DuplicateMetadataException, AuthException, IllegalContextAccessException, NoSuchElementException, PersistenceException, TransactionException {
    final Context ctx = Contexts.lookup( );
    final String volumeId = normalizeVolumeIdentifier( request.getVolumeId( ) );
    final Volume vol;
    try {
      vol = Transactions.find( Volume.named( ctx.getUserFullName( ).asAccountFullName( ), volumeId ) );
    } catch ( NoSuchElementException e ) {
      throw new ClientComputeException( "InvalidVolume.NotFound", "Volume not found '" + request.getVolumeId( ) + "'" );
    }
    final ServiceConfiguration sc = Topology.lookup( Storage.class, Partitions.lookupByName( vol.getPartition( ) ) );
    final Volume volReady = Volumes.checkVolumeReady( vol );
    Supplier<Snapshot> allocator = new Supplier<Snapshot>( ) {
      
      @Override
      public Snapshot get( ) {
        try {
          return Snapshots.initializeSnapshot( ctx.getUserFullName( ), volReady, sc, request.getDescription() );
        } catch ( EucalyptusCloudException ex ) {
          throw new RuntimeException( ex );
        }
      }
    };
    Snapshot snap = RestrictedTypes.allocateUnitlessResource( allocator );
    try {
      snap = Snapshots.startCreateSnapshot( volReady, snap );
    } catch ( Exception e ) {
      final EntityTransaction db = Entities.get( Snapshot.class );
      try {
        Snapshot entity = Entities.uniqueResult( snap );
        Entities.delete( entity );
        db.commit();
      } catch ( Exception ex ) {
        Logs.extreme( ).error( ex , ex );
      } finally {
        if ( db.isActive() ) db.rollback( );
      }
      final SnapshotTooLargeException snapshotTooLargeException =
          Exceptions.findCause( e, SnapshotTooLargeException.class );
      if ( snapshotTooLargeException != null ) {
        throw new ClientComputeException(
            "SnapshotLimitExceeded", snapshotTooLargeException.getMessage( ) );
      }
      if ( !( e.getCause( ) instanceof ExecutionException ) ) {
        throw handleException( e );
      } else {
        throw e;
      }
    }

    try {
      fireUsageEvent( snap, SnapShotEvent.forSnapShotCreate( snap.getVolumeSize(), volReady.getNaturalId(), snap.getDisplayName() ) );
    } catch (Throwable reportEx) {
      LOG.error("Unable to fire snap shot creation reporting event", reportEx);
    }
    
    CreateSnapshotResponseType reply = ( CreateSnapshotResponseType ) request.getReply( );
    com.eucalyptus.compute.common.Snapshot snapMsg = snap.morph( new com.eucalyptus.compute.common.Snapshot( ) );
    snapMsg.setProgress( "0%" );
    snapMsg.setOwnerId( snap.getOwnerAccountNumber( ) );
    snapMsg.setVolumeSize( volReady.getSize( ).toString( ) );
    reply.setSnapshot( snapMsg );
    return reply;
  }

  public DeleteSnapshotResponseType delete( final DeleteSnapshotType request ) throws EucalyptusCloudException {
    final DeleteSnapshotResponseType reply = ( DeleteSnapshotResponseType ) request.getReply( );
    final Context ctx = Contexts.lookup( );
    final String snapshotId = normalizeSnapshotIdentifier( request.getSnapshotId( ) );
    Predicate<Snapshot> deleteSnapshot = new Predicate<Snapshot>( ) {
      
      @Override
      public boolean apply( Snapshot snap ) {
        if ( !State.EXTANT.equals( snap.getState( ) ) && !State.FAIL.equals( snap.getState( ) ) ) {
          return false;
        } else if ( !RestrictedTypes.filterPrivileged( ).apply( snap ) ) {
          throw Exceptions.toUndeclared( new ClientUnauthorizedComputeException( "Not authorized to delete snapshot " + request.getSnapshotId( ) + " by " + ctx.getUser( ).getName( ) ) );
        } else if ( isReservedSnapshot( snapshotId ) ) {
          throw Exceptions.toUndeclared( new ClientComputeException( "InvalidSnapshot.InUse", "Snapshot " + request.getSnapshotId( ) + " is in use, deletion not permitted" ) );
        } else {
          fireUsageEvent(snap, SnapShotEvent.forSnapShotDelete());
          final String partition = snap.getPartition();
          final String snapshotId = snap.getDisplayName( );
          Callable<Boolean> deleteBroadcast = new Callable<Boolean>( ) {
            public Boolean call( ) {
              final DeleteStorageSnapshotType deleteMsg = new DeleteStorageSnapshotType( snapshotId );
              return Iterables.all( Topology.enabledServices( Storage.class ), new Predicate<ServiceConfiguration>( ) {
                
                @Override
                public boolean apply( ServiceConfiguration arg0 ) {
                  if ( !arg0.getPartition( ).equals( partition ) ) {
                    try {
                      AsyncRequests.sendSync( arg0, deleteMsg );
                    } catch ( Exception ex ) {
                      LOG.error( ex );
                      Logs.extreme( ).error( ex, ex );
                    }
                  }
                  return true;
                }
              } );
            }
          };
          
          ServiceConfiguration sc = null;
          try{
            sc = Topology.lookup( Storage.class, Partitions.lookupByName( snap.getPartition( ) ) );
          }catch(final Exception ex){
            sc= null;
          }
          if(sc!=null){
            try {
              DeleteStorageSnapshotResponseType scReply = AsyncRequests.sendSync( sc, new DeleteStorageSnapshotType( snap.getDisplayName( ) ) );
              if ( scReply.get_return( ) ) {
                Threads.enqueue( Eucalyptus.class, Snapshots.class, deleteBroadcast );
              } else {
                throw Exceptions.toUndeclared( new EucalyptusCloudException( "Unable to delete snapshot: " + snap ) );
              }
            } catch ( Exception ex1 ) {
              throw Exceptions.toUndeclared( ex1.getMessage( ), ex1 );
            }
          }else{
            Threads.enqueue( Eucalyptus.class, Snapshots.class, deleteBroadcast );
          }
          return true;
        }
      }
    };
    boolean result = false;
    try {
      result = Transactions.delete( Snapshot.named( ctx.getUserFullName( ).asAccountFullName( ), snapshotId ), deleteSnapshot);
    } catch ( NoSuchElementException ex2 ) {
      try {
        result = Transactions.delete( Snapshot.named( null, snapshotId ), deleteSnapshot );
      } catch ( ExecutionException ex3 ) {
        throw handleException( ex3.getCause() );
      } catch ( NoSuchElementException ex4 ) {
        throw new ClientComputeException( "InvalidSnapshot.NotFound", "The snapshot '"+request.getSnapshotId( )+"' does not exist." );
      }
    } catch ( ExecutionException ex1 ) {
      throw new EucalyptusCloudException( ex1.getCause( ) );
    }
    reply.set_return( result );
    return reply;
  }
  
  public DescribeSnapshotsResponseType describe( final DescribeSnapshotsType request ) throws EucalyptusCloudException {
    final DescribeSnapshotsResponseType reply = request.getReply( );
    final Context ctx = Contexts.lookup( );
    final String requestAccountId = ctx.getUserFullName( ).getAccountNumber( );
    // verbose does not have any special functionality for snapshots, but is ignored when passed as an identifier
    request.getSnapshotSet().remove( "verbose" );
    final Set<String> snapshotIds = Sets.newHashSet( normalizeSnapshotIdentifiers( request.getSnapshotSet( ) ) );
    final List<String> ownersSet = request.getOwnersSet( );
    if ( ownersSet.remove( Snapshots.SELF ) ) {
      ownersSet.add( requestAccountId );
    }
    final Filter filter = Filters.generate( request.getFilterSet(), Snapshot.class );
    try ( final TransactionResource tx = Entities.transactionFor( Snapshot.class ) ){
      final List<Snapshot> unfilteredSnapshots =
          Entities.query( Snapshot.named( null, null ), true, filter.asCriterion(), filter.getAliases() );
      final Predicate<? super Snapshot> requestedAndAccessible = CloudMetadatas.filteringFor( Snapshot.class )
          .byId( snapshotIds )
          .byOwningAccount( request.getOwnersSet( ) )
          .byPredicate( Snapshots.filterRestorableBy( request.getRestorableBySet( ), requestAccountId ) )
          .byPredicate( filter.asPredicate( ) )
          .byPredicate( Snapshots.FilterPermissions.INSTANCE )
          .byPrivilegesWithoutOwner( )
          .buildPredicate( );

      final Iterable<Snapshot> snapshots = Iterables.filter( unfilteredSnapshots, requestedAndAccessible );
      final Map<String,List<Tag>> tagsMap = TagSupport.forResourceClass( Snapshot.class )
          .getResourceTagMap( AccountFullName.getInstance( ctx.getAccountNumber( ) ),
              Iterables.transform( snapshots, CloudMetadatas.toDisplayName() ) );
      for ( final Snapshot snap : snapshots ) {
        try {
          final com.eucalyptus.compute.common.Snapshot snapReply = snap.morph( new com.eucalyptus.compute.common.Snapshot( ) );
          Tags.addFromTags( snapReply.getTagSet(), ResourceTag.class, tagsMap.get( snapReply.getSnapshotId() ) );
          snapReply.setVolumeId( snap.getParentVolume( ) );
          snapReply.setOwnerId( snap.getOwnerAccountNumber( ) );
          reply.getSnapshotSet( ).add( snapReply );
        } catch ( NoSuchElementException e ) {
          LOG.warn( "Error getting snapshot information from the Storage Controller: " + e );
          LOG.debug( e, e );
        }
      }
    }
    return reply;
  }
  
    public ResetSnapshotAttributeResponseType resetSnapshotAttribute( 
        final ResetSnapshotAttributeType request 
    ) throws EucalyptusCloudException {
      final ResetSnapshotAttributeResponseType reply = request.getReply( );
      final Context ctx = Contexts.lookup( );
      final String snapshotId = normalizeSnapshotIdentifier( request.getSnapshotId( ) );
      final Function<String, Boolean> resetSnapshotAttribute = new Function<String,Boolean>( ) {
        @Override
        public Boolean apply( final String snapshotId ) {
          try {
            final Snapshot snap = Entities.uniqueResult( Snapshot.named( 
                ctx.isAdministrator( ) ? null : ctx.getUserFullName( ).asAccountFullName( ), 
                snapshotId ) );
            //Can only reset attributes of snapshots in 'creating' or 'available' state
            if ( !State.EXTANT.equals( snap.getState() ) && !State.GENERATING.equals( snap.getState() ) ) {
              return false;
            } else if ( !canModifySnapshot( snap ) ) {
              throw Exceptions.toUndeclared( new ClientComputeException( 
                  "AuthFailure", 
                  "Not authorized to reset attribute for snapshot " + request.getSnapshotId( ) ) );
            } else if ( request.getCreateVolumePermission() != null && 
                ( "".equals( request.getCreateVolumePermission() ) || 
                    "createVolumePermission".equals( request.getCreateVolumePermission() ) ) ) {
              snap.setSnapshotPublic( false );
              snap.setPermissions( Sets.<String>newHashSet() );
            }
            return true;
          } catch ( TransactionException e ) {
            throw Exceptions.toUndeclared( e );
          }
        }
      };

      try {
        reply.set_return( Entities.asDistinctTransaction( Snapshot.class, resetSnapshotAttribute ).apply( snapshotId ) );
      } catch ( NoSuchElementException e ) {
        throw new ClientComputeException( 
            "InvalidSnapshot.NotFound", 
            "The snapshot '"+request.getSnapshotId( )+"' does not exist." );
      } catch ( Exception e ) {
        Exceptions.findAndRethrow( e, EucalyptusCloudException.class, EucalyptusWebServiceException.class );
        throw new EucalyptusCloudException( Objects.firstNonNull( e.getCause( ), e ) );
      }

      return reply;
    }

    private boolean validateGroup(String groupName) {
        return "all".equals(groupName);
    }

    private static List<String> verifyAccountIds(final List<String> accountIds) throws EucalyptusCloudException {
        final Set<String> validUserIds = Sets.newHashSet( );
        for ( String userId : accountIds ) {
            try {
                validUserIds.add( Accounts.lookupAccountById( userId ).getAccountNumber() );
            } catch ( final Exception e ) {
                throw new EucalyptusCloudException( "Not a valid userId : " + userId );
            }
        }
        return Lists.newArrayList( validUserIds );
    }

    private static boolean canModifySnapshot( final Snapshot snap ) {
        final Context ctx = Contexts.lookup( );
        final String requestAccountId = ctx.getUserFullName( ).getAccountNumber( );
        return
                ( ctx.isAdministrator( ) || snap.getOwnerAccountNumber( ).equals( requestAccountId ) ) &&
                        RestrictedTypes.filterPrivileged( ).apply( snap );
    }

    public ModifySnapshotAttributeResponseType modifySnapshotAttribute( final ModifySnapshotAttributeType request ) throws EucalyptusCloudException {
        ModifySnapshotAttributeResponseType reply = request.getReply( );
        final Context ctx = Contexts.lookup();
        final String snapshotId = normalizeSnapshotIdentifier( request.getSnapshotId( ) );
        Function<Snapshot, Boolean> modifySnapshotAttribute = Functions.forPredicate(new Predicate<Snapshot>( ) {

            @Override
            public boolean apply( Snapshot snap ) {
                //Can only modify attributes of snapshots in 'creating' or 'available' state
                if ( !State.EXTANT.equals( snap.getState( ) ) && !State.GENERATING.equals( snap.getState( ) ) ) {
                    return false;
                } else if ( !canModifySnapshot(snap)) {
                    throw Exceptions.toUndeclared( new EucalyptusCloudException( "Not authorized to modify attribute for snapshot " + request.getSnapshotId( ) + " by " + ctx.getUser( ).getName( ) ) );
                } else {
                    switch ( request.snapshotAttribute() ) {
                        case CreateVolumePermission:
                            //do adds
                            try {
                                snap.addPermissions(verifyAccountIds(request.addUserIds()));
                            } catch (EucalyptusCloudException e) {
                                LOG.warn("Failed validating accountIds", e);
                                throw Exceptions.toUndeclared(e);
                            }
                            if (request.addGroupAll()) {
                                snap.setSnapshotPublic(true);
                            }


                            //do removes
                            snap.removePermissions(request.removeUserIds());
                            if (request.removeGroupAll()) {
                                snap.setSnapshotPublic(false);
                            }
                            break;
                        case ProductCode:
                            for ( String productCode : request.getProductCodes( ) ) {
                                snap.addProductCode( productCode );
                            }
                            break;
                    }
                    return true;
                }
            }
        });

        final boolean result;
        try {
            result = Transactions.one(
                Snapshot.named(
                    ctx.isAdministrator( ) ? null : ctx.getUserFullName( ).asAccountFullName( ),
                    snapshotId ),
                modifySnapshotAttribute );
        } catch ( NoSuchElementException ex2 ) {
            throw new ClientComputeException( "InvalidSnapshot.NotFound", "The snapshot '"+request.getSnapshotId( )+"' does not exist." );
        } catch ( ExecutionException ex1 ) {
            throw new EucalyptusCloudException( ex1.getCause( ) );
        }
        reply.set_return( result );
        return reply;
    }

    public DescribeSnapshotAttributeResponseType describeSnapshotAttribute( DescribeSnapshotAttributeType request ) throws EucalyptusCloudException {
        DescribeSnapshotAttributeResponseType reply = request.getReply( );
        reply.setSnapshotId(request.getSnapshotId());
        final Context ctx = Contexts.lookup( );
        final String snapshotId = normalizeSnapshotIdentifier( request.getSnapshotId( ) );
        try (TransactionResource db = Entities.transactionFor(Snapshot.class)) {
            Snapshot result = Entities.uniqueResult( Snapshot.named(
                ctx.isAdministrator( ) ? null : ctx.getUserFullName( ).asAccountFullName( ),
                snapshotId ) );
            if( !RestrictedTypes.filterPrivileged( ).apply( result ) ) {
                throw new EucalyptusCloudException("Not authorized to describe attributes for snapshot " + request.getSnapshotId());
            }

            ArrayList<CreateVolumePermissionItemType> permissions = Lists.newArrayList();
            for(String id : result.getPermissions()) {
                permissions.add(new CreateVolumePermissionItemType(id, null));
            }
            if(result.getSnapshotPublic()) {
                permissions.add(new CreateVolumePermissionItemType(null, "all"));
            }

            if(result.getProductCodes() != null) {
                reply.setProductCodes(new ArrayList<String>(result.getProductCodes()));
            }
            reply.setCreateVolumePermission(permissions);
        } catch ( NoSuchElementException ex2 ) {
            throw new ClientComputeException( "InvalidSnapshot.NotFound", "The snapshot '"+request.getSnapshotId( )+"' does not exist." );
        } catch ( ExecutionException ex1 ) {
            throw new EucalyptusCloudException( ex1.getCause( ) );
        }
        return reply;
    }

  private static boolean isReservedSnapshot( final String snapshotId ) {
	// Fix for EUCA-4932. Any snapshot associated with an (available or pending) image as a root/non-root device is a reserved snapshot
	// and can't be deleted without first unregistering the image
    return Predicates.or( SnapshotInUseVerifier.INSTANCE ).apply( snapshotId );
  }

  private static String normalizeIdentifier( final String identifier,
                                             final String prefix,
                                             final boolean required,
                                             final String message ) throws ClientComputeException {
    try {
      return Strings.emptyToNull( identifier ) == null && !required ?
          null :
          ResourceIdentifiers.parse( prefix, identifier ).getIdentifier( );
    } catch ( final InvalidResourceIdentifier e ) {
      throw new ClientComputeException( "InvalidParameterValue", String.format( message, e.getIdentifier( ) ) );
    }
  }

  private static String normalizeSnapshotIdentifier( final String identifier ) throws EucalyptusCloudException {
    return normalizeIdentifier(
        identifier, ID_PREFIX, true, "Value (%s) for parameter snapshotId is invalid. Expected: 'snap-...'." );
  }

  private static String normalizeVolumeIdentifier( final String identifier ) throws EucalyptusCloudException {
    return normalizeIdentifier(
        identifier, Volumes.ID_PREFIX, true, "Value (%s) for parameter volume is invalid. Expected: 'vol-...'." );
  }

  private static List<String> normalizeSnapshotIdentifiers( final List<String> identifiers ) throws EucalyptusCloudException {
    try {
      return ResourceIdentifiers.normalize( ID_PREFIX, identifiers );
    } catch ( final InvalidResourceIdentifier e ) {
      throw new ClientComputeException(
          "InvalidParameterValue",
          "Value ("+e.getIdentifier()+") for parameter snapshots is invalid. Expected: 'snap-...'." );
    }
  }

  private enum ImageSnapshotReservation implements Predicate<String> {
    INSTANCE;

    @Override
    public boolean apply( final String identifier ) {
      return Iterables.any(
          Entities.query(Images.exampleBlockStorageWithSnapshotId(identifier), true),
          inState(EnumSet.of( pending, available ) ) );
    }
  }
  
  /**
   * <p>Predicate to check if a snapshot is associated with a pending or available boot from ebs image.
   * Returns true if the snapshot is used in the image registration with root or non root devices.</p>
   */
  private enum SnapshotInUseVerifier implements Predicate<String> {
	INSTANCE;

	@Override
	public boolean apply( final String identifier ) {
	  return Iterables.any(
	    Entities.query(Images.exampleBSDMappingWithSnapshotId(identifier), true),
	    Images.imageInState(EnumSet.of( pending, available ) ) );
	}
  }
  
  private static void fireUsageEvent( final Snapshot snap,
                                      final EventActionInfo<SnapShotAction> actionInfo ) {
    try {
      ListenerRegistry.getInstance().fireEvent(
          SnapShotEvent.with(actionInfo, snap.getNaturalId(), snap.getDisplayName(), snap.getOwner().getUserId() ));
    } catch (final Throwable e) {
      LOG.error(e, e);
    }
  }

  /**
   * Method always throws, signature allows use of "throw handleException ..."
   */
  private static ComputeException handleException( final Throwable e ) throws ComputeException {
    final ComputeException cause = Exceptions.findCause( e, ComputeException.class );
    if ( cause != null ) {
      throw cause;
    }

    LOG.error( e, e );

    final ComputeException exception = new ComputeException( "InternalError", String.valueOf( e.getMessage( ) ) );
    if ( Contexts.lookup( ).hasAdministrativePrivileges() ) {
      exception.initCause( e );
    }
    throw exception;
  }
}
