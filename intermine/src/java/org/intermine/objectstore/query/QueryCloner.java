package org.flymine.objectstore.query;

/*
 * Copyright (C) 2002-2003 FlyMine
 *
 * This code may be freely distributed and modified under the
 * terms of the GNU Lesser General Public Licence.  This should
 * be distributed with the code.  See the LICENSE file for more
 * information or http://www.gnu.org/copyleft/lesser.html.
 *
 */

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;

/**
 * This is a static class that provides a method to clone a Query object.
 *
 * @author Matthew Wakeling
 */
public class QueryCloner
{
    /**
     * Clones a query object.
     *
     * @param query a Query to clone
     * @return a Query object not connected to the original, but identical
     */
    public static Query cloneQuery(Query query) {
        Query newQuery = new Query();
        try {
            Map aliases = query.getAliases();
            Map fromElementMap = new HashMap();
            Iterator fromIter = query.getFrom().iterator();
            while (fromIter.hasNext()) {
                FromElement origFrom = (FromElement) fromIter.next();
                FromElement newFrom = null;
                if (origFrom instanceof QueryClass) {
                    newFrom = new QueryClass(((QueryClass) origFrom).getType());
                } else if (origFrom instanceof Query) {
                    newFrom = cloneQuery((Query) origFrom);
                } else {
                    throw new IllegalArgumentException("Unknown type of FromElement " + origFrom);
                }
                newQuery.addFrom(newFrom, (String) aliases.get(origFrom));
                fromElementMap.put(origFrom, newFrom);
            }
            Iterator selectIter = query.getSelect().iterator();
            while (selectIter.hasNext()) {
                QueryNode origSelect = (QueryNode) selectIter.next();
                QueryNode newSelect = (QueryNode) cloneThing(origSelect, fromElementMap);
                newQuery.addToSelect(newSelect, (String) aliases.get(origSelect));
            }
            Iterator orderIter = query.getOrderBy().iterator();
            while (orderIter.hasNext()) {
                QueryNode origOrder = (QueryNode) orderIter.next();
                QueryNode newOrder = (QueryNode) cloneThing(origOrder, fromElementMap);
                newQuery.addToOrderBy(newOrder);
            }
            Iterator groupIter = query.getGroupBy().iterator();
            while (groupIter.hasNext()) {
                QueryNode origGroup = (QueryNode) groupIter.next();
                QueryNode newGroup = (QueryNode) cloneThing(origGroup, fromElementMap);
                newQuery.addToGroupBy(newGroup);
            }
            newQuery.setConstraint((Constraint) cloneThing(query.getConstraint(), fromElementMap));
            newQuery.setDistinct(query.isDistinct());
        } catch (NoSuchFieldException e) {
            throw new IllegalArgumentException("No such field: " + e.getMessage());
        }
        return newQuery;
    }

