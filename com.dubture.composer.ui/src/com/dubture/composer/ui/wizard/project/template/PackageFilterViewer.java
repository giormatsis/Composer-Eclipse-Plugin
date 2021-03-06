package com.dubture.composer.ui.wizard.project.template;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.equinox.internal.p2.ui.discovery.util.ControlListItem;
import org.eclipse.equinox.internal.p2.ui.discovery.util.ControlListViewer;
import org.eclipse.equinox.internal.p2.ui.discovery.util.FilteredViewer;
import org.eclipse.equinox.internal.p2.ui.discovery.wizards.DiscoveryResources;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.StructuredViewer;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;

import com.dubture.composer.core.log.Logger;
import com.dubture.getcomposer.core.MinimalPackage;
import com.dubture.getcomposer.packages.AsyncPackagistSearch;
import com.dubture.getcomposer.packages.PackageSearchListenerInterface;
import com.dubture.getcomposer.packages.SearchResult;

/**
 * 
 * @author Robert Gruendler <r.gruendler@gmail.com>
 *
 */
@SuppressWarnings("restriction")
public class PackageFilterViewer extends FilteredViewer implements PackageFilterChangedListener {

	protected final static Object[] EMTPY = new Object[0];
	private DiscoveryResources resources;
	private Button showProjectsCheckbox;
	private PackagistContentProvider contentProvider;
	private PackageFilterItem currentSelection = null;
	private List<PackageFilterChangedListener> listeners = new ArrayList<PackageFilterChangedListener>();
	private Label searchResultCount;
	
	@Override
	public void createControl(Composite parent) {
		super.createControl(parent);
		doFind("");
		
	}
	
	public void addChangeListener(PackageFilterChangedListener listener) {
		if (!listeners.contains(listener)) {
			listeners.add(listener);
		}
	}
	
	@Override
	protected StructuredViewer doCreateViewer(Composite container) {
		
		resources = new DiscoveryResources(getControl().getDisplay());
		StructuredViewer viewer = new ControlListViewer(container, SWT.BORDER) {
			@Override
			protected ControlListItem<?> doCreateItem(Composite parent, Object element) {
				return doCreateViewerItem(parent, element);
			}
		};
		
		viewer.setContentProvider(contentProvider = new PackagistContentProvider());
		
		searchResultCount = new Label(container, SWT.NONE);
		searchResultCount.setText("asdfasdf");
		GridDataFactory.fillDefaults().grab(true, false).span(3,1).align(SWT.BEGINNING, SWT.CENTER).hint(400, SWT.DEFAULT).applyTo(searchResultCount);
		
		return viewer;
	}
	
	@Override
	protected void doCreateHeaderControls(Composite parent) {
		
		showProjectsCheckbox = new Button(parent, SWT.CHECK);
		showProjectsCheckbox.setSelection(true);
		showProjectsCheckbox.setText("Show projects only");
		showProjectsCheckbox.addSelectionListener(new SelectionListener() {

			public void widgetDefaultSelected(SelectionEvent e) {
				widgetSelected(e);
			}

			public void widgetSelected(SelectionEvent e) {
				doFind(getFilterText());
			}
		});
	}
	
	protected ControlListItem<?> doCreateViewerItem(Composite parent, Object element) {
		if (element instanceof PackageFilterItem) {
			PackagistItem packagistItem = new PackagistItem(parent, SWT.NONE, resources, (PackageFilterItem) element);
			packagistItem.addFilterChangedListener(this);
			return packagistItem;
		}
		
		return null;
	}
	
	
	@Override
	protected void doFind(String text) {
		try {

			contentProvider.clear();
			viewer.setInput(contentProvider.packages);
			viewer.refresh();
			
			AsyncPackagistSearch search = new AsyncPackagistSearch();
			search.addPackageSearchListener(new PackageSearchListenerInterface() {
				@Override
				public void errorOccured(Exception e) {
					
				}
				@Override
				public void aborted(String url) {
					
				}
				@Override
				public void packagesFound(List<MinimalPackage> packages, String query, final SearchResult result) {
					if (packages != null) {
						final List<PackageFilterItem> items = new ArrayList<PackageFilterItem>();
						for (MinimalPackage pkg : packages) {
							items.add(new PackageFilterItem(pkg));
						}
						
						getControl().getDisplay().asyncExec(new Runnable() {
							@Override
							public void run() {
								contentProvider.add(items);
								viewer.refresh();
								searchResultCount.setText("Found " + result.total + " packages.");
							}
						});
					}
				}
			});
			
			if (showProjectsCheckbox.getSelection()) {
				search.setFilter("project");
			}
			search.search(text);
			
		} catch (Exception e) {
			Logger.logException(e);
		}
	}
	
	protected static class PackagistContentProvider implements ITreeContentProvider {

		private List<PackageFilterItem> packages;
		
		public void dispose() {
			packages = null;
		}

		public Object[] getChildren(Object parentElement) {
			return null;
		}

		public Object[] getElements(Object inputElement) {
			if (packages != null) {
				List<Object> elements = new ArrayList<Object>();
				elements.addAll(packages);
				return elements.toArray(new Object[elements.size()]);
			}
			
			return EMTPY;
		}

		public Object getParent(Object element) {
			if (element instanceof PackageFilterItem) {
				return packages;
			}
			
			return null;
		}

		public boolean hasChildren(Object element) {
			if (element instanceof PackageFilterItem) {
				return false;
			}
			
			if (packages != null) {
				return packages.size() > 0;
			}
			
			return false;
		}
		
		public void add(List<PackageFilterItem> items) {
			packages.addAll(items);
		}
		
		public void clear() {
			packages = new ArrayList<PackageFilterItem>();
		}

		@SuppressWarnings("unchecked")
		public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {
			packages = (List<PackageFilterItem>) newInput;
		}
	}

	public PackageFilterItem getSelectedPackage() {

		for (PackageFilterItem item : contentProvider.packages) {
			if (item.isChecked()) {
				return item;
			}
		}
		
		return  null;
	}

	@Override
	public void filterChanged(PackageFilterItem item) {

		if (currentSelection != null) {
			if (item == currentSelection) {
				fireEvent(item);
				return;
			}
		}
		
		List<PackageFilterItem> input = (List<PackageFilterItem>) viewer.getInput();
		if (!item.isChecked()) {
			return;
		}
		
		for (PackageFilterItem filterItem : input) {
			if (filterItem == item) {
				continue;
			}
			filterItem.setChecked(false);
		}
		
		ScrolledComposite control = (ScrolledComposite) viewer.getControl();
		Point origin = control.getOrigin();
		viewer.refresh();
		control.setOrigin(origin);
		fireEvent(item);
	}
	
	protected void fireEvent(PackageFilterItem item) {
		for (PackageFilterChangedListener listener : listeners) {
			listener.filterChanged(item);
		}
		
		currentSelection = item;
	}
}
