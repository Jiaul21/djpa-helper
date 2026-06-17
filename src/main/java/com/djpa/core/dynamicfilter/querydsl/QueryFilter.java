package com.djpa.core.dynamicfilter.querydsl;

import com.djpa.core.dynamicfilter.dto.FilterRequest;
import com.djpa.core.dynamicfilter.dto.Operator;
import com.djpa.core.dynamicfilter.querydsl.dto.QueryFilterInfo;
import com.djpa.core.util.Converter;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.Ops;
import com.querydsl.core.types.Order;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.Predicate;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.core.types.dsl.SimpleExpression;
import com.querydsl.core.types.dsl.StringExpression;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.querydsl.core.types.Ops.BETWEEN;

public class QueryFilter {

    public static QueryFilterInfo process(List<FilterRequest> filterRequests, Map<String, SimpleExpression<?>> map) {
        BooleanBuilder condition = new BooleanBuilder();
        List<OrderSpecifier<?>> orders = new ArrayList<>();

        for (FilterRequest f : filterRequests) {
            if (map.containsKey(f.field())) {
                SimpleExpression<?> simpleExpression = map.get(f.field());

                if (f.values() != null && !f.values().isEmpty())
                    condition.and(buildPredicate(f.operator(), f.values(), simpleExpression));

                if (f.sort() != null && !f.sort().isEmpty()) {
                    Order order = "DESC".equalsIgnoreCase(f.sort()) ? Order.DESC : Order.ASC;
                    OrderSpecifier<?> orderSpecifier = orderSpecifier(order, simpleExpression);
                    orders.add(orderSpecifier);
                }
            }
        }
        return new QueryFilterInfo(condition, orders.toArray(new OrderSpecifier[0]));
    }

    private static Predicate buildPredicate(Operator operator, List<String> values, SimpleExpression<?> path) {

        return switch (operator) {
            case EQUAL -> eq(values.get(0), path);
            case NOT_EQUAL -> neq(values.get(0), path);
            case CONTAINS -> contains(values.get(0), path);
            case NOT_CONTAINS -> notContains(values.get(0), path);
            case STARTS_WITH -> startsWith(values.get(0), path);
            case ENDS_WITH -> endsWith(values.get(0), path);
            case GREATER_THAN -> gt(values.get(0), path);
            case GREATER_THAN_EQUAL -> gte(values.get(0), path);
            case LESS_THAN -> lt(values.get(0), path);
            case LESS_THAN_EQUAL -> lte(values.get(0), path);
            case BETWEEN -> between(values.get(0), values.get(1), path);
            case NOT_BETWEEN -> notBetween(values.get(0), values.get(1), path);
            case IN -> in(values, path);
            case NOT_IN -> notIn(values, path);
            default -> null;
        };
    }


    @SuppressWarnings({"rawtypes", "unchecked"})
    private static OrderSpecifier<?> orderSpecifier(Order order, SimpleExpression<?> path) {
        return new OrderSpecifier(order, path);
    }

    private static Predicate eq(String value, SimpleExpression<?> path) {
        return Expressions.predicate(Ops.EQ, path, Expressions.constant(Converter.convert(value, path.getType())));
    }

    private static Predicate neq(String v, SimpleExpression<?> path) {
        return eq(v, path).not();
    }

    private static Predicate contains(String value, SimpleExpression<?> path) {
        return asStringExpression(path).containsIgnoreCase(value);
    }

    private static Predicate notContains(String value, SimpleExpression<?> path) {
        return path.isNull().or(contains(value,path).not());
    }

    private static Predicate startsWith(String value, SimpleExpression<?> path) {
        return asStringExpression(path).startsWithIgnoreCase(value);
    }

    private static Predicate endsWith(String value, SimpleExpression<?> path) {
        return asStringExpression(path).endsWithIgnoreCase(value);
    }

    private static Predicate between(String val1, String val2, SimpleExpression<?> path) {
        return Expressions.predicate(
                BETWEEN, path,
                Expressions.constant(Converter.convert(val1, path.getType())),
                Expressions.constant(Converter.convert(val2, path.getType())));
    }

    private static Predicate notBetween(String val1, String val2, SimpleExpression<?> path) {
        return path.isNull().or(between(val1,val2,path).not());
    }

    private static Predicate in(List<String> values, SimpleExpression<?> path) {
        return inExpression(path, values.stream().map(value -> Converter.convert(value, path.getType())).toList());
    }

    private static Predicate notIn(List<String> values, SimpleExpression<?> path) {
        return path.isNull().or(in(values,path).not());
    }

    private static Predicate gt(String v, SimpleExpression<?> path) {
        return compare(Ops.GT, v, path);
    }

    private static Predicate gte(String v, SimpleExpression<?> path) {
        return compare(Ops.GOE, v, path);
    }

    private static Predicate lt(String v, SimpleExpression<?> path) {
        return compare(Ops.LT, v, path);
    }

    private static Predicate lte(String v, SimpleExpression<?> path) {
        return compare(Ops.LOE, v, path);
    }

    private static Predicate compare(Ops operator, String value, SimpleExpression<?> path) {
        return Expressions.predicate(operator, path, Expressions.constant(Converter.convert(value, path.getType())));
    }


    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Predicate inExpression(SimpleExpression<?> path, List<?> values) {
        return ((SimpleExpression) path).in(values);
    }

    private static StringExpression asStringExpression(SimpleExpression<?> path) {
        if (path instanceof StringExpression stringExpression) {
            return stringExpression;
        }
        return Expressions.stringTemplate("str({0})", path);
    }

}
