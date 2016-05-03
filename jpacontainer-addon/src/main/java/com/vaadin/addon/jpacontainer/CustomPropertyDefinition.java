package com.vaadin.addon.jpacontainer;

import java.util.LinkedList;
import java.util.List;

import com.vaadin.addon.jpacontainer.metadata.PropertyKind;
import com.vaadin.data.Property;
import com.vaadin.data.Property.ReadOnlyException;

public abstract class CustomPropertyDefinition<E, T> extends PropertyDefinition<E, T> {

	public class CustomEntityItemProperty implements EntityItemProperty<E, T> {

		private final EntityItem<E> entityItem;
		private final Class<T> type;

		private List<ValueChangeListener> listeners;

		private boolean modified = false;

		public CustomEntityItemProperty(EntityItem<E> entityItem, Class<T> type) {
			assert entityItem != null;
			this.entityItem = entityItem;
			this.type = type;
		}

		@Override
		public T getValue() {
			return (T) CustomPropertyDefinition.this.getPropertyValue(getItem().getEntity());
		}

		@Override
		public void setValue(T newValue) throws ReadOnlyException {
			CustomPropertyDefinition.this.setPropertyValue(getItem().getEntity(), newValue);
		}

		@Override
		public Class getType() {
			return type;
		}

		@Override
		public boolean isReadOnly() {
			return !CustomPropertyDefinition.this.isWritable();
		}

		@Override
		public void setReadOnly(boolean newStatus) {
			CustomPropertyDefinition.this.setWriteable(!newStatus);
		}

		@Override
		public void addValueChangeListener(ValueChangeListener listener) {
			if (listeners == null) {
				listeners = new LinkedList<>();
			}
			listeners.add(listener);
		}

		@Override
		public void addListener(ValueChangeListener listener) {
			addValueChangeListener(listener);
		}

		@Override
		public void removeValueChangeListener(ValueChangeListener listener) {
			if (listeners != null) {
				listeners.remove(listener);
			}
		}

		@Override
		public void removeListener(ValueChangeListener listener) {
			removeValueChangeListener(listener);
		}

		@Override
		public EntityItem<E> getItem() {
			return entityItem;
		}

		@Override
		public String getPropertyId() {
			return CustomPropertyDefinition.this.getPropertyId();
		}

		@Override
		public void fireValueChangeEvent() {
			if (listeners != null) {
				ValueChangeEvent event = new ValueChangeEvent() {
					@Override
					public Property getProperty() {
						return getProperty();
					}
				};
				for (ValueChangeListener l : listeners) {
					l.valueChange(event);
				}
			}
		}

		@Override
		public boolean isModified() {
			return false;
		}

		@Override
		public void commit() {
			;
		}

		@Override
		public void discard() {
			;
		}

		@Override
		public void setWriteThrough(boolean writeThrough) {
			if (!this.isReadOnly()) {
				throw new UnsupportedOperationException();
			}
		}

	}

	private String propertyId;
	private Class<T> type;

	public CustomPropertyDefinition(String propertyId, Class<T> type) {
		this.propertyId = propertyId;
		this.type = type;
	}

	@Override
	public abstract T getPropertyValue(E object);

	@Override
	public void setPropertyValue(E object, T propertyValue) throws ReadOnlyException {
		throw new UnsupportedOperationException();
	}

	@Override
	public String getPropertyId() {
		return propertyId;
	}

	@Override
	public boolean isPersistent() {
		return false;
	}

	@Override
	public String getSortablePropertyId() {
		return null;
	}

	@Override
	public Class<T> getType() {
		return type;
	}

	@Override
	public PropertyKind getKind() {
		return PropertyKind.NONPERSISTENT;
	}

	@Override
	public EntityItemProperty<E, T> createProperty(JPAContainerItem<E> item) {
		return new CustomEntityItemProperty(item, type);
	}

}
