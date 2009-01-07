package edu.ucsb.eucalyptus.msgs;

import edu.ucsb.eucalyptus.msgs.EucalyptusMessage
import edu.ucsb.eucalyptus.msgs.EucalyptusData
import edu.ucsb.eucalyptus.annotation.HttpParameterMapping

public class VmAddressMessage extends EucalyptusMessage{}
/** *******************************************************************************/
public class AllocateAddressType extends VmAddressMessage {} //** added 2008-02-01  **/
public class AllocateAddressResponseType extends VmAddressMessage { //** added 2008-02-01  **/
  String publicIp;
}
/** *******************************************************************************/
public class ReleaseAddressType extends VmAddressMessage { //** added 2008-02-01  **/
  String publicIp;
}
public class ReleaseAddressResponseType extends VmAddressMessage { //** added 2008-02-01  **/
  boolean _return;
}
/** *******************************************************************************/
public class DescribeAddressesType extends VmAddressMessage { //** added 2008-02-01  **/
  @HttpParameterMapping (parameter = "PublicIp")
  ArrayList<String> publicIpsSet = new ArrayList<String>();
}
public class DescribeAddressesResponseType extends VmAddressMessage { //** added 2008-02-01  **/
  ArrayList<DescribeAddressesResponseItemType> addressesSet = new ArrayList<DescribeAddressesResponseItemType>();
}
/** *******************************************************************************/
public class AssociateAddressType extends VmAddressMessage { //** added 2008-02-01  **/
  String publicIp;
  String instanceId;
}
public class AssociateAddressResponseType extends VmAddressMessage { //** added 2008-02-01  **/
  boolean _return;
}
/** *******************************************************************************/
public class DisassociateAddressType extends VmAddressMessage {  //** added 2008-02-01  **/
  String publicIp;
}
public class DisassociateAddressResponseType extends VmAddressMessage { //** added 2008-02-01  **/
  boolean _return;
}
/** *******************************************************************************/
public class DescribeAddressesResponseItemType extends EucalyptusData {  //** added 2008-02-01  **/
  String publicIp;
  String instanceId;

  def DescribeAddressesResponseItemType(final publicIp, final instanceId)
  {
    this.publicIp = publicIp;
    this.instanceId = instanceId;
  }

  def DescribeAddressesResponseItemType()
  {
  }

}


