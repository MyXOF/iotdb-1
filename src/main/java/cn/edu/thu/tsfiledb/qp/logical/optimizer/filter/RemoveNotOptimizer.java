package cn.edu.thu.tsfiledb.qp.logical.optimizer.filter;

import static cn.edu.thu.tsfiledb.qp.constant.SQLConstant.KW_AND;
import static cn.edu.thu.tsfiledb.qp.constant.SQLConstant.KW_NOT;
import static cn.edu.thu.tsfiledb.qp.constant.SQLConstant.KW_OR;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cn.edu.thu.tsfiledb.qp.constant.SQLConstant;
import cn.edu.thu.tsfiledb.qp.exception.QueryProcessorException;
import cn.edu.thu.tsfiledb.qp.exception.logical.operator.BasicOperatorException;
import cn.edu.thu.tsfiledb.qp.exception.logical.optimize.LogicalOptimizeException;
import cn.edu.thu.tsfiledb.qp.exception.logical.optimize.RemoveNotException;
import cn.edu.thu.tsfiledb.qp.logical.operator.clause.filter.BasicFunctionOperator;
import cn.edu.thu.tsfiledb.qp.logical.operator.clause.filter.FilterOperator;

public class RemoveNotOptimizer implements IFilterOptimizer {
    private static final Logger LOG = LoggerFactory.getLogger(RemoveNotOptimizer.class);

    /**
     * get DNF(disjunctive normal form) for this filter operator tree. Before getDNF, this op tree
     * must be binary, in another word, each non-leaf node has exactly two children.
     * 
     * @return
     * @throws LogicalOptimizeException
     */
    @Override
    public FilterOperator optimize(FilterOperator filter) throws LogicalOptimizeException {
        return removeNot(filter);
    }

    private FilterOperator removeNot(FilterOperator filter) throws RemoveNotException {
        if (filter.isLeaf())
            return filter;
        int tokenInt = filter.getTokenIntType();
        switch (tokenInt) {
            case KW_AND:
            case KW_OR:
                // replace children in-place for efficiency
                List<FilterOperator> children = filter.getChildren();
                children.set(0, removeNot(children.get(0)));
                children.set(1, removeNot(children.get(1)));
                return filter;
            case KW_NOT:
                return reverseFilter(filter.getChildren().get(0));
            default:
                throw new RemoveNotException("Unknown token in removeNot: " + tokenInt + ","
                        + SQLConstant.tokenNames.get(tokenInt));
        }
    }

    /**
     * reverse given filter to reversed expression
     * 
     * @param filter BasicFunctionOperator
     * @return FilterOperator reversed BasicFunctionOperator
     * @throws QueryProcessorException
     */
    private FilterOperator reverseFilter(FilterOperator filter) throws RemoveNotException {
        int tokenInt = filter.getTokenIntType();
        if (filter.isLeaf()) {
            try {
                ((BasicFunctionOperator) filter).setReversedTokenIntType();
            } catch (BasicOperatorException e) {
                throw new RemoveNotException(
                        "convert BasicFuntion to reserved meet failed: previous token:" + tokenInt
                                + "tokenName:" + SQLConstant.tokenNames.get(tokenInt));
            }
            return filter;
        }
        switch (tokenInt) {
            case KW_AND:
            case KW_OR:
                List<FilterOperator> children = filter.getChildren();
                children.set(0, reverseFilter(children.get(0)));
                children.set(1, reverseFilter(children.get(1)));
                filter.setTokenIntType(SQLConstant.reverseWords.get(tokenInt));
                return filter;
            case KW_NOT:
                return removeNot(filter.getChildren().get(0));
            default:
                throw new RemoveNotException("Unknown token in reverseFilter: " + tokenInt + ","
                        + SQLConstant.tokenNames.get(tokenInt));
        }
    }

}
