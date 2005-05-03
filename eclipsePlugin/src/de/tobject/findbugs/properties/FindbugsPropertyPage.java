/*
 * FindBugs Eclipse Plug-in.
 * Copyright (C) 2003 - 2004, Peter Friese
 * Copyright (C) 2004-2005, University of Maryland
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA
 */

package de.tobject.findbugs.properties;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.dialogs.ProgressMonitorDialog;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.jface.viewers.CheckboxTableViewer;
import org.eclipse.jface.viewers.ILabelProviderListener;
import org.eclipse.jface.viewers.IStructuredContentProvider;
import org.eclipse.jface.viewers.ITableLabelProvider;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerSorter;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.ui.dialogs.PropertyPage;

import de.tobject.findbugs.FindbugsPlugin;
import de.tobject.findbugs.reporter.MarkerUtil;
import de.tobject.findbugs.util.ProjectUtilities;
import edu.umd.cs.findbugs.BugPattern;
import edu.umd.cs.findbugs.DetectorFactory;
import edu.umd.cs.findbugs.DetectorFactoryCollection;
import edu.umd.cs.findbugs.I18N;
import edu.umd.cs.findbugs.config.ProjectFilterSettings;
import edu.umd.cs.findbugs.config.UserPreferences;

/**
 * Project properties page for setting FindBugs properties. 
 *  
 * @author Andrei Loskutov
 * @author Peter Friese 
 * @author David Hovemeyer
 * @author Phil Crosby
 * @version 1.0
 * @since 17.06.2004
 */
public class FindbugsPropertyPage extends PropertyPage {

	private static final String COLUMN_PROPS_DESCRIPTION = "description"; //$NON-NLS-1$
	private static final String COLUMN_PROPS_SPEED = "speed"; //$NON-NLS-1$
	private static final String COLUMN_PROPS_NAME = "name"; //$NON-NLS-1$
	private static final String COLUMN_PROPS_BUG_ABBREV = "bug codes"; //$NON-NLS-1$
	private boolean initialEnabled;
	private Button chkEnableFindBugs;
	private Combo minPriorityCombo;
	/*
	private Button chkDisplayFalseWarnings;
	*/
	private Button[] chkEnableBugCategoryList;
	private String[] bugCategoryList;
	private UserPreferences origUserPreferences;
	private UserPreferences currentUserPreferences;
	private IProject project;
	protected TableViewer availableFactoriesTableViewer;
	protected Map factoriesToBugAbbrev;
	private Button restoreDefaultsButton;

	/**
	 * Constructor for FindbugsPropertyPage.
	 */
	public FindbugsPropertyPage() {
		super();
	}

