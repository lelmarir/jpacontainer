/**
 * Copyright 2009-2013 Oy Vaadin Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.vaadin.addon.jpacontainer;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import com.vaadin.data.Container;
import com.vaadin.data.Container.ItemSetChangeEvent;
import com.vaadin.data.Property;
import com.vaadin.data.Property.ValueChangeListener;
import com.vaadin.data.Validator.InvalidValueException;

/**
 * {@link EntityItem}-implementation that is used by {@link JPAContainer}.
 * Should not be used directly by clients.
 * 
 * @author Petter Holmström (Vaadin Ltd)
 * @since 1.0
 */
public final class JPAContainerItem<T> implements EntityItem<T> {

    private static final long serialVersionUID = 3835181888110236341L;

    private T entity;
    private JPAContainer<T> container;
    private PropertyList<T> propertyList;
    private Map<Object, JPAContainerItemProperty<T>> propertyMap;
    private boolean modified = false;
    private boolean dirty = false;
    private boolean persistent = true;
    private boolean readThrough = true;
    private boolean writeThrough = true;
    private boolean deleted = false;
    private Object itemId;

    /**
     * Creates a new <code>JPAContainerItem</code>. This constructor assumes
     * that <code>entity</code> is persistent. The item ID is the entity
     * identifier.
     * 
     * @param container
     *            the container that holds the item (must not be null).
     * @param entity
     *            the entity for which the item should be created (must not be
     *            null).
     */
    JPAContainerItem(JPAContainer<T> container, T entity) {
		this(container, entity, container.getIdentifierPropertyValue(entity),
				true);
    }

    /**
     * Creates a new <code>JPAContainerItem</code>.
     * 
     * @param container
     *            the container that created the item (must not be null).
     * @param entity
     *            the entity for which the item should be created (must not be
     *            null).
     * @param itemId
     *            the item ID, or null if the item is not yet inside the
     *            container that created it.
     * @param persistent
     *            true if the entity is persistent, false otherwise. If
     *            <code>itemId</code> is null, this parameter will be ignored.
     */
    JPAContainerItem(JPAContainer<T> container, T entity, Object itemId,
            boolean persistent) {
        assert container != null : "container must not be null";
        assert entity != null : "entity must not be null";
        this.entity = entity;
        this.container = container;
		// the local propertyList will be inherited from container only if
		// needed
		this.propertyList = null;
		// TODO when the item will be persisted and added to the container we
		// must update the itemId
        this.itemId = itemId;
        if (itemId == null) {
            this.persistent = false;
        } else {
            this.persistent = persistent;
        }
        this.propertyMap = new HashMap<Object, JPAContainerItemProperty<T>>();
		// the itemRegistry will ignore this item if the id is null
        //FIXME: l'item viene registrato quando viene salvato (e gli viene assegnato un id)?
        container.registerItem(this);
    }

    @Override
    public Object getItemId() {
        return itemId;
    }

    @Override
    public boolean addItemProperty(Object id, @SuppressWarnings("rawtypes") Property property)
            throws UnsupportedOperationException {
        throw new UnsupportedOperationException();
    }

	/**
	 * 
	 * @return the local propertyList if present, or the container one.
	 */
	private PropertyList<T> getPropertyList() {
		if (this.propertyList == null) {
			// questo metodo chiamerebbe getPropertyList(false), quindi evito di
			// fare un if in piu
			return container.getPropertyList();
		} else {
			return getPropertyList(false);
		}
	}

	/**
	 * 
	 * @param localCopy
	 *            true if a local copy (inherited from container) must be
	 *            created.
	 * @return the local propertyList if present or localCopy is true, else the
	 *         container one.
	 */
	private PropertyList<T> getPropertyList(boolean localCopy) {

		if (this.propertyList == null) {
			if (localCopy == true) {
				this.propertyList = new PropertyList<T>(
						container.getPropertyList());
			} else {
				return container.getPropertyList();
			}
		}
		return this.propertyList;
	}

