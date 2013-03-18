/* Copyright (c) 2001-2009, The HSQL Development Group
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * Neither the name of the HSQL Development Group nor the names of its
 * contributors may be used to endorse or promote products derived from this
 * software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL HSQL DEVELOPMENT GROUP, HSQLDB.ORG,
 * OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */


package org.hsqldb_voltpatches;

import java.util.ArrayList;

import org.hsqldb_voltpatches.HSQLInterface.HSQLParseException;
import org.hsqldb_voltpatches.HsqlNameManager.HsqlName;
import org.hsqldb_voltpatches.ParserDQL.CompileContext;
import org.hsqldb_voltpatches.lib.OrderedHashSet;
import org.hsqldb_voltpatches.result.Result;
import org.hsqldb_voltpatches.result.ResultMetaData;

/**
 * Implementation of Statement for query expressions.<p>
 *
 * @author Fred Toussi (fredt@users dot sourceforge.net)
 * @version 1.9.0
 * @since 1.9.0
 */
public class StatementQuery extends StatementDMQL {

    StatementQuery(Session session, QueryExpression queryExpression,
                   CompileContext compileContext) {

        super(StatementTypes.SELECT_CURSOR, StatementTypes.X_SQL_DATA,
              session.currentSchema);

        this.queryExpression = queryExpression;

        setDatabseObjects(compileContext);
        checkAccessRights(session);
    }

    StatementQuery(Session session, QueryExpression queryExpression,
                   CompileContext compileContext, HsqlName[] targets) {

        super(StatementTypes.SELECT_SINGLE, StatementTypes.X_SQL_DATA,
              session.currentSchema);

        this.queryExpression = queryExpression;

        setDatabseObjects(compileContext);
        checkAccessRights(session);
    }

    @Override
    Result getResult(Session session) {

        Result result = queryExpression.getResult(session,
            session.getMaxRows());

        result.setStatement(this);

        return result;
    }

    @Override
    public ResultMetaData getResultMetaData() {

        switch (type) {

            case StatementTypes.SELECT_CURSOR :
                return queryExpression.getMetaData();

            case StatementTypes.SELECT_SINGLE :
                return queryExpression.getMetaData();

            default :
                throw Error.runtimeError(
                    ErrorCode.U_S0500,
                    "CompiledStatement.getResultMetaData()");
        }
    }

    @Override
    void getTableNamesForRead(OrderedHashSet set) {

        queryExpression.getBaseTableNames(set);

        for (SubQuery subquerie : subqueries) {
            if (subquerie.queryExpression != null) {
                subquerie.queryExpression.getBaseTableNames(set);
            }
        }
    }

    @Override
    void getTableNamesForWrite(OrderedHashSet set) {}

    /**
     * Returns true if the specified exprColumn index is in the list of column indices specified by groupIndex
     * @return true/false
     */
    private boolean isGroupByColumn(QuerySpecification select, int index) {
        if (!select.isGrouped) {
            return false;
        }
        for (int ii : select.groupIndex.getColumns()) {
            if (index == ii) {
                return true;
            }
        }
        return false;
    }

    /*************** VOLTDB *********************/

    /**
     * VoltDB added method to get a non-catalog-dependent
     * representation of this HSQLDB object.
     * @param session The current Session object may be needed to resolve
     * some names.
     * @return XML, correctly indented, representing this object.
     * @throws HSQLParseException
     */
    @Override
    VoltXMLElement voltGetStatementXML(Session session)
    throws HSQLParseException
    {
        return voltGetXMLExpression(queryExpression, session);
    }

