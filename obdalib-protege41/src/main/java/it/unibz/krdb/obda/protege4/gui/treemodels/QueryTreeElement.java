package it.unibz.krdb.obda.protege4.gui.treemodels;

import it.unibz.krdb.obda.protege4.gui.treemodels.TreeElement;

import javax.swing.tree.DefaultMutableTreeNode;

public class QueryTreeElement extends DefaultMutableTreeNode implements TreeElement {

	private static final long serialVersionUID = -5221902062065891204L;
	private String id = "";
	private String query = "";

	public QueryTreeElement(String id, String query) {
		this.id = id;
		setQuery(query);
		setAllowsChildren(false);
	}

	public String getID() {
		return id;
	}

	public void setQuery(String query) {
		this.query = query;
	}

	public String getQuery() {
		return query;
	}

	public String getNodeName() {
		return id + ": " + query.toString();
	}

	public String toString() {
		return getNodeName();
	}

	@Override
	public Object getUserObject() {
		return getNodeName();
	}
}