	/**
	 * @see PreferencePage#createContents(Composite)
	 */
	protected Control createContents(Composite parent) {

		noDefaultAndApplyButton();

		// getElement returns the element this page has been opened for,
		// in our case this is a Java Project (IJavaProject).
		IAdaptable resource = getElement();
		this.project = (IProject) resource.getAdapter(IProject.class);
		
		collectUserPreferences();
		
		Composite composite = new Composite(parent, SWT.NONE);
		GridLayout layout = new GridLayout();
		composite.setLayout(layout);

		chkEnableFindBugs = new Button(composite, SWT.CHECK);
		chkEnableFindBugs.setText("Run FindBugs automatically");
		initialEnabled = isEnabled();
		chkEnableFindBugs.setSelection(initialEnabled);

		Composite prioGroup = new Composite(composite, SWT.NONE);
		GridLayout prioLayout = new GridLayout(3, false);
		prioGroup.setLayout(prioLayout);
		
		Label minPrioLabel = new Label(prioGroup, SWT.NONE);
		minPrioLabel.setText("Minimum priority to report: ");
		minPrioLabel.setLayoutData(new GridData(SWT.BEGINNING, SWT.CENTER, false, false));
		
		minPriorityCombo = new Combo(prioGroup, SWT.DROP_DOWN | SWT.READ_ONLY);
		minPriorityCombo.add(ProjectFilterSettings.HIGH_PRIORITY);
		minPriorityCombo.add(ProjectFilterSettings.MEDIUM_PRIORITY);
		minPriorityCombo.add(ProjectFilterSettings.LOW_PRIORITY);
		minPriorityCombo.setText(origUserPreferences.getFilterSettings().getMinPriority());
		minPriorityCombo.setLayoutData(new GridData(SWT.BEGINNING, SWT.CENTER, false, false));
		
		/*
		chkDisplayFalseWarnings = new Button(prioGroup, SWT.CHECK);
		chkDisplayFalseWarnings.setText("Display false warnings");
		GridData chkDisplayFalseWarningsLayoutData =
			new GridData(SWT.BEGINNING, SWT.CENTER, false, false);
		chkDisplayFalseWarningsLayoutData.horizontalIndent = 15;
		chkDisplayFalseWarnings.setLayoutData(chkDisplayFalseWarningsLayoutData);
		chkDisplayFalseWarnings.setSelection(
				origUserPreferences.getFilterSettings().displayFalseWarnings());
		*/
		
		Composite categoryGroup = new Composite(composite, SWT.NONE);
		categoryGroup.setLayout(new GridLayout(2, true));
		
		Label activeCategoriesLabel = new Label(categoryGroup, SWT.NONE);
		activeCategoriesLabel.setText("Enable bug categories:");
		activeCategoriesLabel.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false, 2, 1));
		buildBugCategoryList(categoryGroup, project);
		
		addSeparator(composite);

		buildLabel(composite, "Select bug patterns to check for:");
		Table availableRulesTable =
			buildAvailableRulesTableViewer(composite, project);
		GridData tableLayoutData = new GridData();
		tableLayoutData.grabExcessHorizontalSpace = true;
		tableLayoutData.grabExcessVerticalSpace = true;
		tableLayoutData.horizontalAlignment = GridData.FILL;
		tableLayoutData.verticalAlignment = GridData.FILL;
		tableLayoutData.heightHint = 50;
		availableRulesTable.setLayoutData(tableLayoutData);

		addSeparator(composite);
		
		restoreDefaultsButton = new Button(composite, SWT.NONE);
		restoreDefaultsButton.setText("Restore default settings");
		restoreDefaultsButton.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false));
		restoreDefaultsButton.addSelectionListener(new SelectionAdapter() {
			/* (non-Javadoc)
			 * @see org.eclipse.swt.events.SelectionAdapter#widgetSelected(org.eclipse.swt.events.SelectionEvent)
			 */
			public void widgetSelected(SelectionEvent e) {
				restoreDefaultSettings();
			}
		});
		
		return composite;
	}

	private void collectUserPreferences() {
		// Get current user preferences for project
		try {
			this.origUserPreferences = FindbugsPlugin.getUserPreferences(project);
		} catch (CoreException e) {
			// Use default settings
			FindbugsPlugin.getDefault().logException(e, "Could not get user preferences for project");
			this.origUserPreferences = UserPreferences.createDefaultUserPreferences();
		}
		this.currentUserPreferences = (UserPreferences) origUserPreferences.clone();
	}

	/**
	 * Restore default settings.
	 * This just changes the dialog widgets - the user still needs
	 * to confirm by clicking the "OK" button.
	 */
	private void restoreDefaultSettings() {
		// By default, don't run FindBugs automatically
		chkEnableFindBugs.setSelection(false);
		
		// Use the default minimum priority (which is medium)
		minPriorityCombo.setText(ProjectFilterSettings.DEFAULT_PRIORITY);
		
		// By default, all bug categories are enabled
		for (int i = 0; i < chkEnableBugCategoryList.length; ++i) {
			chkEnableBugCategoryList[i].setSelection(true);
		}
		
		// Enable only those detectors that are enabled by default
		TableItem[] itemList =
			availableFactoriesTableViewer.getTable().getItems();
		for (int i = 0; i < itemList.length; i++) {
			TableItem item = itemList[i];
			DetectorFactory factory = (DetectorFactory) item.getData();
			item.setChecked(factory.isDefaultEnabled());
		}
	}

	/**
	 * Add a horizontal separator to given panel.
	 * 
	 * @param composite the panel
	 */
	private void addSeparator(Composite composite) {
		Label separator =
			new Label(composite, SWT.SEPARATOR | SWT.SHADOW_IN | SWT.HORIZONTAL);
		GridData data = new GridData();
		data.horizontalAlignment = GridData.FILL;
		data.grabExcessHorizontalSpace = true;
		separator.setLayoutData(data);
	}

	/**
	 * Build list of bug categories to be enabled or disabled.
	 * Populates chkEnableBugCategoryList and bugCategoryList fields.
	 * 
	 * @param categoryGroup control checkboxes should be added to
	 * @param project       the project being configured
	 */
	private void buildBugCategoryList(Composite categoryGroup, final IProject project) {
		List bugCategoryList = new LinkedList(I18N.instance().getBugCategories());
		List checkBoxList = new LinkedList();
		for (Iterator i = bugCategoryList.iterator(); i.hasNext(); ) {
			String category = (String) i.next();
			Button checkBox = new Button(categoryGroup, SWT.CHECK);
			checkBox.setText(I18N.instance().getBugCategoryDescription(category));
			checkBox.setSelection(origUserPreferences.getFilterSettings().containsCategory(category));
			
			GridData layoutData = new GridData();
			layoutData.horizontalIndent = 15;
			checkBox.setLayoutData(layoutData);
			
			// Every time a checkbox is clicked, rebuild the detector factory table
			// to show only relevant entries
			
			checkBox.addListener(SWT.Selection, 
				new Listener(){
					public void handleEvent(Event e){
						System.out.println("Category preferences changed!");
						syncSelectedCategories();
						populateAvailableRulesTable(project);
					}
				} 
			);

			
			checkBoxList.add(checkBox);
		}
		
		this.chkEnableBugCategoryList = (Button[]) checkBoxList.toArray(new Button[checkBoxList.size()]);
		this.bugCategoryList = (String[]) bugCategoryList.toArray(new String[bugCategoryList.size()]);
	}

	/**
	 * Synchronize selected bug category checkboxes with the current user preferences.
	 */
	private void syncSelectedCategories() {
		for (int i = 0; i < chkEnableBugCategoryList.length; ++i) {
			Button checkBox = chkEnableBugCategoryList[i];
			String category = bugCategoryList[i];
			if (checkBox.getSelection()) {
				currentUserPreferences.getFilterSettings().addCategory(category);
			} else {
				currentUserPreferences.getFilterSettings().removeCategory(category);
			}
		}
	}				

	/**
	 * Build rule table viewer
	 */
	private Table buildAvailableRulesTableViewer(
		Composite parent,
		IProject project) {
		final BugPatternTableSorter sorter = new BugPatternTableSorter(this);

		int tableStyle =
			SWT.BORDER
				| SWT.H_SCROLL
				| SWT.V_SCROLL
				| SWT.SINGLE
				| SWT.FULL_SELECTION
				| SWT.CHECK;
		availableFactoriesTableViewer =
			CheckboxTableViewer.newCheckList(parent, tableStyle);

		int currentColumnIdx = -1;
		Table factoriesTable = availableFactoriesTableViewer.getTable();

		TableColumn bugsAbbrevColumn = new TableColumn(factoriesTable, SWT.LEFT);
		bugsAbbrevColumn.setResizable(true);
		bugsAbbrevColumn.setText(getMessage("Bug codes"));
		bugsAbbrevColumn.setWidth(100);
		addColumnSelectionListener(sorter, bugsAbbrevColumn, ++currentColumnIdx);

		TableColumn factoryNameColumn = new TableColumn(factoriesTable, SWT.LEFT);
		factoryNameColumn.setResizable(true);
		factoryNameColumn.setText(getMessage("Detector name"));
		factoryNameColumn.setWidth(200);
		addColumnSelectionListener(sorter, factoryNameColumn, ++currentColumnIdx);

		TableColumn factorySpeedColumn =
			new TableColumn(factoriesTable, SWT.LEFT);
		factorySpeedColumn.setResizable(true);
		factorySpeedColumn.setText(getMessage("Detector speed"));
		factorySpeedColumn.setWidth(90);
		addColumnSelectionListener(
			sorter,
			factorySpeedColumn,
			++currentColumnIdx);

		TableColumn bugsDescriptionColumn =
			new TableColumn(factoriesTable, SWT.LEFT);
		bugsDescriptionColumn.setResizable(true);
		bugsDescriptionColumn.setText(getMessage("Detector description"));
		bugsDescriptionColumn.setWidth(200);

		factoriesTable.setLinesVisible(true);
		factoriesTable.setHeaderVisible(true);

		availableFactoriesTableViewer.setContentProvider(
			new DetectorFactoriesContentProvider());
		availableFactoriesTableViewer.setLabelProvider(
			new DetectorFactoryLabelProvider(this));
		availableFactoriesTableViewer.setColumnProperties(
			new String[] {
				COLUMN_PROPS_BUG_ABBREV,
				COLUMN_PROPS_NAME,
				COLUMN_PROPS_SPEED,
				COLUMN_PROPS_DESCRIPTION });

		availableFactoriesTableViewer.setSorter(sorter);

		populateAvailableRulesTable(project);
		factoriesTable.setEnabled(true);

		return factoriesTable;
	}

	/**
	 * @param sorter
	 * @param column
	 */
	private void addColumnSelectionListener(
		final BugPatternTableSorter sorter,
		TableColumn column,
		final int columnIdx) {
		column.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				sorter.setSortColumnIndex(columnIdx);
				availableFactoriesTableViewer.refresh();
			}
		});
	}
	
	/**
	 * Return whether or not given DetectorFactory reports bug patterns
	 * in one of the currently-enabled set of bug categories.
	 * 
	 * @param factory the DetectorFactory
	 * @return true if the factory reports bug patterns in one of the
	 *         currently-enabled bug categories, false if not
	 */
	private boolean reportsInEnabledCategory(DetectorFactory factory) {
		for (Iterator i = factory.getReportedBugPatterns().iterator(); i.hasNext();) {
			BugPattern pattern = (BugPattern) i.next();
			if (currentUserPreferences.getFilterSettings().containsCategory(pattern.getCategory()))
				return true;
		}
		return false;
	}
	
	/**
	 * Populate the rule table
	 */
	private void populateAvailableRulesTable(IProject project) {
		List allAvailableList = new ArrayList();
		factoriesToBugAbbrev = new HashMap();
		Iterator iterator =
			DetectorFactoryCollection.instance().factoryIterator();
		while (iterator.hasNext()) {
			DetectorFactory factory = (DetectorFactory) iterator.next();
			
			// Only configure non-hidden factories
			if (factory.isHidden()) {
				//System.out.println("Factory " + factory.getFullName() + " is hidden");
				continue;
			}
			
			// Only add items for detectors which report in currently-enabled categories
			if (!reportsInEnabledCategory(factory))
				continue;
			
			allAvailableList.add(factory);
			addBugsAbbreviation(factory);
		}

		availableFactoriesTableViewer.setInput(allAvailableList);
		TableItem[] itemList =
			availableFactoriesTableViewer.getTable().getItems();
		for (int i = 0; i < itemList.length; i++) {
			DetectorFactory rule = (DetectorFactory) itemList[i].getData();
			//set enabled if defined in configuration
			if (currentUserPreferences.isDetectorEnabled(rule)) {
				itemList[i].setChecked(true);
			}
			
			// Listen for check/uncheck events, update user preferences accordingly
			itemList[i].addListener(SWT.Selection, new Listener() {
				public void handleEvent(Event event) {
					System.out.println("Detector selection changed!");
					syncUserPreferencesWithTable();
				}
			});
		}
	}

	/**
	 * @param factory
	 */
	protected void addBugsAbbreviation(DetectorFactory factory) {
		factoriesToBugAbbrev.put(factory, createBugsAbbreviation(factory));
	}

	protected String getBugsAbbreviation(DetectorFactory factory) {
		String abbr = (String) factoriesToBugAbbrev.get(factory);
		if (abbr == null) {
			abbr = createBugsAbbreviation(factory);
		}
		if (abbr == null) {
			abbr = ""; //$NON-NLS-1$
		}
		return abbr;
	}

	protected String createBugsAbbreviation(DetectorFactory factory) {
		StringBuffer sb = new StringBuffer();
		Collection patterns = factory.getReportedBugPatterns();
		ArrayList abbrs = new ArrayList();
		for (Iterator iter = patterns.iterator(); iter.hasNext();) {
			BugPattern pattern = (BugPattern) iter.next();
			String abbr = pattern.getAbbrev();
			if (abbrs.contains(abbr)) {
				continue;
			}
			abbrs.add(abbr);
		}
		for (Iterator iter = abbrs.iterator(); iter.hasNext();) {
			String element = (String) iter.next();
			sb.append(element);
			if (iter.hasNext()) {
				sb.append("|"); //$NON-NLS-1$
			}
		}
		return sb.toString();
	}

	/**
	 * Build a label
	 */
	private Label buildLabel(Composite parent, String msgKey) {
		Label label = new Label(parent, SWT.NONE);
		String message = getMessage(msgKey);
		label.setText(message == null ? msgKey : message);
		return label;
	}

	/**
	 * Will be called when the user presses the OK button.
	 * @see IPreferencePage#performOk()
	 */
	public boolean performOk() {
		boolean selection = this.chkEnableFindBugs.getSelection();
		boolean result = true;
		
		// Keep of track of whether we need to update
		// which warning markers are shown.
		boolean filterOptionsChanged = false;

		// Have user preferences for project changed?
		// If so, write them to the user preferences file.
		if (!currentUserPreferences.equals(origUserPreferences)) {
			//System.out.println("User preferences for project changed!");
			try {
				FindbugsPlugin.saveUserPreferences(project, currentUserPreferences);
			} catch (CoreException e) {
				FindbugsPlugin.getDefault().logException(e, "Could not store FindBugs preferences for project");
			} catch (IOException e) {
				FindbugsPlugin.getDefault().logException(e, "Could not store FindBugs preferences for project");
			}
			
			// Have filter settings changed?
			// If so, we need to redisplay warnings.
			if (!currentUserPreferences.getFilterSettings().equals(
					origUserPreferences.getFilterSettings())) {
				//System.out.println("Filter setting for project changed!");
				filterOptionsChanged = true;
			}
		}
		
		// Update whether or not FindBugs is run automatically.
		if (!this.initialEnabled && selection == true) {
			result = addNature();
		}
		else if (this.initialEnabled && selection == false) {
			result = removeNature();
		}
		
		if (result && filterOptionsChanged) {
			//System.out.println("Redisplaying markers!");
			MarkerUtil.redisplayMarkers(project, getShell());
		}
		
		return result;
	}

	/**
	 * Using the natures name, check whether the current
	 * project has the given nature.
	 *
	 * @return boolean <code>true</code>, if the nature is
	 *   assigned to the project, <code>false</code> otherwise.
	 */
	private boolean isEnabled() {
		boolean result = false;

		try {
			if (this.project.hasNature(FindbugsPlugin.NATURE_ID))
				result = true;
		}
		catch (CoreException e) {
			System.err.println("Exception: " + e); //$NON-NLS-1$
		}
		return result;
	}

	/**
	 * Add the nature to the current project. The real work is
	 * done by the inner class NatureWorker
	 * @return boolean <code>true</code> if the nature could
	 *   be added successfully, <code>false</code> otherwise.
	 */
	private boolean addNature() {
		boolean result = true;
		try {
			NatureWorker worker = new NatureWorker(true);
			ProgressMonitorDialog monitor = new ProgressMonitorDialog(getShell());
			monitor.run(true, true, worker);
		}
		catch (InvocationTargetException e) {
			System.err.println("Exception: " + e); //$NON-NLS-1$
		}
		catch (InterruptedException e) {
			System.err.println("Exception: " + e); //$NON-NLS-1$
		}
		return result;
	}

	/**
	 * Remove the nature from the project.
	 * @return boolean <code>true</code> if the nature could
	 *   be added successfully, <code>false</code> otherwise.
	 */
	private boolean removeNature() {
		boolean result = true;
		try {
//			MarkerUtil.removeMarkers(project);

			NatureWorker worker = new NatureWorker(false);
			ProgressMonitorDialog monitor = new ProgressMonitorDialog(getShell());
			monitor.run(true, true, worker);
		}
		catch (InvocationTargetException e) {
			System.err.println("Exception: " + e); //$NON-NLS-1$
		}
		catch (InterruptedException e) {
			System.err.println("Exception: " + e); //$NON-NLS-1$
		}
//		catch (CoreException e) {
//			System.err.println("Exception: " + e); //$NON-NLS-1$
//		}
		return result;
	}

	private final class NatureWorker implements IRunnableWithProgress {
		private boolean add = true;

		public NatureWorker(boolean add) {
			this.add = add;
		}

		/**
		 * @see IRunnableWithProgress#run(IProgressMonitor)
		 */
		public void run(IProgressMonitor monitor) {
			try {
				if (add) {
					ProjectUtilities.addFindBugsNature(project, monitor);
				}
				else {
					ProjectUtilities.removeFindBugsNature(project, monitor);
				}
			}
			catch (CoreException e) {
				e.printStackTrace();
				System.err.println("Exception: " + e); //$NON-NLS-1$
			}
		}
	}

	/**
	 * Helper method to shorten message access
	 * @param key a message key
	 * @return requested message
	 */
	protected String getMessage(String key) {
		return FindbugsPlugin.getDefault().getMessage(key);
	}

	/**
	 * Disables all unchecked detector factories and enables checked factory detectors, leaving
	 * those not in the table unmodified.
	 * @param userPrefs the UserPreferences to adjust to match the UI table
	 */
	protected void syncUserPreferencesWithTable(){
		TableItem[] itemList =
			availableFactoriesTableViewer.getTable().getItems();
		for (int i = 0; i < itemList.length; i++) {
			DetectorFactory factory = (DetectorFactory) itemList[i].getData();
			
			//set enabled if defined in configuration
			currentUserPreferences.enableDetector(factory, itemList[i].getChecked());
		}	
	}

	/**
	 * @author Andrei
	 */
	private static final class BugPatternTableSorter
		extends ViewerSorter
		implements Comparator {
		private int sortColumnIndex;
		private int lastSortColumnIdx;
		boolean revertOrder;
		private FindbugsPropertyPage page;

		BugPatternTableSorter(FindbugsPropertyPage page) {
			this.page = page;
		}

		public int compare(Viewer viewer, Object e1, Object e2) {
			return compare(e1, e2);
		}

		/**
		 * @param e1
		 * @param e2
		 * @return
		 */
		public int compare(Object e1, Object e2) {
			int result = 0;
			DetectorFactory factory1 = (DetectorFactory) e1;
			DetectorFactory factory2 = (DetectorFactory) e2;
			String s1, s2;
			switch (getSortColumnIndex()) {
				case 0 :
					s1 = page.getBugsAbbreviation(factory1);
					s2 = page.getBugsAbbreviation(factory2);
					break;
				case 1 :
					s1 = "" + factory1.getShortName(); //$NON-NLS-1$
					s2 = factory2.getShortName();
					break;
				case 2 :
					s1 = "" + factory1.getSpeed(); //$NON-NLS-1$
					s2 = factory2.getSpeed();
					break;
				default :
					s1 = "" + factory1.getSpeed(); //$NON-NLS-1$
					s2 = factory2.getSpeed();
					break;
			}

			result = s1.compareTo(s2);

			// second sort if elements are equals - on opposite criteria
			if (result == 0) {
				switch (getSortColumnIndex()) {
					case 0 :
						// fall througth
					case 1 :
						s1 = "" + factory1.getSpeed(); //$NON-NLS-1$
						s2 = factory2.getSpeed();
						break;
					case 2 :
						s1 = "" + factory1.getShortName(); //$NON-NLS-1$
						s2 = factory2.getShortName();
						break;
				}
				result = s1.compareTo(s2);
			}
			else if (revertOrder) {
				// same column selected twice - revert first order
				result = -result;
			}
			return result;
		}

		public boolean isSorterProperty(Object element, String property) {
			return property.equals(COLUMN_PROPS_NAME)
				|| property.equals(COLUMN_PROPS_BUG_ABBREV)
				|| property.equals(COLUMN_PROPS_SPEED);
		}

		/**
		 * @param sortColumnIndex The sortColumnIndex to set.
		 */
		public void setSortColumnIndex(int sortColumnIndex) {
			this.lastSortColumnIdx = this.sortColumnIndex;
			this.sortColumnIndex = sortColumnIndex;
			revertOrder = !revertOrder && lastSortColumnIdx == sortColumnIndex;
		}

		/**
		 * @return Returns the sortColumnIndex.
		 */
		public int getSortColumnIndex() {
			return sortColumnIndex;
		}
	}

	/**
	 * @author Andrei
	 */
	private static final class DetectorFactoryLabelProvider
		implements ITableLabelProvider {
		private FindbugsPropertyPage page;
		DetectorFactoryLabelProvider(FindbugsPropertyPage page) {
			this.page = page;
		}
		/* (non-Javadoc)
		 * @see org.eclipse.jface.viewers.IBaseLabelProvider#addListener(org.eclipse.jface.viewers.ILabelProviderListener)
		 */
		public void addListener(ILabelProviderListener listener) {
			// ignored
		}

		/* (non-Javadoc)
		 * @see org.eclipse.jface.viewers.IBaseLabelProvider#dispose()
		 */
		public void dispose() {
			// ignored
		}

		/* (non-Javadoc)
		 * @see org.eclipse.jface.viewers.IBaseLabelProvider#isLabelProperty(java.lang.Object, java.lang.String)
		 */
		public boolean isLabelProperty(Object element, String property) {
			return false;
		}

		/* (non-Javadoc)
		 * @see org.eclipse.jface.viewers.IBaseLabelProvider#removeListener(org.eclipse.jface.viewers.ILabelProviderListener)
		 */
		public void removeListener(ILabelProviderListener listener) {
			// ignored
		}

		/* (non-Javadoc)
		 * @see org.eclipse.jface.viewers.ITableLabelProvider#getColumnImage(java.lang.Object, int)
		 */
		public Image getColumnImage(Object element, int columnIndex) {
			// TODO ignored - but if we have images for different detectors ...
			return null;
		}

		/* (non-Javadoc)
		 * @see org.eclipse.jface.viewers.ITableLabelProvider#getColumnText(java.lang.Object, int)
		 */
		public String getColumnText(Object element, int columnIndex) {

			if (!(element instanceof DetectorFactory)) {
				return null;
			}
			DetectorFactory factory = (DetectorFactory) element;

			switch (columnIndex) {
				case 0 :
					return page.getBugsAbbreviation(factory);
				case 1 :
					return factory.getShortName();
				case 2 :
					return factory.getSpeed();
				case 3 :
					StringBuffer sb = new StringBuffer();
					Collection patterns = factory.getReportedBugPatterns();
					for (Iterator iter = patterns.iterator(); iter.hasNext();) {
						BugPattern pattern = (BugPattern) iter.next();
						sb.append(pattern.getShortDescription());
						if (iter.hasNext()) {
							sb.append(" | "); //$NON-NLS-1$
						}
					}
					return sb.toString();
				default :
					return null;
			}
		}

	}
	/**
	 * @author Andrei
	 */
	private static final class DetectorFactoriesContentProvider
		implements IStructuredContentProvider {
		/* (non-Javadoc)
		 * @see org.eclipse.jface.viewers.IContentProvider#dispose()
		 */
		public void dispose() {
			// ignored
		}
		/* (non-Javadoc)
		 * @see org.eclipse.jface.viewers.IContentProvider#inputChanged(org.eclipse.jface.viewers.Viewer, java.lang.Object, java.lang.Object)
		 */
		public void inputChanged(
			Viewer viewer,
			Object oldInput,
			Object newInput) {
			// ignored
		}
		/* (non-Javadoc)
		 * @see org.eclipse.jface.viewers.IStructuredContentProvider#getElements(java.lang.Object)
		 */
		public Object[] getElements(Object inputElement) {
			if (inputElement instanceof List) {
				List list = (List) inputElement;
				return list.toArray();
			}
			return null;
		}
	}

}