    VoltXMLElement voltGetXMLExpression(QueryExpression queryExpr, Session session)
    throws HSQLParseException
    {
        // "select" statements/clauses are always represented by a QueryExpression of type QuerySpecification.
        // The only other instances of QueryExpression are direct QueryExpression instances instantiated in XreadSetOperation
        // to represent UNION, etc.
        int exprType = queryExpr.getUnionType();
        if (exprType == QueryExpression.NOUNION) {
            // "select" statements/clauses are always represented by a QueryExpression of type QuerySpecification.
            if (! (queryExpr instanceof QuerySpecification)) {
                throw new HSQLParseException(queryExpr.operatorName() + " is not supported.");
            }
            QuerySpecification select = (QuerySpecification) queryExpr;
            return voltGetXMLSpecification(select, session);
        } else if (exprType == QueryExpression.UNION || exprType == QueryExpression.UNION_ALL ||
                   exprType == QueryExpression.EXCEPT || exprType == QueryExpression.EXCEPT_ALL ||
                   exprType == QueryExpression.INTERSECT || exprType == QueryExpression.INTERSECT_ALL){
            VoltXMLElement unionExpr = new VoltXMLElement("union");
            unionExpr.attributes.put("uniontype", queryExpr.operatorName());

            VoltXMLElement leftExpr = voltGetXMLExpression(
                    queryExpr.getLeftQueryExpression(), session);
            VoltXMLElement rightExpr = voltGetXMLExpression(
                    queryExpr.getRightQueryExpression(), session);
            /**
             * Try to merge parent and the child nodes for UNION and INTERSECT (ALL) set operation.
             * In case of EXCEPT(ALL) operation only the left child can be merged with the parent in order to preserve
             * associativity - (Select1 EXCEPT Select2) EXCEPT Select3 vs. Select1 EXCEPT (Select2 EXCEPT Select3)
             */
            if ("union".equalsIgnoreCase(leftExpr.name) &&
                    queryExpr.operatorName().equalsIgnoreCase(leftExpr.attributes.get("uniontype"))) {
                unionExpr.children.addAll(leftExpr.children);
            } else {
                unionExpr.children.add(leftExpr);
            }
            if (exprType != QueryExpression.EXCEPT && exprType != QueryExpression.EXCEPT_ALL &&
                "union".equalsIgnoreCase(rightExpr.name) &&
                queryExpr.operatorName().equalsIgnoreCase(rightExpr.attributes.get("uniontype"))) {
                unionExpr.children.addAll(rightExpr.children);
            } else {
                unionExpr.children.add(rightExpr);
            }
            return unionExpr;
        } else {
            throw new HSQLParseException(queryExpression.operatorName() + "  tuple set operator is not supported.");
        }
    }

