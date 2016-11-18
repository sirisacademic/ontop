package it.unibz.inf.ontop.pivotalrepr.impl;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import it.unibz.inf.ontop.model.Variable;
import it.unibz.inf.ontop.pivotalrepr.*;

import java.util.Optional;
import java.util.stream.Stream;

/**
 * Mutable component used for internal implementations of IntermediateQuery.
 */
public interface QueryTreeComponent {

    ImmutableList<QueryNode> getChildren(QueryNode node);

    Stream<QueryNode> getChildrenStream(QueryNode node);

    ConstructionNode getRootConstructionNode() throws IllegalTreeException;

    ImmutableList<QueryNode> getNodesInBottomUpOrder() throws IllegalTreeException;

    ImmutableList<QueryNode> getNodesInTopDownOrder() throws IllegalTreeException;

    ImmutableSet<EmptyNode> getEmptyNodes();

    ImmutableSet<TrueNode> getTrueNodes();

    ImmutableSet<IntensionalDataNode> getIntensionalNodes();

    boolean contains(QueryNode node);

    void replaceNode(QueryNode previousNode, QueryNode replacingNode);

    /**
     * Replaces all the sub-tree by one sub-tree node
     */
    void replaceSubTree(QueryNode subTreeRootNode, QueryNode replacingNode);

    void addSubTree(IntermediateQuery subQuery, QueryNode subQueryTopNode, QueryNode localTopNode)
            throws IllegalTreeUpdateException;

    void removeSubTree(QueryNode subTreeRoot);

    /**
     * All the nodes EXCEPT the root of this sub-tree
     */
    ImmutableList<QueryNode> getSubTreeNodesInTopDownOrder(QueryNode currentNode);

    Optional<NonCommutativeOperatorNode.ArgumentPosition> getOptionalPosition(QueryNode parentNode,
                                                                              QueryNode childNode);

    /**
     * From the parent to the oldest ancestor.
     */
    ImmutableList<QueryNode> getAncestors(QueryNode descendantNode) throws IllegalTreeException;

    Optional<QueryNode> getParent(QueryNode node) throws IllegalTreeException;

    /**
     * TODO: explain
     */
    QueryNode removeOrReplaceNodeByUniqueChildren(QueryNode node) throws IllegalTreeUpdateException;

    /**
     * TODO: explain
     */
    void replaceNodesByOneNode(ImmutableList<QueryNode> queryNodes, QueryNode replacingNode, QueryNode parentNode,
                               Optional<NonCommutativeOperatorNode.ArgumentPosition> optionalPosition)
            throws IllegalTreeUpdateException;

    /**
     * Please consider using an IntermediateQueryBuilder instead of this tree component.
     */
    void addChild(QueryNode parentNode, QueryNode childNode,
                  Optional<NonCommutativeOperatorNode.ArgumentPosition> optionalPosition,
                  boolean canReplacePreviousChildren) throws IllegalTreeUpdateException;

    Optional<QueryNode> nextSibling(QueryNode node) throws IllegalTreeException;

    Optional<QueryNode> getFirstChild(QueryNode node);

    /**
     * Inserts a new node between a node and its former parent (now grand-parent)
     */
    void insertParent(QueryNode childNode, QueryNode newParentNode) throws IllegalTreeUpdateException;

    /**
     * Inserts a new node between a node and its former parent (now grand-parent)
     */
    void insertParent(QueryNode childNode, QueryNode newParentNode, Optional<NonCommutativeOperatorNode.ArgumentPosition> optionalPosition)
            throws IllegalTreeUpdateException;

    /**
     * Transfers a child node from a parent to another parent
     */
    void transferChild(QueryNode childNode, QueryNode formerParentNode, QueryNode newParentNode,
                       Optional<NonCommutativeOperatorNode.ArgumentPosition> optionalPosition)
            throws IllegalTreeUpdateException;


    /**
     * Returns a variable that is not used in the intermediate query.
     */
    Variable generateNewVariable();

    /**
     * Returns a variable that is not used in the intermediate query.
     *
     * The new variable always differs from the former one.
     *
     */
    Variable generateNewVariable(Variable formerVariable);

    /**
     * All the possibly already allocated variables
     */
    ImmutableSet<Variable> getKnownVariables();

    /**
     * If no position is given, replaces the parent node by its first child
     */
    QueryNode replaceNodeByChild(QueryNode parentNode,
                                 Optional<NonCommutativeOperatorNode.ArgumentPosition> optionalReplacingChildPosition);


    /**
     * Keeps the same query node objects but clones the tree edges
     * (since the latter are mutable by default).
     */
    QueryTreeComponent createSnapshot();

    ImmutableSet<Variable> getVariables(QueryNode node);


    /**
     * The version number of the query.
     * Used in fixed-point optimization.
     */
    int getVersionNumber();
}