	@Override
    public void addNestedContainerProperty(String nestedProperty)
            throws UnsupportedOperationException {
		getPropertyList(true).addNestedProperty(nestedProperty);
    }

    @Override
    public EntityItemProperty getItemProperty(Object id) {
        assert id != null : "id must not be null";
        JPAContainerItemProperty<T> p = propertyMap.get(id);
        if (p == null) {
            if (!getItemPropertyIds().contains(id.toString())) {
                return null;
            }
            p = new JPAContainerItemProperty<T>(this, id.toString());
            propertyMap.put(id, p);
        }
        return p;
    }
    
    public Class<?> getItemPropertyType(String propertyName) {
		return getPropertyList().getPropertyType(propertyName);
    }
    
    public Object getItemPropertyValue(String propertyName) {
		return getPropertyList().getPropertyValue(entity, propertyName);
    }
    
    public boolean isItemPropertyWritable(String propertyName) {
		return getPropertyList().isPropertyWritable(propertyName);
    }
    
	public void setItemPropertyWriteable(String propertyName, boolean writeable) {
		getPropertyList().setPropertyWriteable(propertyName, writeable);
	}

	public void setItemPropertyValue(String propertyName, Object propertyValue)
			throws IllegalArgumentException, IllegalStateException {
		getPropertyList().setPropertyValue(entity, propertyName, propertyValue);
    	setDirty(true);
    }
    
    void containerItemPropertyModified(String propertyId) {
    	container.containerItemPropertyModified(this, propertyId);
    }
    
	void containerItemModified() {
		container.containerItemModified(this);
	}

    public boolean isItemPropertyLazyLoaded(String propertyName) {
		return getPropertyList().isPropertyLazyLoaded(propertyName);
    }

    @Override
    public Collection<String> getItemPropertyIds() {
        /*
         * Although the container may only contain a few properties, all
         * properties are available for items.
         */
		return getPropertyList().getAllAvailablePropertyNames();
    }

    @Override
    public boolean removeItemProperty(Object id)
            throws UnsupportedOperationException {
        assert id != null : "id must not be null";
        if (id.toString().indexOf('.') > -1) {
			return getPropertyList(true).removeProperty(id.toString());
        } else {
            return false;
        }
    }

    @Override
    public boolean isModified() {
        return modified;
    }
    
    public void setModified(boolean modified) {
    	this.modified = modified;
    }

    /**
     * Changes the <code>dirty</code> flag of this item.
     * 
     * @see #isDirty()
     * @param dirty
     *            true to mark the item as dirty, false to mark it as untouched.
     */
    void setDirty(boolean dirty) {
        this.dirty = dirty;
    }

    @Override
    public boolean isDirty() {
        return isPersistent() && dirty;
    }

    @Override
	public void markAsDirty() {
		if (isWriteThrough() == false) {
			throw new IllegalStateException(
					"You shoud not modify the entity directly if the item is not WriteThrough, "
							+ "thre could be unsaved property not yet reflected on the entity");
		}
		setDirty(true);
		containerItemModified();
	}

	@Override
    public boolean isPersistent() {
        return persistent;
    }

    /**
     * Changes the <code>persistent</code> flag of this item.
     * 
     * @see #isPersistent()
     * @param persistent
     *            true to mark the item as persistent, false to mark it as
     *            transient.
     */
    void setPersistent(boolean persistent) {
        this.persistent = persistent;
    }

    @Override
    public boolean isDeleted() {
        return isPersistent() && !getContainer().isBuffered() && deleted;
    }

    /**
     * Changes the <code>deleted</code> flag of this item.
     * 
     * @see #isDeleted()
     * @param deleted
     *            true to mark the item as deleted, false to mark it as
     *            undeleted.
     */
    void setDeleted(boolean deleted) {
        this.deleted = true;
    }

    @Override
    public EntityContainer<T> getContainer() {
        return container;
    }

    @Override
    public T getEntity() {
        return this.entity;
    }
    
    public void replaceEntity(T entity) {
    	this.entity = entity;
    }