    VoltXMLElement voltGetXMLSpecification(QuerySpecification select, Session session)
    throws HSQLParseException {

        // select
        VoltXMLElement query = new VoltXMLElement("select");
        if (select.isDistinctSelect)
            query.attributes.put("distinct", "true");

        // limit
        if ((select.sortAndSlice != null) && (select.sortAndSlice.limitCondition != null)) {
            Expression limitCondition = select.sortAndSlice.limitCondition;
            if (limitCondition.nodes.length != 2) {
                throw new HSQLParseException("Parser did not create limit and offset expression for LIMIT.");
            }
            try {
                // read offset. it may be a parameter token.
                if (limitCondition.nodes[0].isParam() == false) {
                    Integer offset = (Integer)limitCondition.nodes[0].getValue(session);
                    if (offset > 0) {
                        query.attributes.put("offset", offset.toString());
                    }
                }
                else {
                    query.attributes.put("offset_paramid", limitCondition.nodes[0].getUniqueId(session));
                }

                // read limit. it may be a parameter token.
                if (limitCondition.nodes[1].isParam() == false) {
                    Integer limit = (Integer)limitCondition.nodes[1].getValue(session);
                    query.attributes.put("limit", limit.toString());
                }
                else {
                    query.attributes.put("limit_paramid", limitCondition.nodes[1].getUniqueId(session));
                }
            } catch (HsqlException ex) {
                // XXX really?
                ex.printStackTrace();
            }
        }

        // columns
        VoltXMLElement cols = new VoltXMLElement("columns");
        query.children.add(cols);

        ArrayList<Expression> orderByCols = new ArrayList<Expression>();
        ArrayList<Expression> groupByCols = new ArrayList<Expression>();
        ArrayList<Expression> displayCols = new ArrayList<Expression>();

        /*
         * select.exprColumns stores all of the columns needed by HSQL to
         * calculate the query's result set. It contains more than just the
         * columns in the output; for example, it contains columns representing
         * aliases, columns for groups, etc.
         *
         * Volt uses multiple collections to organize these columns.
         *
         * Observing this loop in a debugger, the following seems true:
         *
         * 1. Columns in exprColumns that appear in the output schema, appear in
         * exprColumns in the same order that they occur in the output schema.
         *
         * 2. expr.columnIndex is an index back in to the select.exprColumns
         * array. This allows multiple exprColumn entries to refer to each
         * other; for example, an OpType.SIMPLE_COLUMN type storing an alias
         * will have its columnIndex set to the offset of the expr it aliases.
         */
        for (int kk = 0; kk < select.exprColumns.length; kk++) {
            final Expression expr = select.exprColumns[kk];

            // This is a summary of the effective filtering for the source of an alias
            // that was used in the earlier "woodchuck" version of this code --
            // anything with an alias and a valid columnIndex that is either a column expression
            // without a column name or a non-column expression.
            if (expr.alias != null &&
                expr.columnIndex > -1 &&
                ( ( ! (expr instanceof ExpressionColumn) ) ||
                  ( ((ExpressionColumn)expr).columnName == null ))) {
                select.exprColumns[expr.columnIndex].alias = expr.alias;
            }

            if (isGroupByColumn(select, kk)) {
                groupByCols.add(expr);
            } else if (expr.opType == OpTypes.ORDER_BY) {
                orderByCols.add(expr);
            } else if (expr.opType != OpTypes.SIMPLE_COLUMN || (expr.isAggregate && expr.alias != null)) {
                // Add aggregate aliases to the display columns to maintain
                // the output schema column ordering.
                displayCols.add(expr);
            }
            // XXX: Why are other (than "aliased aggregate") SIMPLE_COLUMNs being ignored, and why?
            // One possibility is that they just exist to provide an alias for another column
            // -- and that case has just been handled. Is that it? Does that cover all the cases?
        }

        /*
         * The columns chosen above as display columns aren't always the same
         * expr objects HSQL would use as display columns - some data were
         * unified (namely, SIMPLE_COLUMN aliases were pushed into COLUMNS).
         *
         * However, the correct output schema ordering was correct in exprColumns.
         * This order was maintained by adding SIMPLE_COLUMNs to displayCols.
         * XXX: But this was only done if they were aliased aggregates?
         *
         * Now need to serialize the displayCols, serializing the non-simple-columns
         * corresponding to simple_columns for any simple_columns that woodchucks
         * could chuck. (aliased aggregates only, mighty particular for woodchucks)
         *
         * Serialize the display columns in the exprColumn order.
         */
        for (int jj=0; jj < displayCols.size(); ++jj) {
            Expression expr = displayCols.get(jj);
            if (expr != null && expr.opType == OpTypes.SIMPLE_COLUMN) {
                // simple columns are not serialized as display columns
                // but they are place holders for another column
                // that follows later in the displayCols list.
                // in the output schema. Go find that corresponding column
                // and serialize it in this place.
                int targetIndex = expr.columnIndex;
                expr = null; // Skip serilalizing this one if it finds no match.
                for (int ii=jj+1; ii < displayCols.size(); ++ii) {
                    Expression otherCol = displayCols.get(ii);
                    if (otherCol != null &&
                        (otherCol.opType != OpTypes.SIMPLE_COLUMN) &&
                        (otherCol.columnIndex == targetIndex)) {
                        // serialize the column this simple column stands-in for
                        expr = otherCol;
                        // null-out otherCol to not serialize it twice
                        displayCols.set(ii, null);
                        // quit seeking the simple column's replacement
                        break;
                    }
                }
            }
            // Skip SIMPLE_COLUMNs that match no later column
            // or other columns that were matched by an earlier SIMPLE_COLUMN.
            if (expr == null) {
                continue;
            }
            VoltXMLElement xml = expr.voltGetXML(session);
            cols.children.add(xml);
            assert(xml != null);
        }

        // parameters
        voltAppendParameters(session, query);

        // scans
        VoltXMLElement scans = new VoltXMLElement("tablescans");
        query.children.add(scans);
        assert(scans != null);

        for (RangeVariable rangeVariable : rangeVariables)
            scans.children.add(rangeVariable.voltGetRangeVariableXML(session));

        Expression cond = null;
        // conditions
        // XXX: Are queryCondition and rv.nonIndexJoinCondition and rv.indexCondition/rv.indexEndCondition
        // REALLY mutually exclusive, or might they be complementary? or might they be partially redundant?
        // look for inner joins expressed on range variables. It may be that we can't experience all of
        // the possible combinations until we support joins with ON and USING clauses.
        if (select.queryCondition != null) {
            cond = select.queryCondition;
        } else {
            for (int rvi=0; rvi < select.rangeVariables.length; ++rvi) {
                RangeVariable rv = rangeVariables[rvi];
                // joins on non-indexed columns for inner join tokens created a range variable
                // and assigned this expression.
                if (rv.nonIndexJoinCondition != null) {
                    cond = voltCombineWithAnd(cond, rv.nonIndexJoinCondition);
                }
                // joins on indexed columns for inner join tokens created a range variable
                // and assigned an expression and set the flag isJoinIndex.
                else if (rv.isJoinIndex) {
                    cond = voltCombineWithAnd(cond, rv.indexCondition, rv.indexEndCondition);
                }
            }
        }
        if (cond != null) {
            VoltXMLElement condition = new VoltXMLElement("querycondition");
            query.children.add(condition);
            condition.children.add(cond.voltGetXML(session));
        }

        // having
        if (select.havingCondition != null) {
            throw new HSQLParseException("VoltDB does not support the HAVING clause");
        }

        // groupby
        if (select.isGrouped) {
            VoltXMLElement groupCols = new VoltXMLElement("groupcolumns");
            query.children.add(groupCols);
            for (Expression groupByCol : groupByCols) {
                groupCols.children.add(groupByCol.voltGetXML(session));
            }
        }
        // orderby
        if (orderByCols.size() > 0) {
            VoltXMLElement orderCols = new VoltXMLElement("ordercolumns");
            query.children.add(orderCols);
            for (Expression orderByCol : orderByCols) {
                orderCols.children.add(orderByCol.voltGetXML(session));
            }
        }

        return query;
    }
}
