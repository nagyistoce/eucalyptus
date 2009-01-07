package edu.ucsb.eucalyptus.admin.client;

import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.*;

import java.util.*;
import java.lang.*;

public class VmTypeTable extends VerticalPanel {

	private static Label statusLabel = new Label();
	private Grid grid = new Grid ();
	private List<VmTypeWeb> VmTypeList = new ArrayList<VmTypeWeb>();
	private static String sessionId;
	
	public VmTypeTable(String sessionId)
	{
		this.sessionId = sessionId;
		this.setSpacing (10);
		this.setVerticalAlignment(HasVerticalAlignment.ALIGN_MIDDLE);
		this.setHorizontalAlignment(HasHorizontalAlignment.ALIGN_CENTER);
		Label VmTypesHeader = new Label( "VM Types:" );
		VmTypesHeader.setStyleName ( "euca-section-header" );
		this.add ( VmTypesHeader );
		this.add ( this.grid );
		HorizontalPanel hpanel = new HorizontalPanel ();
		hpanel.setSpacing (5);
		hpanel.add ( new Button( "Save VmTypes", new SaveCallback( this ) ) );
		hpanel.add ( this.statusLabel );
		this.statusLabel.setWidth ("250");
		this.statusLabel.setText ("");
		this.statusLabel.setStyleName ("euca-greeting-pending");
		this.add ( hpanel );
		EucalyptusWebBackend.App.getInstance().getVmTypes( 
			this.sessionId, new GetCallback( this ) );
	}

	private void rebuildTable()
	{
		int rows = this.VmTypeList.size() + 1;
		this.grid.clear ();
		this.grid.resize ( rows, 5 );
		this.grid.setVisible (true);
		this.grid.setStyleName( "euca-table" );
		this.grid.setCellPadding( 6 );
		//this.grid.setWidget( 0, 0, new Label( "Enabled" ) );
		this.grid.setWidget( 0, 1, new Label( "Name" ) );
		this.grid.setWidget( 0, 2, new Label( "CPUs" ) );
		this.grid.setWidget( 0, 3, new Label( "Memory (MB)" ) );
		this.grid.setWidget( 0, 4, new Label( "Disk (GB)" ) );
		this.grid.getRowFormatter().setStyleName( 0, "euca-table-heading-row" );
		int row = 1;
		for ( VmTypeWeb VmType : this.VmTypeList ) {
			addVmTypeEntry (row++, VmType);
		}
	}

	private void addVmTypeEntry( int row, VmTypeWeb VmType )
	{			
		if ( ( row % 2 ) == 1 ) {
			this.grid.getRowFormatter().setStyleName( row, "euca-table-odd-row" );
		} else {
			this.grid.getRowFormatter().setStyleName( row, "euca-table-even-row" );
		}
				
		final CheckBox cb = new CheckBox();
		cb.addClickListener (new ChangeCallback (this, row));
		cb.setChecked( true ); // TODO: get this from server
		//this.grid.setWidget( row, 0, cb );
				
		final Label name_b = new Label ();
		name_b.setText( VmType.getName() );
		this.grid.setWidget( row, 1, name_b );
		
		final TextBox cpu_b = new TextBox();
		cpu_b.addChangeListener (new ChangeCallback (this, row));
		cpu_b.setVisibleLength( 2 );
		cpu_b.setText( "" + VmType.getCpu() );
		this.grid.setWidget( row, 2, cpu_b );
		this.grid.getCellFormatter().setHorizontalAlignment(row, 2, HasHorizontalAlignment.ALIGN_CENTER);

		final TextBox mem_b = new TextBox();
		mem_b.addChangeListener (new ChangeCallback (this, row));
		mem_b.setVisibleLength( 4 );
		mem_b.setText( "" + VmType.getMemory() );
		this.grid.setWidget( row, 3, mem_b );
		this.grid.getCellFormatter().setHorizontalAlignment(row, 3, HasHorizontalAlignment.ALIGN_CENTER);
		
		
		final TextBox disk_b = new TextBox();
		disk_b.addChangeListener (new ChangeCallback (this, row));
		disk_b.setVisibleLength( 4 );
		disk_b.setText( "" + VmType.getDisk() );
		this.grid.setWidget( row, 4, disk_b );	
		this.grid.getCellFormatter().setHorizontalAlignment(row, 4, HasHorizontalAlignment.ALIGN_CENTER);
					
	}

	public List<VmTypeWeb> getVmTypeList()
	{
		return VmTypeList;
	}

	public void setVmTypeList( final List<VmTypeWeb> VmTypeList )
	{
		this.VmTypeList = VmTypeList;
	}

	public void updateRow (int row)
	{
		VmTypeWeb VmType = this.VmTypeList.get (row-1); // table has a header row 
		VmType.setCpu    (Integer.parseInt(((TextBox)this.grid.getWidget(row, 2)).getText()));
		VmType.setMemory (Integer.parseInt(((TextBox)this.grid.getWidget(row, 3)).getText()));
		VmType.setDisk   (Integer.parseInt(((TextBox)this.grid.getWidget(row, 4)).getText()));
	}
	
	class ChangeCallback implements ChangeListener, ClickListener {
		private VmTypeTable parent;
		private int row;
		
		ChangeCallback ( final VmTypeTable parent, final int row )
		{
			this.parent = parent;
			this.row = row;
		}
		
		public void onChange (Widget sender) 
		{
			this.parent.updateRow (this.row);
			this.parent.statusLabel.setText ("Unsaved changes");
			this.parent.statusLabel.setStyleName ("euca-greeting-warning");
		}
		
		public void onClick (Widget sender) 
		{
			this.parent.updateRow (this.row);
			this.parent.statusLabel.setText ("Unsaved changes");
			this.parent.statusLabel.setStyleName ("euca-greeting-warning");
		}
	}
	
	class GetCallback implements AsyncCallback {

		private VmTypeTable parent;

		GetCallback( final VmTypeTable parent )
		{
			this.parent = parent;
		}

		public void onFailure( final Throwable throwable )
		{
			this.parent.statusLabel.setText ("Failed to contact server!");
			this.parent.statusLabel.setStyleName ("euca-greeting-error");
		}

		public void onSuccess( final Object o )
		{
			List<VmTypeWeb> newVmTypeList = (List<VmTypeWeb>) o;
			this.parent.VmTypeList = newVmTypeList;
			this.parent.rebuildTable(); 
		}
	}

	class SaveCallback implements AsyncCallback, ClickListener {

		private VmTypeTable parent;

		SaveCallback( final VmTypeTable parent )
		{
			this.parent = parent;
		}

		public void onClick( final Widget widget )
		{
			this.parent.statusLabel.setText ("Saving...");
			this.parent.statusLabel.setStyleName ("euca-greeting-pending");
			EucalyptusWebBackend.App.getInstance().setVmTypes( 
				this.parent.sessionId, this.parent.VmTypeList, this );
		}

		public void onFailure( final Throwable throwable )
		{
			this.parent.statusLabel.setText ("Failed to save! (Make sure values in each column are ordered.)");
			this.parent.statusLabel.setStyleName ("euca-greeting-error");
		}

		public void onSuccess( final Object o )
		{
			this.parent.statusLabel.setText ("Saved VM Types to server");
			this.parent.statusLabel.setStyleName ("euca-greeting-disabled");
			EucalyptusWebBackend.App.getInstance().getVmTypes( 
				this.parent.sessionId, new GetCallback( this.parent ) ); // so the order will refresh
		}
	}
}
