package com.vaadin.data.util.filter;

import java.util.Collection;

import com.vaadin.data.Container.Filter;
import com.vaadin.data.Item;
import com.vaadin.data.Property;

public class In implements Filter {

	private final Object propertyId;
	private Collection<?> collection;
	
	public In(Object propertyId, Collection<?> collection) {
		assert propertyId != null;
		assert collection != null;
		this.propertyId = propertyId;
		this.collection = collection;
	}
	
	@Override
	public boolean passesFilter(Object itemId, Item item)
			throws UnsupportedOperationException {
		final Property<?> p = item.getItemProperty(getPropertyId());
        if (null == p) {
            return false;
        }
        Object value = p.getValue();
        return getCollection().contains(value);
	}

	@Override
	public boolean appliesToProperty(Object propertyId) {
		return getPropertyId().equals(propertyId);
	}

	public Collection<?> getCollection() {
		return collection;
	}

	public void setCollection(Collection<?> collection) {
		this.collection = collection;
	}

	public Object getPropertyId() {
		return propertyId;
	}

}
