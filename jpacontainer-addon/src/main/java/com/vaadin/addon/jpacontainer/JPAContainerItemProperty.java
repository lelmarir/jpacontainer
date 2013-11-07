package com.vaadin.addon.jpacontainer;

import java.lang.reflect.Constructor;
import java.util.Collection;
import java.util.EventObject;
import java.util.LinkedList;
import java.util.List;

import com.vaadin.addon.jpacontainer.util.HibernateUtil;
import com.vaadin.data.Property;
import com.vaadin.data.util.converter.Converter.ConversionException;

/**
 * {@link Property}-implementation that is used by {@link EntityItem}.
 * Should not be used directly by clients.
 * 
 * @author Petter Holmström (Vaadin Ltd)
 * @since 1.0
 */
public class JPAContainerItemProperty<T> implements EntityItemProperty {

	private static boolean nullSafeEquals(Object o1, Object o2) {
        try {
            return o1 == o2 || o1.equals(o2);
        } catch (NullPointerException e) {
            return false;
        }
    }

    private static final long serialVersionUID = 2791934277775480650L;
    private JPAContainerItem<T> item;
    private String propertyId;
    private Object cachedValue;

    /**
     * Creates a new <code>ItemProperty</code>.
     * 
     * @param propertyId
     *            the property id of the new property (must not be null).
     */
    JPAContainerItemProperty(JPAContainerItem<T> item, String propertyId) {
        assert item != null : "item must not be null";
        assert propertyId != null : "propertyId must not be null";
        this.item = item;
        this.propertyId = propertyId;

        // Initialize cached value if necessary
        if (!item.isWriteThrough()) {
            cacheRealValue();
        }
    }

    @Override
	public String getPropertyId() {
        return propertyId;
    }

    /**
     * Like the name suggests, this method notifies the listeners if the
     * cached value and real value are different.
     */
    void notifyListenersIfCacheAndRealValueDiffer() {
        Object realValue = getRealValue();
        if (!nullSafeEquals(realValue, cachedValue)) {
            fireValueChangeEvent();
        }
    }

    /**
     * Caches the real value of the property.
     */
    void cacheRealValue() {
        Object realValue = getRealValue();
        cachedValue = realValue;
    }

    /**
     * Clears the cached value, without notifying any listeners.
     */
    void clearCache() {
        cachedValue = null;
    }

    /**
     * <b>Note! This method assumes that write through is OFF!</b>
     * <p>
     * Sets the real value to the cached value. If read through is on, the
     * listeners are also notified as the value will appear to have changed
     * to them.
     * <p>
     * If the property is read only, nothing happens.
     * 
     * @throws ConversionException
     *             if the real value could not be set for some reason.
     */
    void commit() throws ConversionException {
        if (!isReadOnly()) {
            try {
                setRealValue(cachedValue);
            } catch (Exception e) {
                throw new ConversionException(e);
            }
        }
    }

    /**
     * <b>Note! This method assumes that write through is OFF!</b>
     * <p>
     * Replaces the cached value with the real value. If read through is
     * off, the listeners are also notified as the value will appera to have
     * changed to them.
     */
    void discard() {
        Object realValue = getRealValue();
        if (!nullSafeEquals(realValue, cachedValue)) {
            cacheRealValue();
            fireValueChangeEvent();
        } else {
            cacheRealValue();
        }
    }

    @Override
	public EntityItem<?> getItem() {
        return item;
    }

    @Override
	public Class<?> getType() {
        return item.getItemPropertyType(propertyId);
    }

    @Override
	public Object getValue() {
        if (item.isReadThrough() && item.isWriteThrough()) {
            return getRealValue();
        } else {
            return cachedValue;
        }
    }

    /**
     * Gets the real value from the backend entity.
     * 
     * @return the real value.
     */
    private Object getRealValue() {
        ensurePropertyLoaded(propertyId);
        return item.getItemPropertyValue(propertyId);
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
     * Sets the real value of the property to <code>newValue</code>. The
     * value is expected to be of the correct type at this point (i.e. any
     * conversions from a String should have been done already). As this
     * method updates the backend entity object, it also turns on the
     * <code>dirty</code> flag of the item.
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
            Object value = item
                    .getItemPropertyValue(propertyId);
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
            item.replaceEntity(lazyLoadingDelegate.ensureLazyPropertyLoaded(item.getEntity(),
                    propertyId));
        }
    }

    @Override
	public void setValue(Object newValue) throws ReadOnlyException,
            ConversionException {
        if (isReadOnly()) {
            throw new ReadOnlyException();
        }

        if (newValue != null
                && !getType().isAssignableFrom(newValue.getClass())) {
            /*
             * The type we try to set is incompatible with the type of the
             * property. We therefore try to convert the value to a string
             * and see if there is a constructor that takes a single string
             * argument. If this fails, we throw an exception.
             */
            try {
                // Gets the string constructor
                final Constructor<?> constr = getType().getConstructor(
                        new Class[] { String.class });

                newValue = constr.newInstance(new Object[] { newValue
                        .toString() });
            } catch (Exception e) {
                throw new ConversionException(e);
            }
        }
        try {
            if (item.isWriteThrough()) {
                //FIXME: setRealValue e containerItemPropertyModified modificano 2 entity diverse
                setRealValue(newValue);
                item.containerItemPropertyModified(propertyId);
            } else {
                cachedValue = newValue;
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

        private ValueChangeEvent(JPAContainerItemProperty<T> source) {
            super(source);
        }

        @Override
		public Property<?> getProperty() {
            return (Property<?>) getSource();
        }
    }

    /**
     * Notifies all the listeners that the value of the property has
     * changed.
     */
    @Override
	public void fireValueChangeEvent() {
        if (listeners != null) {
            final Object[] l = listeners.toArray();
            final Property.ValueChangeEvent event = new ValueChangeEvent(
                    this);
            for (int i = 0; i < l.length; i++) {
                ((Property.ValueChangeListener) l[i]).valueChange(event);
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
}