    @Override
    public void commit() throws SourceException, InvalidValueException {
        if (!isWriteThrough()) {
            try {
                /*
                 * Commit all properties. The commit() operation will check if
                 * the property is read only and ignore it if that is the case.
                 */
                for (JPAContainerItemProperty<T> prop : propertyMap.values()) {
                    prop.commit();
                }
                modified = false;
                containerItemModified();
            } catch (Property.ReadOnlyException e) {
                throw new SourceException(this, e);
            }
        }
    }

    @Override
    public void discard() throws SourceException {
        if (!isWriteThrough()) {
            for (JPAContainerItemProperty<T> prop : propertyMap.values()) {
                prop.discard();
            }
            modified = false;
        }
    }

    public boolean isReadThrough() {
        return readThrough;
    }

    public boolean isWriteThrough() {
        return writeThrough;
    }

    public void setReadThrough(boolean readThrough) throws SourceException {
        if (this.readThrough != readThrough) {
            if (!readThrough && writeThrough) {
                throw new IllegalStateException(
                        "ReadThrough can only be turned off if WriteThrough is turned off");
            }
            this.readThrough = readThrough;
        }
    }

    public void setWriteThrough(boolean writeThrough) throws SourceException,
            InvalidValueException {
        if (this.writeThrough != writeThrough) {
            if (writeThrough) {
                /*
                 * According to the Buffered interface, commit must be executed
                 * if writeThrough is turned on.
                 */
                commit();
                /*
                 * Do some cleaning up
                 */
                for (JPAContainerItemProperty<T> prop : propertyMap.values()) {
                    prop.clearCache();
                }
            } else {
                /*
                 * We can iterate directly over the map, as this operation only
                 * affects existing properties. Properties that are lazily
                 * created afterwards will work automatically.
                 */
                for (JPAContainerItemProperty<T> prop : propertyMap.values()) {
                    prop.cacheRealValue();
                }
            }
            this.writeThrough = writeThrough;
        }
    }

    @Override
    public void addListener(ValueChangeListener listener) {
        /*
         * This operation affects ALL properties, so we have to iterate over the
         * list of ids instead of the map.
         */
        for (String propertyId : getItemPropertyIds()) {
            ((Property.ValueChangeNotifier) getItemProperty(propertyId))
                    .addValueChangeListener(listener);
        }
    }

    @Override
    public void removeListener(ValueChangeListener listener) {
        /*
         * This operation affects ALL properties, so we have to iterate over the
         * list of ids instead of the map.
         */
        for (String propertyId : getItemPropertyIds()) {
            ((Property.ValueChangeNotifier) getItemProperty(propertyId))
                    .removeValueChangeListener(listener);
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

    @Override
    public String toString() {
        return entity.toString();
    }

    @Override
    @SuppressWarnings("serial")
    public void refresh() {
        if (isPersistent()) {
            T refreshedEntity = getContainer().getEntityProvider()
                    .refreshEntity(getEntity());
            if (refreshedEntity == null) {
                /*
                 * Entity has been removed, fire item set change for the
                 * container
                 */
                setPersistent(false);
                container.fireContainerItemSetChange(new ItemSetChangeEvent() {
                    @Override
                    public Container getContainer() {
                        return container;
                    }
                });
                return;
            } else {
                replaceEntity(refreshedEntity);
            }
            if (isDirty() || isModified()) {
                discard();
            }
            setDirty(false);
            //FIXME we could fire only really changed properties
            Collection<String> itemPropertyIds = getItemPropertyIds();
            for (String string : itemPropertyIds) {
                getItemProperty(string).fireValueChangeEvent();
            }
        }
    }

    private void readObject(java.io.ObjectInputStream in) throws IOException,
            ClassNotFoundException {
        in.defaultReadObject();
        container.registerItem(this);
    }

    @Override
    public void setBuffered(boolean buffered) {
        setWriteThrough(!buffered);
        setReadThrough(!buffered);
    }

    @Override
    public boolean isBuffered() {
        return !isReadThrough() && !isWriteThrough();
    }

}
