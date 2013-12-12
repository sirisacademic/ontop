package it.unibz.krdb.obda.protege4.gui.treemodels;

import it.unibz.krdb.obda.model.OBDAMappingAxiom;
import it.unibz.krdb.obda.protege4.gui.treemodels.TreeModelFilter;

public class MappingIDTreeModelFilter extends TreeModelFilter<OBDAMappingAxiom> {

	public MappingIDTreeModelFilter() {
		super.bNegation = false;
	}

	@Override
	public boolean match(OBDAMappingAxiom object) {
		boolean isMatch = false;
		for (String keyword : vecKeyword) {
			isMatch = match(keyword.trim(), object.getId());
			if (isMatch) {
				break; // end loop if a match is found!
			}
		}
		// no match found!
		return (bNegation ? !isMatch : isMatch);
	}

	/** A helper method to check a match */
	public static boolean match(String keyword, String mappingId) {
		if (mappingId.indexOf(keyword) != -1) { // match found!
			return true;
		}
		return false;
	}
}
