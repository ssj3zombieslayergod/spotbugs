/*
 * FindBugs - Find Bugs in Java programs
 * Copyright (C) 2006, University of Maryland
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
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston MA 02111-1307, USA
 */

package edu.umd.cs.findbugs.gui2;

import java.util.ArrayList;
import java.util.List;

import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.TableColumnModelEvent;
import javax.swing.event.TableColumnModelListener;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeExpansionListener;
import javax.swing.event.TreeModelEvent;
import javax.swing.event.TreeModelListener;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;

import edu.umd.cs.findbugs.BugInstance;
import edu.umd.cs.findbugs.SystemProperties;
import edu.umd.cs.findbugs.gui2.BugAspects.SortableValue;

/*
 * Our TreeModel.  Once upon a time it was a simple model, that queried data, its BugSet, for what to show under each branch
 * Then it got more and more complicated, no one knows why it still seems to work... or why it doesn't if it in fact doesn't.  
 * 
 * Here's a tip, Dont even attempt to deal with suppressions or filtering and their related tree model events without the API
 * for TreeModelEvents open.  And read it three times first.  Ignore the fact that its inconsistent for sending events about the root, just pick one of the things it says and go with it
 * 
 * Heres the order things MUST be done when dealing with suppressions, filtering, unsuppressions... unfiltering... all that fun stuff
 * 
 * Inserts:
 * Update model
 * Get Path
 * Make Event
 * Send Event
 * ResetData
 * 
 * Removes:
 * Get Path
 * Make Event
 * Update Model
 * Send Event
 * ResetData
 * 
 * Restructure:
 * Update Model
 * Get Path
 * Make Event
 * Send Event
 * resetData? hmmm
 * 
 * These may or may not be the orders of events used in suppressBug, unsuppressBug, branchOperations and so forth
 * if they seem to work anyway, I wouldn't touch them.  
 * 
 * changeSet() is what to do when the data set is completely different (loaded a new collection, reran the analysis what have you)
 * changeSet calls rebuild(), which does a very tricky thing, where it makes a new model, and a new JTree, and swaps them in in place of this one, as well as 
 * turning off user input in hopefully every place it needs to be turned off
 * 
 */