    private static Object cloneThing(Object orig, Map fromElementMap)
            throws NoSuchFieldException {
        if (orig == null) {
            return null;
        } else if (orig instanceof FromElement) {
            return fromElementMap.get(orig);
        } else if (orig instanceof QueryField) {
            QueryField origF = (QueryField) orig;
            return new QueryField((FromElement) fromElementMap.get(origF.getFromElement()),
                    origF.getFieldName(), origF.getSecondFieldName(), origF.getType());
        } else if (orig instanceof QueryObjectReference) {
            QueryObjectReference origR = (QueryObjectReference) orig;
            return new QueryObjectReference((QueryClass) fromElementMap.get(origR.getQueryClass()),
                    origR.getFieldName());
        } else if (orig instanceof QueryCollectionReference) {
            QueryCollectionReference origR = (QueryCollectionReference) orig;
            return new QueryCollectionReference((QueryClass)
                    fromElementMap.get(origR.getQueryClass()), origR.getFieldName());
        } else if (orig instanceof QueryValue) {
            return new QueryValue(((QueryValue) orig).getValue());
        } else if (orig instanceof QueryFunction) {
            QueryFunction origF = (QueryFunction) orig;
            if (origF.getOperation() == QueryFunction.COUNT) {
                return orig;
            } else if (origF.getParam() instanceof QueryField) {
                return new QueryFunction((QueryField) cloneThing(origF.getParam(), fromElementMap),
                        origF.getOperation());
            } else {
                return new QueryFunction((QueryExpression) cloneThing(origF.getParam(),
                            fromElementMap), origF.getOperation());
            }
        } else if (orig instanceof QueryExpression) {
            QueryExpression origE = (QueryExpression) orig;
            if ((origE.getOperation() == QueryExpression.SUBSTRING) && (origE.getArg3() != null)) {
                return new QueryExpression((QueryEvaluable)
                        cloneThing(origE.getArg1(), fromElementMap),
                        (QueryEvaluable) cloneThing(origE.getArg2(), fromElementMap),
                        (QueryEvaluable) cloneThing(origE.getArg3(), fromElementMap));
            } else {
                return new QueryExpression((QueryEvaluable)
                        cloneThing(origE.getArg1(), fromElementMap),
                        origE.getOperation(),
                        (QueryEvaluable) cloneThing(origE.getArg2(), fromElementMap));
            }
        } else if (orig instanceof QueryCast) {
            return new QueryCast((QueryEvaluable) cloneThing(((QueryCast) orig).getValue(),
                        fromElementMap), ((QueryCast) orig).getType());
        } else if (orig instanceof SimpleConstraint) {
            SimpleConstraint origC = (SimpleConstraint) orig;
            if ((origC.getOp() == ConstraintOp.IS_NULL)
                    || (origC.getOp() == ConstraintOp.IS_NOT_NULL)) {
                return new SimpleConstraint((QueryEvaluable) cloneThing(origC.getArg1(),
                            fromElementMap), origC.getOp());
            } else {
                return new SimpleConstraint((QueryEvaluable) cloneThing(origC.getArg1(),
                            fromElementMap), origC.getOp(),
                        (QueryEvaluable) cloneThing(origC.getArg2(), fromElementMap));
            }
        } else if (orig instanceof ConstraintSet) {
            ConstraintSet origC = (ConstraintSet) orig;
            ConstraintSet newC = new ConstraintSet(origC.getOp());
            Iterator conIter = origC.getConstraints().iterator();
            while (conIter.hasNext()) {
                newC.addConstraint((Constraint) cloneThing(conIter.next(), fromElementMap));
            }
            return newC;
        } else if (orig instanceof ContainsConstraint) {
            ContainsConstraint origC = (ContainsConstraint) orig;
            if (origC.getOp().equals(ConstraintOp.IS_NULL) || origC.getOp().equals(
                        ConstraintOp.IS_NOT_NULL)) {
                return new ContainsConstraint((QueryObjectReference) cloneThing(
                            origC.getReference(), fromElementMap), origC.getOp());
            } else if (origC.getQueryClass() == null) {
                return new ContainsConstraint((QueryReference) cloneThing(origC.getReference(),
                            fromElementMap), origC.getOp(), origC.getObject());
            } else {
                return new ContainsConstraint((QueryReference) cloneThing(origC.getReference(),
                            fromElementMap), origC.getOp(),
                        (QueryClass) cloneThing(origC.getQueryClass(), fromElementMap));
            }
        } else if (orig instanceof ClassConstraint) {
            ClassConstraint origC = (ClassConstraint) orig;
            if (origC.getArg2QueryClass() == null) {
                return new ClassConstraint((QueryClass) fromElementMap.get(origC.getArg1()),
                        origC.getOp(), origC.getArg2Object());
            } else {
                return new ClassConstraint((QueryClass) fromElementMap.get(origC.getArg1()),
                        origC.getOp(),
                        (QueryClass) fromElementMap.get(origC.getArg2QueryClass()));
            }
        } else if (orig instanceof SubqueryConstraint) {
            SubqueryConstraint origC = (SubqueryConstraint) orig;
            if (origC.getQueryEvaluable() == null) {
                return new SubqueryConstraint((QueryClass)
                        fromElementMap.get(origC.getQueryClass()), origC.getOp(),
                        cloneQuery(origC.getQuery()));
            } else {
                return new SubqueryConstraint(
                        (QueryEvaluable) cloneThing(origC.getQueryEvaluable(), fromElementMap),
                        origC.getOp(), cloneQuery(origC.getQuery()));
            }
        } else if (orig instanceof BagConstraint) {
            BagConstraint origC = (BagConstraint) orig;
            return new BagConstraint((QueryNode) cloneThing(origC.getQueryNode(), fromElementMap),
                                     origC.getOp(), new HashSet(origC.getBag()));
        }
        throw new IllegalArgumentException("Unknown object type: " + orig);
    }
}
