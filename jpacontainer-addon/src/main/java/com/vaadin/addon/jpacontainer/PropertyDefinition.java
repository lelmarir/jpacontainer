package com.vaadin.addon.jpacontainer;

import com.vaadin.addon.jpacontainer.metadata.PropertyKind;
import com.vaadin.data.Property.ReadOnlyException;

public abstract class PropertyDefinition<E, T> {
	private boolean writable = false;

	public PropertyDefinition() {
		;
	}

	public abstract T getPropertyValue(E object);

	public abstract void setPropertyValue(E object, T propertyValue) throws ReadOnlyException;
	
	public abstract String getPropertyId();

	public boolean isWritable() {
		return writable;
	}

	public void setWriteable(boolean writable) throws UnsupportedOperationException {
		this.writable = writable;
	}

	public abstract boolean isPersistent();

	public abstract String getSortablePropertyId();

	public abstract Class<T> getType();

	public abstract PropertyKind getKind();

	public abstract EntityItemProperty<E, T> createProperty(JPAContainerItem<E> item);
}