/**
 * The treeModel for our JTree
 */
	public class BugTreeModel implements TreeModel, TableColumnModelListener, TreeExpansionListener
	{	
		private BugAspects root = new BugAspects();
		private SorterTableColumnModel st;
		private BugSet bugSet;
		private ArrayList<TreeModelListener> listeners = new ArrayList<TreeModelListener>();
		private JTree tree;
		static ArrayList<BugLeafNode> selectedBugLeafNodes = new ArrayList<BugLeafNode>();

		private static final boolean DEBUG = false;

		private volatile Thread rebuildingThread;
		private boolean sortOrderChanged;
		private boolean sortsAddedOrRemoved;


		public BugTreeModel(JTree tree, SorterTableColumnModel st, BugSet data)
		{
			st.addColumnModelListener(this);
			this.tree = tree;
			this.st = st;
			this.bugSet = data;
			BugSet.setAsRootAndCache(this.bugSet);
			root.setCount(data.size());
			FilterActivity.addFilterListener(bugTreeFilterListener);
			if (DEBUG) 
				this.addTreeModelListener(new TreeModelListener() {

					public void treeNodesChanged(TreeModelEvent arg0) {
						System.out.println("Tree nodes changed");
						System.out.println("  " + arg0.getTreePath());

					}

					public void treeNodesInserted(TreeModelEvent arg0) {
						System.out.println("Tree nodes inserted");
						System.out.println("  " + arg0.getTreePath());

					}

					public void treeNodesRemoved(TreeModelEvent arg0) {
						System.out.println("Tree nodes removed");
						System.out.println("  " + arg0.getTreePath());

					}

					public void treeStructureChanged(TreeModelEvent arg0) {
						System.out.println("Tree structure changed");
						System.out.println("  " + arg0.getTreePath());

					}});
		}

		public BugTreeModel(BugTreeModel other)
		{
			this.root = new BugAspects(other.root);
			this.st = other.st;
			this.bugSet = new BugSet(other.bugSet);
			//this.listeners = other.listeners;
			this.tree = other.tree;
		}

		public void getOffListenerList()
		{
			FilterActivity.removeFilterListener(bugTreeFilterListener);
			st.removeColumnModelListener(this);
			tree.removeTreeExpansionListener(this);
		}

		public void clearViewCache() {
			bugSet.clearCache();
		}
		public Object getRoot()
		{
			return root;
		}
		
		public  Object getChild(Object o, int index)
		{
			BugAspects a = (BugAspects) o;
			int treeLevels = st.getOrderBeforeDivider().size();
			int queryDepth = a.size();
			assert queryDepth <= treeLevels;
			
			if (treeLevels==0 && a.size()==0)//Root without any sortables
				return bugSet.get(index);
			if (SystemProperties.ASSERTIONS_ENABLED) 
				for(int i = 0; i < queryDepth; i++) {
					Sortables treeSortable = st.getOrderBeforeDivider().get(i);
					Sortables querySortable = a.get(i).key;
					assert treeSortable.equals(querySortable);
				}

			try {
				if (queryDepth < treeLevels) {
					BugAspects child = a.addToNew(enumsThatExist(a).get(index));
					child.setCount(bugSet.query(child).size());
					return child;
				}	
			else
				return bugSet.query(a).get(index);
			}
			catch (IndexOutOfBoundsException e)
			{
				assert false;
				return null;
			}

		}

		public int getChildCount(Object o)
		{

			if(!(o instanceof BugAspects))
				return 0;

			BugAspects a = (BugAspects) o;

					if (st.getOrderBeforeDivider().size()==0 && a.size() == 0)//If its the root and we aren't sorting by anything
						return bugSet.size();

					if ((a.size() == 0) || (a.last().key != st.getOrderBeforeDivider().get(st.getOrderBeforeDivider().size() - 1)))
						return enumsThatExist(a).size();
					else
						return bugSet.query(a).size();
		}


		/*This contract has been changed to return a HashList of Stringpair, our own data structure in which finding the index of an object in the list is very fast*/

		private ArrayList<SortableValue> enumsThatExist(BugAspects a)
		{
					if (st.getOrderBeforeDivider().size()==0)
						return null;					


					Sortables key = (a.size() == 0 ?
							st.getOrderBeforeDivider().get(0) :
								st.getOrderBeforeDivider().get(st.getOrderBeforeDivider().indexOf(a.last().key) + 1));

					String[] all = key.getAll(bugSet.query(a));
					ArrayList<SortableValue> result = new ArrayList<SortableValue>();
					for (String i : all)
						result.add(new SortableValue(key, i));
					return result;

		}

		public boolean isLeaf(Object o)
		{
			return (o instanceof BugLeafNode);
		}

		public void valueForPathChanged(TreePath arg0, Object arg1) {}

		public int getIndexOfChild(Object parent, Object child)
		{	
			if (parent == null || child == null || isLeaf(parent))
				return -1;

			if (isLeaf(child))
			{
				return bugSet.query((BugAspects) parent).indexOf((BugLeafNode) child);
			}
			else
			{
				ArrayList<SortableValue> stringPairs = enumsThatExist((BugAspects) parent);
				if (stringPairs==null)
				{
					//XXX-Threading difficulties-stringpairs is null somehow
					Debug.println("Stringpairs is null on getIndexOfChild!  Error!");
					assert(false);
					return -1; 
				}

				return stringPairs.indexOf(((BugAspects)child).last());

			}
		}

		public void addTreeModelListener(TreeModelListener listener)
		{
			listeners.add(listener);
		}

		public void removeTreeModelListener(TreeModelListener listener)
		{
			listeners.remove(listener);
		}


		public void columnAdded(TableColumnModelEvent e)
		{
			sortsAddedOrRemoved=true;
			//rebuild();	
		}

		public void columnRemoved(TableColumnModelEvent e)
		{
			sortsAddedOrRemoved=true;
			//rebuild();
		}

		public void columnMoved(final TableColumnModelEvent evt)
		{
			if (evt.getFromIndex() == evt.getToIndex())
				return;
			sortOrderChanged=true;
			//rebuild();
		}

		public void needToRebuild()
		{
			sortOrderChanged=true;
		}


		void changeSet(BugSet set)
		{
			BugSet.setAsRootAndCache(set);
			bugSet=new BugSet(set);
			root.setCount(bugSet.size());
			rebuild();
		}


		/**
		 * Swaps in a new BugTreeModel and a new JTree
		 *
		 */
        public void rebuild()
		{
			if (TRACE) 
				System.out.println("rebuilding bug tree model");
			
			NewFilterFromBug.closeAll();

			//If this thread is not interrupting a previous thread, set the paths to be opened when the new tree is complete
			//If the thread is interrupting another thread, dont do this, because you dont have the tree with the correct paths selected

			//As of now, it should be impossible to interrupt a rebuilding thread, in another version this may change, so this if statement check is left in, even though it should always be true.
			if (rebuildingThread==null)
				setOldSelectedBugs();

			Debug.println("Please Wait called right before starting rebuild thread");
			MainFrame.getInstance().acquireDisplayWait();
			rebuildingThread = new Thread("Rebuilding thread")
			{
				 BugTreeModel newModel;
				@Override
				public void run()
				{	
					try
					{
						newModel = new BugTreeModel(BugTreeModel.this);
						newModel.listeners = listeners;
						newModel.resetData();
						newModel.bugSet.sortList();
					}
					finally
					{
						rebuildingThread = null;
						SwingUtilities.invokeLater(new Runnable() {
							public void run() {
								if (newModel != null) {
								JTree newTree = new JTree(newModel);
								newModel.tree = newTree;
								MainFrame.getInstance().newTree(newTree,newModel);
								MainFrame.getInstance().releaseDisplayWait();
								}
						getOffListenerList();
							}});
					}
				}
			};
			rebuildingThread.start();
		}

		public void crawl(final ArrayList<BugAspects> path, final int depth)
		{
			for (int i = 0; i < getChildCount(path.get(path.size() - 1)); i++)
				if (depth > 0)
				{
					ArrayList<BugAspects> newPath = new ArrayList<BugAspects>(path);
					newPath.add((BugAspects) getChild(path.get(path.size() - 1), i));
					crawl(newPath, depth - 1);
				}
				else
				{
					for (TreeModelListener l : listeners)
						l.treeStructureChanged(new TreeModelEvent(this, path.toArray()));
				}
		}




		void openPreviouslySelected(List<BugLeafNode> selected)
		{
			
			Debug.printf("Starting Open Previously Selected for %d nodes\n", selected.size());
				for (BugLeafNode b: selected)
				{
					try
					{
						BugInstance bug =b.getBug();
						TreePath path=getPathToBug(bug);
						if (path == null)
							continue;
						Debug.printf("Opening %s\n", path);
						MainFrame.getInstance().getTree().expandPath(path.getParentPath());
						MainFrame.getInstance().getTree().addSelectionPath(path);
					}
					catch(RuntimeException e)
					{
						Debug.println("Failure opening a selected node, node will not be opened in new tree");
					}
				}

		}

		/* Recursively traverses the tree, opens all nodes matching any bug 
		 * in the list, then creates the full paths to the bugs that are selected
		 * This keeps whatever bugs were selected selected when sorting
		 *	DEPRECATED--Too Slow, use openPreviouslySelected
		 */

		public void crawlToOpen(TreePath path, ArrayList<BugLeafNode> bugLeafNodes, ArrayList<TreePath> treePaths)
		{
			for (int i = 0; i < getChildCount(path.getLastPathComponent()); i++)
			{
				if (!isLeaf(getChild(path.getLastPathComponent(), i)))
					for (BugLeafNode p : bugLeafNodes)
					{
						if (p.matches((BugAspects) getChild(path.getLastPathComponent(), i)))
						{
							tree.expandPath(path);
							crawlToOpen(path.pathByAddingChild(getChild(path.getLastPathComponent(), i)), bugLeafNodes, treePaths);
							break;
						}
					}
				else
				{
					for (BugLeafNode b: bugLeafNodes)
					{
						if (getChild(path.getLastPathComponent(),i).equals(b) )
						{
							tree.expandPath(path);
							treePaths.add(path.pathByAddingChild(getChild(path.getLastPathComponent(), i)));
						}
					}
				}
			}
		}

		public static boolean TRACE = false;
		public void resetData()//FIXME:  Does this need a setAsRootAndCache() on the new BugSet?
		{
			if (TRACE) System.out.println("Reseting data in bug tree model");
			bugSet=new BugSet(bugSet);
		}

		FilterListener bugTreeFilterListener = new MyFilterListener();
		class MyFilterListener implements FilterListener {
		public void clearCache()
		{
			if (TRACE) System.out.println("clearing cache in bug tree model");
			resetData();
			BugSet.setAsRootAndCache(bugSet);//FIXME:  Should this be in resetData?  Does this allow our main list to not be the same as the data in our tree?
			root.setCount(bugSet.size());

			rebuild();
		}

		public void unsuppressBug(TreePath path)
		{
			if (TRACE) System.out.println("unsuppressing bug");
			if (path==null)
				return;
			TreePath pathToFirstDeleted=null;
			Object[] objPath=path.getParentPath().getPath();
			ArrayList<Object> reconstruct=new ArrayList<Object>();
			boolean earlyStop=false;
			for (int x=0; x<objPath.length;x++)
			{
				Object o=objPath[x];
				reconstruct.add(o);
				if (o instanceof BugAspects)
				{
					pathToFirstDeleted=new TreePath(reconstruct.toArray());
					((BugAspects)o).setCount(((BugAspects)o).getCount()+1);

					if (((BugAspects)o).getCount()==2 && reconstruct.size() >1)
					{
						earlyStop=true;
						break;
					}

					for (TreeModelListener l: listeners)
					{
						if (pathToFirstDeleted.getParentPath()!=null)
							l.treeNodesChanged(new TreeModelEvent(this, pathToFirstDeleted.getParentPath(),new int[]{getIndexOfChild(pathToFirstDeleted.getParentPath().getLastPathComponent(),pathToFirstDeleted.getLastPathComponent())}, new Object[]{pathToFirstDeleted.getLastPathComponent()}));					
					}
				}
			}


			if (path.getParentPath()==null)//They are unsuppressing from the root, but we don't allow root to be suppressed, Dont know what to do here
			{
				throw new RuntimeException();
			}

			if (pathToFirstDeleted==null)
			{
				pathToFirstDeleted=path;
			}

			if (earlyStop==false)
			{
				pathToFirstDeleted=pathToFirstDeleted.pathByAddingChild(path.getLastPathComponent());
			}

			Object parent=pathToFirstDeleted.getParentPath().getLastPathComponent();
			Object child=pathToFirstDeleted.getLastPathComponent();

			TreeModelEvent insertionEvent=new TreeModelEvent(this, pathToFirstDeleted.getParentPath(),new int[]{getIndexOfChild(parent,child)}, new Object[]{child});
			for (TreeModelListener l: listeners)			
			{
				l.treeNodesInserted(insertionEvent);
			}
			if (!isLeaf(child))
			{
				TreeModelEvent structureEvent=new TreeModelEvent(this, pathToFirstDeleted,new int[0], new Object[0]);
				for (TreeModelListener l: listeners)			
				{			
					l.treeStructureChanged(structureEvent);
				}
			}
		}

		public void suppressBug(TreePath path)
		{
			if (TRACE) System.out.println("unsuppressing bug");
			Debug.println(path);
			Object[] objPath=path.getParentPath().getPath();
			ArrayList<Object> reconstruct=new ArrayList<Object>();
			for (int x=0; x< objPath.length;x++)
			{
				Object o=objPath[x];
				((BugAspects)o).setCount(((BugAspects)o).getCount()-1);
			}

			for (int x=0; x< objPath.length;x++)
			{
				Object o=objPath[x];
				reconstruct.add(o);
				if (o instanceof BugAspects)
				{
					if (((BugAspects)o).getCount()==0)
					{
						path=new TreePath(reconstruct.toArray());
						break;
					}
				}
			}

			TreeModelEvent event;

			if (path.getParentPath()==null)//They are suppressing the last bug in the tree
			{
				event=new TreeModelEvent(this,path,new int[]{0},new Object[]{BugTreeModel.this.getChild(root,0)});
				root.setCount(0);
			}
			else
			{
				Object parent = path.getParentPath().getLastPathComponent();
				Object child = path.getLastPathComponent();
				int indexOfChild=getIndexOfChild(parent,child);
				if (indexOfChild!=-1)
				{
					event=new TreeModelEvent(this, path.getParentPath(),new int[]{indexOfChild}, new Object[]{child});
					resetData();
				}
				else//They are suppressing something that has already been filtered out by setting a designation of a bug to a type that has been filtered out.
				{
					resetData();
					for (TreeModelListener l: listeners)
					{
						l.treeStructureChanged(new TreeModelEvent(this, path.getParentPath()));
					}
					return;
				}
			}

			for (TreeModelListener l: listeners)			
			{
				l.treeNodesRemoved(event);
			}
		}
		}
		void treeNodeChanged(TreePath path)
		{
			Debug.println("Tree Node Changed: " + path);
			if (path.getParentPath()==null)
			{
				TreeModelEvent event=new TreeModelEvent(this,path,null,null);
				for (TreeModelListener l:listeners)
				{
					l.treeNodesChanged(event);
				}
				return;
			}

			TreeModelEvent event=new TreeModelEvent(this,path.getParentPath(),new int[]{getIndexOfChild(path.getParentPath().getLastPathComponent(),path.getLastPathComponent())},new Object[] {path.getLastPathComponent()});
			for (TreeModelListener l: listeners)
			{
				l.treeNodesChanged(event);
			}
		}

		public TreePath getPathToBug(BugInstance b)
		{
			//ArrayList<Sortables> order=MainFrame.getInstance().getSorter().getOrder();
			List<Sortables> order=st.getOrderBeforeDivider();
			//Create an array of BugAspects of lengths from one to the full BugAspect list of the bugInstance	
			BugAspects[] toBug=new BugAspects[order.size()];
			for (int i=0; i < order.size(); i++)
				toBug[i]=new BugAspects();

			for (int x=0; x< order.size();x++)
			{
				for (int y=0; y<=x;y++)
				{
					Sortables s = order.get(y);
					toBug[x].add(new SortableValue(s,s.getFrom(b)));
				}
			}
			//Add this array as elements of the path
			TreePath pathToBug=new TreePath(root);
			for (int x=0;x<order.size();x++)
			{
				int index=getIndexOfChild(pathToBug.getLastPathComponent(),toBug[x]);

				if (index==-1)
				{
					if (MainFrame.GUI2_DEBUG) System.err.println("Node does not exist in the tree");//For example, not a bug bugs are filtered, they set a bug to be not a bug it filters out
					return null;
				}

				pathToBug=pathToBug.pathByAddingChild(getChild(pathToBug.getLastPathComponent(),index));
			}
			//Using a hashlist to store bugs in BugSet will make getIndexOfChild Waaaaaay faster, thus making this O(1) (avg case)
			int index=getIndexOfChild(pathToBug.getLastPathComponent(),new BugLeafNode(b));
			if(index == -1)
				return null;
			pathToBug=pathToBug.pathByAddingChild(getChild(pathToBug.getLastPathComponent(),index));
			return pathToBug;

		}

		public TreePath getPathToNewlyUnsuppressedBug(BugInstance b)
		{
			resetData();
			return getPathToBug(b);
		}

		@Override
		protected void finalize() throws Throwable
		{
			super.finalize();

			//this will inform us when the garbage collector finds our old bug tree models and deletes them, thus preventing obnoxiously hard to find bugs from not remembering to remove the model from our listeners
			Debug.println("The BugTreeModel has been DELETED!  This means there are no more references to it, and its finally off all of the stupid listener lists");
		}

		public void columnMarginChanged(ChangeEvent arg0) {}
		public void columnSelectionChanged(ListSelectionEvent arg0) {}

		public void treeExpanded(TreeExpansionEvent event) {
		}

		public void treeCollapsed(TreeExpansionEvent event) {
		}

		private void setOldSelectedBugs()
		{
			selectedBugLeafNodes.clear();
			if (tree.getSelectionPaths() != null) // Who the cussword wrote this API anyway?
				for (TreePath path : tree.getSelectionPaths())
					if (isLeaf(path.getLastPathComponent()))
						selectedBugLeafNodes.add((BugLeafNode) path.getLastPathComponent());
		}

		ArrayList<BugLeafNode> getOldSelectedBugs()
		{
			return selectedBugLeafNodes;
		}


		void checkSorter()
		{
			if (sortOrderChanged==true || sortsAddedOrRemoved==true)
			{
				sortOrderChanged=false;
				sortsAddedOrRemoved=false;
				rebuild();
			}
		}		


		public TreeModelEvent restructureBranch(ArrayList<String> stringsToBranch, boolean removing) throws BranchOperationException
		{
			if (removing)
				return branchOperations(stringsToBranch, TreeModification.REMOVERESTRUCTURE);
			else
				return branchOperations(stringsToBranch, TreeModification.INSERTRESTRUCTURE);
		}

		public TreeModelEvent insertBranch(ArrayList<String> stringsToBranch) throws BranchOperationException
		{
			return branchOperations(stringsToBranch, TreeModification.INSERT);
		}

		public TreeModelEvent removeBranch(ArrayList<String> stringsToBranch) throws BranchOperationException
		{
			return branchOperations(stringsToBranch, TreeModification.REMOVE);
		}

		public void sortBranch(TreePath pathToBranch)
		{
			BugSet bs=bugSet.query((BugAspects)pathToBranch.getLastPathComponent());
			bs.sortList();
			Debug.println("Data in sorted branch: " + pathToBranch.getLastPathComponent());
			for (BugLeafNode b: bs)
			{
				Debug.println(b);
			}

			Object[] children=new Object[getChildCount(pathToBranch.getLastPathComponent())];
			int[] childIndices=new int[children.length];
			for (int x=0; x<children.length; x++)
			{
				children[x]=getChild(pathToBranch.getLastPathComponent(),x);
				childIndices[x]=x;
			}
			for (TreeModelListener l: listeners)
			{
				TreeModelEvent event=new TreeModelEvent(this,pathToBranch,childIndices,children);
				l.treeNodesChanged(event);
			}

		}

		@SuppressWarnings("serial")
		static class BranchOperationException extends Exception
		{
			public BranchOperationException(String s)
			{
				super(s);
			}
		}
		enum TreeModification {REMOVE, INSERT, REMOVERESTRUCTURE, INSERTRESTRUCTURE};
		private TreeModelEvent branchOperations(ArrayList<String> stringsToBranch, TreeModification whatToDo) throws BranchOperationException
		{
			TreeModelEvent event=null;

			if (whatToDo==TreeModification.REMOVE)
				Debug.println("Removing a branch......");
			else if (whatToDo==TreeModification.INSERT)
				Debug.println("Inserting a branch......");
			else if (whatToDo==TreeModification.REMOVERESTRUCTURE)
				Debug.println("Restructuring from branch to remove......");
			else if (whatToDo==TreeModification.INSERTRESTRUCTURE)
				Debug.println("Restructuring from branch to insert......");
			Debug.println(stringsToBranch);

			if (whatToDo==TreeModification.INSERT || whatToDo==TreeModification.INSERTRESTRUCTURE)
			{
				resetData();
			}
			//ArrayList<Sortables> order=MainFrame.getInstance().getSorter().getOrder();
			List<Sortables> order=st.getOrderBeforeDivider();
			//Create an array of BugAspects of lengths from one to the full BugAspect list of the bugInstance	
			BugAspects[] toBug=new BugAspects[stringsToBranch.size()];
			for (int x=0; x < stringsToBranch.size(); x++) {
				toBug[x]=new BugAspects();

				for (int y=0; y<=x;y++)
				{
					Sortables s = order.get(y);
					toBug[x].add(new SortableValue(s,stringsToBranch.get(y)));
				}
			}

			//Add this array as elements of the path
			TreePath pathToBranch=new TreePath(root);
			for (int x=0;x<stringsToBranch.size();x++)
			{
				BugAspects child=toBug[x];
				BugAspects parent=(BugAspects) pathToBranch.getLastPathComponent();
				if (getIndexOfChild(parent,child)!=-1)
				{
					pathToBranch=pathToBranch.pathByAddingChild(child);
				}
				else
				{
					Debug.println(parent + " does not contain " + child);
					throw new BranchOperationException("Branch has been filtered out by another filter.");
//					break;
				}
			}
			if (pathToBranch.getParentPath()!=null)
				while (getChildCount(pathToBranch.getParentPath().getLastPathComponent())==1)
				{
					if (pathToBranch.getParentPath().getLastPathComponent().equals(root))
						break;
					pathToBranch=pathToBranch.getParentPath();
				}
			Debug.println(pathToBranch);


			if (whatToDo==TreeModification.INSERT)
			{
				event=new TreeModelEvent(this,pathToBranch.getParentPath(),new int[]{getIndexOfChild(pathToBranch.getParentPath().getLastPathComponent(),pathToBranch.getLastPathComponent())}, new Object[]{pathToBranch.getLastPathComponent()});				
			}
			else if (whatToDo==TreeModification.INSERTRESTRUCTURE)
			{
				event=new TreeModelEvent(this,pathToBranch);
			}

			if (whatToDo==TreeModification.REMOVE)
			{
				event=new TreeModelEvent(this,pathToBranch.getParentPath(),new int[]{getIndexOfChild(pathToBranch.getParentPath().getLastPathComponent(),pathToBranch.getLastPathComponent())}, new Object[]{pathToBranch.getLastPathComponent()});

			}
			else if (whatToDo==TreeModification.REMOVERESTRUCTURE)
			{
				event=new TreeModelEvent(this,pathToBranch);
			}

			if (whatToDo==TreeModification.REMOVE || whatToDo==TreeModification.REMOVERESTRUCTURE)
				resetData();

			return event;
		}		

		void sendEvent(TreeModelEvent event, TreeModification whatToDo)
		{
			Debug.println("Sending An Event!");
			if (event==null)
			{
				throw new IllegalStateException("Dont throw null events.");
			}
			resetData();
			for (TreeModelListener l: listeners)
			{
				if (whatToDo==TreeModification.REMOVE)
					l.treeNodesRemoved(event);
				else if (whatToDo==TreeModification.INSERT)
				{
					l.treeNodesInserted(event);
					l.treeStructureChanged(new TreeModelEvent(this,new TreePath(event.getPath()).pathByAddingChild(event.getChildren()[0])));
				}
				else if (whatToDo==TreeModification.INSERTRESTRUCTURE || whatToDo==TreeModification.REMOVERESTRUCTURE)
				{
					l.treeStructureChanged(event);
				}
			}

			root.setCount(bugSet.size());
			TreePath changedPath=new TreePath(root);
			treeNodeChanged(changedPath);
			changedPath=new TreePath(event.getPath());
			while (changedPath.getParentPath()!=null)
			{
				treeNodeChanged(changedPath);
				changedPath=changedPath.getParentPath();
			}
		}

	}
