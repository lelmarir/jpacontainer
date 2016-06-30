package com.vaadin.addon.jpacontainer;

import java.lang.reflect.Constructor;
import java.util.Collection;
import java.util.EventObject;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

import com.vaadin.addon.jpacontainer.util.HibernateUtil;
import com.vaadin.data.Property;
import com.vaadin.data.Property.Transactional;
import com.vaadin.data.util.converter.Converter.ConversionException;

/**
 * {@link Property}-implementation that is used by {@link EntityItem}. Should
 * not be used directly by clients.
 * 
 * @author Petter Holmström (Vaadin Ltd)
 * @since 1.0
 */
public class JPAContainerItemProperty<E, T> implements EntityItemProperty<E, T>,
		Transactional<T> {

	private static final long serialVersionUID = 2791934277775480650L;
	private JPAContainerItem<E> item;
	private String propertyId;
	private T cachedValue;
	private boolean modified;

	private boolean inTransaction = false;
	private T valueBeforeTransaction;
	private boolean modifiedBeforeTransaction;
	private boolean valueChangePending = false;

	/**
	 * Creates a new <code>ItemProperty</code>.
	 * 
	 * @param propertyId
	 *            the property id of the new property (must not be null).
	 */
	JPAContainerItemProperty(JPAContainerItem<E> item, String propertyId) {
		assert item != null : "item must not be null";
		assert propertyId != null : "propertyId must not be null";
		this.item = item;
		this.propertyId = propertyId;

		// Initialize cached value if necessary
		if (!item.isReadThrough()) {
			cacheRealValue();
		}
	}

	@Override
	public String getPropertyId() {
		return propertyId;
	}

	/**
	 * Like the name suggests, this method notifies the listeners if the cached
	 * value and real value are different.
	 */
	void notifyListenersIfCacheAndRealValueDiffer() {
		Object realValue = getRealValue();
		if (!Objects.equals(realValue, cachedValue)) {
			fireValueChangeEvent();
		}
	}

	@Override
	public void setWriteThrough(boolean writeThrough) {
		if(writeThrough) {
			clearCache();
		}else{
			cacheRealValue();
		}
	}
	
	/**
	 * Caches the real value of the property.
	 */
	private void cacheRealValue() {
		T realValue = getRealValue();
		cachedValue = realValue;
	}
	
	/**
	 * Clears the cached value, without notifying any listeners.
	 */
	private void clearCache() {
		cachedValue = null;
	}

	@Override
	public void commit() throws ConversionException {
		if (inTransaction) {
			endTransaction();
		} else {
			assert item.isWriteThrough() == false;
			if (!isReadOnly()) {
				try {
					if (this.modified) {
						setRealValue(cachedValue);
						this.modified = false;
					}
				} catch (Exception e) {
					throw new ConversionException(e);
				}
			}
		}

	}

	@Override
	public void discard() {
		Object realValue = getRealValue();

		if (!item.isWriteThrough()) {
			if (!item.isReadThrough()) {
				// FIXME should not read the real value but return to the value
				// cached when set readTrough OFF, but this is not feasible with
				// only a cachedValue, we'd need a readTroughCachedValue and
				// writeTroughCachedValue
				cacheRealValue();
			} else {
				clearCache();
			}
			this.modified = false;
		} else {
			if (!item.isReadThrough()) {
				cacheRealValue();
			} else {
				throw new IllegalStateException(
						"Should not (is useless) call discard() if at least readTrough or writeTrough is OFF");
			}
		}

		if (!Objects.equals(realValue, cachedValue)) {
			fireValueChangeEvent();
		}
	}

	@Override
	public EntityItem<E> getItem() {
		return item;
	}

	@Override
	public Class<T> getType() {
		return (Class<T>) item.getItemPropertyType(propertyId);
	}

	@Override
	public T getValue() {
		if (item.isReadThrough()) {
			if (!item.isWriteThrough() && this.modified) {
				return cachedValue;
			} else {
				return getRealValue();
			}
		} else {
			return cachedValue;
		}
	}

	/**
	 * Gets the real value from the backend entity.
	 * 
	 * @return the real value.
	 */
	@SuppressWarnings("unchecked")
	private T getRealValue() {
		ensurePropertyLoaded(propertyId);
		return (T) item.getItemPropertyValue(propertyId);
	}

	@Override
	public String toString() {
		final Object value = getValue();
		if (value == null) {
			return null;
		}
		return value.toString();
	}

	@Override
	public boolean isReadOnly() {
		return !item.isItemPropertyWritable(propertyId);
	}

	/**
	 * <strong>This functionality is not supported by this
	 * implementation.</strong>
	 * <p>
	 * {@inheritDoc }
	 */
	@Override
	public void setReadOnly(boolean newStatus) {
		item.setItemPropertyWriteable(propertyId, !newStatus);
	}

	/**
	 * Sets the real value of the property to <code>newValue</code>. The value
	 * is expected to be of the correct type at this point (i.e. any conversions
	 * from a String should have been done already). As this method updates the
	 * backend entity object, it also turns on the <code>dirty</code> flag of
	 * the item.
	 * 
	 * @see JPAContainerItem#isDirty()
	 * @param newValue
	 *            the new value to set.
	 */
	private void setRealValue(Object newValue) {
		ensurePropertyLoaded(propertyId);
		item.setItemPropertyValue(propertyId, newValue);
	}

	/**
	 * Ensures that any lazy loaded properties are available.
	 * 
	 * @param propertyId
	 *            the id of the property to check.
	 */
	private void ensurePropertyLoaded(String propertyId) {
		LazyLoadingDelegate lazyLoadingDelegate = item.getContainer()
				.getEntityProvider().getLazyLoadingDelegate();
		if (lazyLoadingDelegate == null
				|| !item.isItemPropertyLazyLoaded(propertyId)) {
			// Don't need to do anything
			return;
		}
		boolean shouldLoadEntity = false;
		try {
			Object value = item.getItemPropertyValue(propertyId);
			if (value != null) {
				shouldLoadEntity = HibernateUtil
						.isUninitializedAndUnattachedProxy(value);
				if (Collection.class.isAssignableFrom(item
						.getItemPropertyType(propertyId))) {
					((Collection<?>) value).iterator().hasNext();
				}
			}
		} catch (IllegalArgumentException e) {
			shouldLoadEntity = true;
		} catch (RuntimeException e) {
			if (HibernateUtil.isLazyInitializationException(e)) {
				shouldLoadEntity = true;
			} else {
				throw e;
			}
		}
		if (shouldLoadEntity) {
			item.updateEntity(lazyLoadingDelegate.ensureLazyPropertyLoaded(
					item.getEntity(), propertyId));
		}
	}

	@SuppressWarnings("unchecked")
	@Override
	public void setValue(T newValue) throws ReadOnlyException,
			ConversionException {
		if (isReadOnly()) {
			throw new ReadOnlyException("The property is in read-only state");
		}

		if (newValue != null
				&& !getType().isAssignableFrom(newValue.getClass())) {
			/*
			 * The type we try to set is incompatible with the type of the
			 * property. We therefore try to convert the value to a string and
			 * see if there is a constructor that takes a single string
			 * argument. If this fails, we throw an exception.
			 */
			try {
				// Gets the string constructor
				final Constructor<?> constr = getType().getConstructor(
						new Class[] { String.class });

				newValue = (T) constr.newInstance(new Object[] { newValue
						.toString() });
			} catch (Exception e) {
				throw new ConversionException(e);
			}
		}
		try {
			if (item.isWriteThrough()) {
				// FIXME: setRealValue e containerItemPropertyModified
				// modificano 2 entity diverse
				setRealValue(newValue);
				item.containerItemPropertyModified(propertyId);
			} else {
				cachedValue = newValue;
				this.modified = true;
				item.setModified(true);
			}
		} catch (Exception e) {
			throw new ConversionException(e);
		}
		fireValueChangeEvent();
	}

	private List<ValueChangeListener> listeners;

	class ValueChangeEvent extends EventObject implements
			Property.ValueChangeEvent {

		private static final long serialVersionUID = 4999596001491426923L;

		private ValueChangeEvent(JPAContainerItemProperty<E, T> source) {
			super(source);
		}

		@Override
		public Property<?> getProperty() {
			return (Property<?>) getSource();
		}
	}

	/**
	 * Notifies all the listeners that the value of the property has changed.
	 */
	@Override
	public void fireValueChangeEvent() {
		if (inTransaction) {
			valueChangePending = true;
		} else {
			if (listeners != null) {
				final Object[] l = listeners.toArray();
				final Property.ValueChangeEvent event = new ValueChangeEvent(
						this);
				for (int i = 0; i < l.length; i++) {
					((Property.ValueChangeListener) l[i]).valueChange(event);
				}
			}
		}
	}

	@Override
	public void addListener(ValueChangeListener listener) {
		assert listener != null : "listener must not be null";
		if (listeners == null) {
			listeners = new LinkedList<ValueChangeListener>();
		}
		listeners.add(listener);
	}

	@Override
	public void removeListener(ValueChangeListener listener) {
		assert listener != null : "listener must not be null";
		if (listeners != null) {
			listeners.remove(listener);
			if (listeners.isEmpty()) {
				listeners = null;
			}
		}
	}

	@Override
	public void addValueChangeListener(ValueChangeListener listener) {
		addListener(listener);
	}

	@Override
	public void removeValueChangeListener(ValueChangeListener listener) {
		removeListener(listener);
	}

	/** {@inheritDoc}
	 * If the property is in read-only state the transaction has no sense. In this case the transaction is not started.
	 * <p>
	 * */
	@Override
	public void startTransaction() {
		if (!isReadOnly()) {
			inTransaction = true;
			valueBeforeTransaction = cachedValue;
			modifiedBeforeTransaction = modified;
		}
	}

	@Override
	public void rollback() {
		if(inTransaction) {
			cachedValue = valueBeforeTransaction;
			modified = modifiedBeforeTransaction;
			// the rollback() method recover the old value and if during the
			// transaction the property value was changed by another this event is
			// not fired
			valueChangePending = false;
			item.containerItemPropertyRollbacked(this);
			endTransaction();
		}
	}

	private void endTransaction() {
		assert inTransaction == true;
		inTransaction = false;
		valueBeforeTransaction = null;
		if (valueChangePending) {
			fireValueChangeEvent();
		}
	}
	
	@Override
	public boolean isModified() {
		return modified;
	}
}