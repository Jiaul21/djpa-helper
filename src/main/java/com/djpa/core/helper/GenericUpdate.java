package com.djpa.core.helper;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public interface GenericUpdate<E, ID> {

    E updateEntity(ID id, Consumer<E> updater);

    int updateField(ID id, String fieldName, Object value);

    int updateFields(Collection<ID> ids, Map<String, Object> fields);

    int updateField(String cName, Object cValue, String fieldName, Object value);

    int updateFieldForBulk(String fieldName, Object value, Collection<ID> ids);

    int updateFieldForBulk(String fieldName, List<? extends IdFieldValue> dtos);

    int updateBulkEntity(List<ID> ids, List<? extends IdFieldValue> fields);

    int updateFields(ID id, Map<String, Object> fields);

    Object convertValue(String fieldName, Object value);

    Map<Object, List<Long>> groupFields(List<? extends IdFieldValue> fields);

}
