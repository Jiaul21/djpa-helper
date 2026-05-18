package com.djpa.core.helper;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Constructor;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GenericSelectionImpl<E, ID> implements GenericSelection<E, ID> {

    private final EntityManager entityManager;
    private final Class<E> entityClass;

    public GenericSelectionImpl(EntityManager entityManager, Class<E> entityClass) {
        this.entityManager = entityManager;
        this.entityClass = entityClass;
    }

    @Transactional(readOnly = true)
    public <P> Page<P> findAll(Class<P> projectionClass, Map<String, Object> filters, Pageable pageable) {

        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<P> cq = cb.createQuery(projectionClass);
        Root<E> root = cq.from(entityClass);
        Map<String, Join<?, ?>> joins = new HashMap<>();

        List<Predicate> predicates = buildPredicates(cb, root, joins, filters);

        cq.where(predicates.toArray(new Predicate[0]));
        applySorting(cb, cq, root, joins, pageable.getSort());

        cq.select(buildSelection(cb, cq, root, joins, projectionClass));

        TypedQuery<P> query = entityManager.createQuery(cq);
        query.setFirstResult((int) pageable.getOffset());
        query.setMaxResults(pageable.getPageSize());

        List<P> content = query.getResultList();

        long total = count(filters);
        return new PageImpl<>(content, pageable, total);
    }


    private List<Predicate> buildPredicates(CriteriaBuilder cb, Root<E> root, Map<String, Join<?, ?>> joins, Map<String, Object> filters) {
        List<Predicate> predicates = new ArrayList<>();
        if (filters == null) return predicates;

        for (Map.Entry<String, Object> entry : filters.entrySet()) {
            String field = entry.getKey();
            Object value = entry.getValue();

            Path<?> path = resolvePath(root, joins, field);

            if (value instanceof String s) {
                predicates.add(cb.like(cb.lower(path.as(String.class)), "%" + s.toLowerCase() + "%"));
            } else {
                predicates.add(cb.equal(path, value));
            }
        }
        return predicates;
    }


    private Path<?> resolvePath(Root<E> root, Map<String, Join<?, ?>> joins, String fieldPath) {
        String[] parts = fieldPath.split("\\.");

        Path<?> path = root;
        StringBuilder joinPath = new StringBuilder();

        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];

            if (i > 0) joinPath.append(".");
            joinPath.append(part);

            if (i < parts.length - 1) {
                if (!(path instanceof From<?, ?> from)) {
                    throw new IllegalArgumentException("Cannot join from non-entity attribute: " + joinPath);
                }
                String key = joinPath.toString();
                Join<?, ?> join = joins.computeIfAbsent(key, k -> from.join(part, JoinType.LEFT));
                path = join;
            } else {
                path = path.get(part);
            }
        }
        return path;
    }


    private void applySorting(CriteriaBuilder cb, CriteriaQuery<?> cq, Root<E> root, Map<String, Join<?, ?>> joins, Sort sort) {
        if (sort.isUnsorted()) return;

        List<Order> orders = new ArrayList<>();
        for (Sort.Order order : sort) {
            Path<?> path = resolvePath(root, joins, order.getProperty());
            orders.add(order.isAscending() ? cb.asc(path) : cb.desc(path));
        }
        cq.orderBy(orders);
    }

    private <P> Selection<P> buildSelection(CriteriaBuilder cb, CriteriaQuery<P> cq, Root<E> root, Map<String, Join<?, ?>> joins, Class<P> projectionClass) {
        Constructor<?> constructor = projectionClass.getConstructors()[0];
        List<Selection<?>> selections = new ArrayList<>();

        for (Parameter p : constructor.getParameters()) {
            selections.add(resolvePath(root, joins, p.getName()));
        }
        return cb.construct(projectionClass, selections.toArray(new Selection[0]));
    }

    private long count(Map<String, Object> filters) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Long> cq = cb.createQuery(Long.class);
        Root<E> root = cq.from(entityClass);

        Map<String, Join<?, ?>> joins = new HashMap<>();
        cq.select(cb.countDistinct(root));
        cq.where(buildPredicates(cb, root, joins, filters).toArray(new Predicate[0]));

        return entityManager.createQuery(cq).getSingleResult();
    }

}
//private Path<?> resolvePath(Root<E> root, Map<String, Join<?, ?>> joins, String fieldPath) {
//    String[] parts = fieldPath.split("\\.");
//
//    Path<?> path = root;
//    for (int i = 0; i < parts.length; i++) {
//        String part = parts[i];
//
//        if (i < parts.length - 1) {
//            joins.putIfAbsent(part, ((From<?, ?>) path).join(part, JoinType.LEFT));
//            path = joins.get(part);
//        } else {
//            path = path.get(part);
//        }
//    }
//    return path;
//}
