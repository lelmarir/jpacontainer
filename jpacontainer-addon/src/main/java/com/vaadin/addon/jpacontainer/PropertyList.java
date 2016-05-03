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

import java.io.Serializable;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.persistence.Basic;
import javax.persistence.ElementCollection;
import javax.persistence.FetchType;
import javax.persistence.ManyToMany;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.OneToOne;

import com.vaadin.addon.jpacontainer.metadata.ClassMetadata;
import com.vaadin.addon.jpacontainer.metadata.PersistentPropertyMetadata;
import com.vaadin.addon.jpacontainer.metadata.PropertyKind;
import com.vaadin.addon.jpacontainer.metadata.PropertyMetadata;
import com.vaadin.data.util.NestedPropertyDescriptor;

/**
 * Helper class to make it easier to work with nested properties. Intended to be
 * used by {@link JPAContainer}. This class is not part of the public API and
 * hence should not be used directly by client applications.
 * <p>
 * Property lists can be chained. A child property list will always include all
 * the properties of its parent in addition to its own. A child list cannot be
 * used to add or remove properties to/from its parent.
 * 
 * @author Petter Holmström (Vaadin Ltd)
 * @since 1.0
 */
final class PropertyList<E> implements Serializable {

	public class MetadataPropertyDefinition<T> extends NestedPersistentProprtyDefinition<T> {

		public MetadataPropertyDefinition(PropertyMetadata pm) {
			super(null, pm);
		}

	}

	public abstract class NestedPropertyDefinition<T> extends PropertyDefinition<E, T> {

		private final PropertyDefinition<E, ?> parent;

		protected NestedPropertyDefinition(PropertyDefinition<E, ?> parent) {
			this.parent = parent;
		}

		protected PropertyDefinition<E, ?> getParent() {
			return parent;
		}

		@Override
		public String getSortablePropertyId() {
			if (isPersistent()) {
				return getPropertyId();
			} else {
				return null;
			}
		}

		@Override
		public T getPropertyValue(E object) {
			assert object != null : "entity must not be null";
			return (T) PropertyList.this.metadata.getPropertyValue(object, getPropertyId());
		}

		@Override
		public void setPropertyValue(E object, T propertyValue) {
			assert object != null : "entity must not be null";
			PropertyList.this.metadata.setPropertyValue(object, getPropertyId(), propertyValue);
		}

		@Override
		public EntityItemProperty<E, T> createProperty(JPAContainerItem<E> item) {
			return new JPAContainerItemProperty<E, T>(item, getPropertyId());
		}
	}

	public class NestedPersistentProprtyDefinition<T> extends NestedPropertyDefinition<T> {

		private PropertyMetadata pm;

		public NestedPersistentProprtyDefinition(PropertyDefinition<E, ?> parent, PropertyMetadata pm) {
			super(parent);
			this.pm = pm;
			setWriteable(pm.isWritable());
		}

		public String getPropertyId() {
			if (getParent() == null) {
				return pm.getName();
			} else {
				return getParent().getPropertyId() + "." + pm.getName();
			}
		}

		ClassMetadata<?> getTypeMetadata() {
			if (pm instanceof PersistentPropertyMetadata) {
				return ((PersistentPropertyMetadata) pm).getTypeMetadata();
			}
			return null;
		}

		@Override
		public boolean isWritable() {
			return super.isWritable() && pm.isWritable();
		}

		@Override
		public void setWriteable(boolean writable) throws UnsupportedOperationException {
			if (pm.isWritable() == false) {
				if (writable == true) {
					throw new UnsupportedOperationException("Can't set as writeable");
				}
			}
			super.setWriteable(writable);
		}

		@Override
		public boolean isPersistent() {
			return true;
		}

		@Override
		public Class<T> getType() {
			return (Class<T>) pm.getType();
		}

		@Override
		public PropertyKind getKind() {
			return pm.getPropertyKind();
		}
	}

	public class NestedTransientProprtyDefinition<T> extends NestedPropertyDefinition<T> {

		private final Method propertyGetterMethod;
		private final Method propertySetterMethod;

		public NestedTransientProprtyDefinition(PropertyDefinition<E, ?> parent, Method propertyGetterMethod) {
			super(parent);
			this.propertyGetterMethod = propertyGetterMethod;
			/*
			 * There are cases when this may not work. For example, if the
			 * setter is declared in a subclass.
			 */
			propertySetterMethod = getSetterMethod();
			setWriteable(propertySetterMethod != null);
		}

		private Method getSetterMethod() {
			try {
				return propertyGetterMethod.getDeclaringClass()
						.getMethod("s" + propertyGetterMethod.getName().substring(1), getType());
			} catch (NoSuchMethodException e) {
				return null;
			}
		}

		private String getName() {
			return propertyGetterMethod.getName().substring(3).toLowerCase();
		}

		@Override
		public String getPropertyId() {
			if (getParent() == null) {
				return getName();
			} else {
				return getParent().getPropertyId() + "." + getName();
			}
		}

		@Override
		public boolean isWritable() {
			return super.isWritable() && propertySetterMethod != null;
		}

		@Override
		public void setWriteable(boolean writable) throws UnsupportedOperationException {
			if (propertySetterMethod == null)
				if (writable == true) {
					throw new UnsupportedOperationException("Can't set as writeable without a setter");
				}
			super.setWriteable(writable);
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
			return (Class<T>) propertyGetterMethod.getReturnType();
		}

		@Override
		public PropertyKind getKind() {
			return PropertyKind.NONPERSISTENT;
		}

	}

	private static final long serialVersionUID = 372287057799712177L;
	private ClassMetadata<E> metadata;
	private Map<String, PropertyDefinition<E, Object>> properties = new HashMap<>();
	// private Set<String> propertyNames = new HashSet<String>();
	private Set<String> persistentPropertyNames = new HashSet<String>();
	// map from property name to the name of the property to be used to sort by
	// that property (in a format usable in JPQL - e.g. address.street)
	private Map<String, String> sortablePropertyMap = new HashMap<String, String>();
	private Set<String> nestedPropertyNames = new HashSet<String>();
	private Set<String> allPropertyNames = new HashSet<String>();

	private PropertyList<E> parentList;

	/**
	 * Creates a new <code>PropertyList</code> for the specified metadata.
	 * Initially, all the properties of <code>metadata</code> will be added to
	 * the list.
	 * 
	 * @param metadata
	 *            the class metadata (must not be null).
	 */
	public PropertyList(ClassMetadata<E> metadata) {
		assert metadata != null : "metadata must not be null";
		this.metadata = metadata;

		for (PropertyMetadata pm : metadata.getProperties()) {
			PropertyList<E>.MetadataPropertyDefinition<Object> pd = new MetadataPropertyDefinition(pm);
			addProperty(pd);
		}
	}

	public void addProperty(PropertyDefinition<E, Object> definition) {
		String id = definition.getPropertyId();
		if (properties.containsKey(id)) {
			throw new IllegalStateException("The PropertyList already contains a property with the same name: " + id);
		}
		properties.put(id, definition);
		allPropertyNames.add(id);
		if (definition.isPersistent()) {
			persistentPropertyNames.add(id);
			String sortPropertyId = definition.getSortablePropertyId();
			if (sortPropertyId != null) {
				sortablePropertyMap.put(id, sortPropertyId);
			}
		}
		if (definition instanceof PropertyList.NestedPropertyDefinition) {
			nestedPropertyNames.add(id);
		}
	}

	/**
	 * Creates a new <code>PropertyList</code> with the specified parent list.
	 * Initially, all the properties of the parent list will be available.
	 * 
	 * @param parentList
	 *            the parent list (must not be null).
	 */
	public PropertyList(PropertyList<E> parentList) {
		assert parentList != null : "parentList must not be null";
		this.parentList = parentList;
		this.metadata = parentList.getClassMetadata();
	}

	/**
	 * Gets the metadata for the class from which the properties should be
	 * fetched.
	 * 
	 * @return the class metadata (never null).
	 */
	public ClassMetadata<E> getClassMetadata() {
		return metadata;
	}

	/**
	 * Gets the parent property list, if any.
	 * 
	 * @return the parent list, or null if the list has no parent.
	 */
	public PropertyList<E> getParentList() {
		return parentList;
	}

	/**
	 * Configures a property to be sortable based on another property, typically
	 * a sub-property.
	 * <p>
	 * For example, let's say there is a property named <code>address</code> and
	 * that this property's type in turn has the property <code>street</code>.
	 * Addresses are not directly sortable as they are not simple properties.
	 * <p>
	 * If we want to be able to sort addresses based on the street property, we
	 * can set the sort property for <code>address</code> to be
	 * <code>address.street</code> using this method. Sort properties must be
	 * persistent and usable in JPQL, but need not be registered as separate
	 * properties in the PropertyList.
	 * <p>
	 * Note that the sort property is not checked when this method is called. If
	 * it is not a valid sort property, an exception will be thrown when trying
	 * to sort a container.
	 * 
	 * @param propertyName
	 *            the property for which sorting is to be customized (must not
	 *            be null).
	 * @param sortPropertyName
	 *            the property based on which sorting should be performed - this
	 *            need not be a separate property in the container but needs to
	 *            be usable in JPQL
	 * @throws IllegalArgumentException
	 *             if <code>propertyName</code> does not refer to a persistent
	 *             property.
	 * @since 1.2.1
	 */
	public void setSortProperty(String propertyName, String sortPropertyName) throws IllegalArgumentException {
		if (persistentPropertyNames.contains(propertyName)) {
			sortablePropertyMap.put(propertyName, sortPropertyName);
		} else {
			throw new IllegalArgumentException("Property " + propertyName + " cannot be sorted based on "
					+ sortPropertyName + ": not a persistent property");
		}
	}

	/**
	 * Adds the nested property <code>propertyName</code> to the set of
	 * properties. An asterisk can be used as a wildcard to indicate all
	 * leaf-properties.
	 * <p>
	 * For example, let's say there is a property named <code>address</code> and
	 * that this property's type in turn has the properties <code>street</code>,
	 * <code>postalCode</code> and <code>city</code>.
	 * <p>
	 * If we want to be able to access the street property directly, we can add
	 * the nested property <code>address.street</code> using this method. The
	 * method will figure out whether the nested property is persistent (can be
	 * used in queries) or transient (can only be used to display data).
	 * <p>
	 * However, if we want to add all the address properties, we can also use
	 * <code>address.*</code>. This will cause the nested properties
	 * <code>address.street</code>, <code>address.postalCode</code> and
	 * <code>address.city</code> to be added to the set of properties.
	 * 
	 * @param propertyName
	 *            the nested property to add (must not be null).
	 * @throws IllegalArgumentException
	 *             if <code>propertyName</code> was invalid.
	 */
	public void addNestedProperty(String propertyName) throws IllegalArgumentException {
		addProperty(propertyName);
	}

	public void addProperty(String propertyName) throws IllegalArgumentException {
		assert propertyName != null : "propertyName must not be null";

		if (!isNestedProperty(propertyName)) {
			throw new IllegalArgumentException(propertyName + " is not nested");
		}

		if (getAllAvailablePropertyNames().contains(propertyName)) {
			return; // Do nothing, the property already exists.
		}

		if (propertyName.endsWith("*")) {
			// We add a whole bunch of properties
			String parentPropertyName = propertyName.substring(0, propertyName.length() - 2);
			PropertyDefinition<E, ?> parentProperty = getOrCreateProperty(parentPropertyName);
			if (parentProperty instanceof PropertyList.NestedPersistentProprtyDefinition) {
				// The parent property is persistent and contains metadata
				ClassMetadata<?> parentTypeMetadata = ((PropertyList<E>.NestedPersistentProprtyDefinition<?>) parentProperty)
						.getTypeMetadata();
				for (PropertyMetadata pm : parentTypeMetadata.getProperties()) {
					NestedPersistentProprtyDefinition definition = new NestedPersistentProprtyDefinition(parentProperty,
							pm);
					addProperty(definition);
				}
			} else if (parentProperty instanceof PropertyList.NestedTransientProprtyDefinition) {
				// The parent property is transient or is a simple property that
				// does not contain any nestable properties
				Class<?> parentClass = ((PropertyList<E>.NestedTransientProprtyDefinition<?>) parentProperty).getType();
				for (Method m : parentClass.getMethods()) {
					if (m.getName().startsWith("get") && m.getName().length() > 3
							&& !Modifier.isStatic(m.getModifiers()) && m.getReturnType() != Void.TYPE
							&& m.getParameterTypes().length == 0 && m.getDeclaringClass() != Object.class) {

						NestedTransientProprtyDefinition definition = new NestedTransientProprtyDefinition(
								parentProperty, m);
						addProperty(definition);
					}
				}
			}
		} else {
			// We add a single property
			PropertyDefinition<E, Object> np = getOrCreateProperty(propertyName);
			addProperty(np);
		}
	}

	public PropertyDefinition<E, Object> getProperty(String propertyName) {
		return getProperty(propertyName, false);
	}

	private PropertyDefinition<E, Object> getOrCreateProperty(String propertyName) {
		return getProperty(propertyName, true);
	}

	private PropertyDefinition<E, Object> getProperty(String propertyName, boolean createIfNotExists)
			throws IllegalArgumentException {
		if (properties.containsKey(propertyName)) {
			return properties.get(propertyName);
		} else if (createIfNotExists) {
			try {
				if (isNestedProperty(propertyName)) {
					// Try with the parent
					int offset = propertyName.lastIndexOf('.');
					String parentName = propertyName.substring(0, offset);
					String name = propertyName.substring(offset + 1);
					PropertyDefinition<E, ?> parentProperty = getProperty(parentName, true);
					NestedPropertyDefinition property;
					if (parentProperty instanceof PropertyList.NestedPersistentProprtyDefinition) {
						ClassMetadata<?> parentClassMetadata = ((PropertyList<E>.NestedPersistentProprtyDefinition<?>) parentProperty)
								.getTypeMetadata();
						PropertyMetadata pm = parentClassMetadata.getProperty(name);
						if (pm == null) {
							throw new IllegalArgumentException("Invalid property name");
						} else {
							property = new NestedPersistentProprtyDefinition(parentProperty, pm);
						}
					} else if (parentProperty instanceof PropertyList.NestedTransientProprtyDefinition) {
						Class<?> parentType = ((PropertyList<E>.NestedTransientProprtyDefinition<?>) parentProperty)
								.getType();
						Method getter = getGetterMethod(name, parentType);
						if (getter == null) {
							throw new IllegalArgumentException("Invalid property name");
						} else {
							property = new NestedTransientProprtyDefinition(parentProperty, getter);
						}
					} else {
						throw new UnsupportedOperationException("Unhandled parent property type ("
								+ parentProperty.getClass() + "): " + parentProperty);
					}
					return property;
				} else {
					// There are no more parent properties
					PropertyMetadata pm = metadata.getProperty(propertyName);
					if (pm == null) {
						throw new IllegalArgumentException("Invalid property name");
					} else {
						MetadataPropertyDefinition<Object> property = new MetadataPropertyDefinition(pm);
						return property;
					}
				}
			} catch (IllegalArgumentException e) {
				if (parentList == null) {
					throw e;
				} else {
					return parentList.getProperty(propertyName, true);
				}
			}
		} else {
			return null;
		}
	}

	private boolean isNestedProperty(String propertyName) {
		return propertyName.indexOf('.') != -1;
	}

	private Method getGetterMethod(String prop, Class<?> parent) {
		String propertyName = prop.substring(0, 1).toUpperCase() + prop.substring(1);
		try {
			Method m = parent.getMethod("get" + propertyName);
			if (m.getReturnType() != Void.TYPE) {
				return m;
			} else {
				return null;
			}
		} catch (NoSuchMethodException e) {
			return null;
		}
	}

	/**
	 * Removes <code>propertyName</code> from the set of properties. If the
	 * property is contained in the parent list, nothing happens.
	 * 
	 * @param propertyName
	 *            the property name to remove, must not be null.
	 * @return true if a property was removed, false if not (i.e. it did not
	 *         exist in the first place).
	 */
	public boolean removeProperty(String propertyName) {
		assert propertyName != null : "propertyName must not be null";
		PropertyDefinition<E, ?> property = properties.remove(propertyName);
		persistentPropertyNames.remove(propertyName);
		sortablePropertyMap.remove(propertyName);
		allPropertyNames.remove(propertyName);
		nestedPropertyNames.remove(propertyName);
		return property != null;
	}

	/**
	 * Gets the set of all available property names, i.e. the union of
	 * {@link ClassMetadata#getPropertyNames() } and
	 * {@link #getNestedPropertyNames() }. Only nested property names can be
	 * added to or removed from this set.
	 * 
	 * @return an unmodifiable set of property names (never null).
	 */
	public Set<String> getAllAvailablePropertyNames() {
		return Collections.unmodifiableSet(doGetAllAvailablePropertyNames());
	}

	private <E> Set<E> union(Set<E>... sets) {
		HashSet<E> newSet = new HashSet<E>();
		for (Set<E> s : sets) {
			newSet.addAll(s);
		}
		return newSet;
	}

	private <K, V> Map<K, V> union(Map<K, V>... maps) {
		HashMap<K, V> newMap = new HashMap<K, V>();
		for (Map<K, V> s : maps) {
			newMap.putAll(s);
		}
		return newMap;
	}

	@SuppressWarnings("unchecked")
	protected Set<String> doGetAllAvailablePropertyNames() {
		if (parentList == null) {
			return allPropertyNames;
		} else {
			return union(allPropertyNames, parentList.doGetAllAvailablePropertyNames());
		}
	}

	/**
	 * Gets the set of all property names. If no properties have been explicitly
	 * removed using {@link #removeProperty(java.lang.String) }, this set is
	 * equal to {@link #getAllAvailablePropertyNames() }. Otherwise, this set is
	 * a subset of {@link #getAllAvailablePropertyNames()}.
	 * 
	 * @return an unmodifiable set of property names (never null).
	 */
	public Set<String> getPropertyNames() {
		return Collections.unmodifiableSet(doGetPropertyNames());
	}

	@SuppressWarnings("unchecked")
	protected Set<String> doGetPropertyNames() {
		if (parentList == null) {
			return properties.keySet();
		} else {
			return union(properties.keySet(), parentList.doGetPropertyNames());
		}
	}

	/**
	 * Gets the set of persistent property names. This set is a subset of
	 * {@link #getPropertyNames() }.
	 * 
	 * @return an unmodifiable set of property names (never null).
	 */
	public Set<String> getPersistentPropertyNames() {
		return Collections.unmodifiableSet(doGetPersistentPropertyNames());
	}

	@SuppressWarnings("unchecked")
	protected Set<String> doGetPersistentPropertyNames() {
		if (parentList == null) {
			return persistentPropertyNames;
		} else {
			return union(persistentPropertyNames, parentList.doGetPersistentPropertyNames());
		}
	}

	/**
	 * Gets the map of all sortable property names and their corresponding sort
	 * properties. The keys of this map also show up in
	 * {@link #getPropertyNames() } and {@link #getPersistentPropertyNames() }.
	 * 
	 * @return an unmodifiable map from property names (never null) to sort
	 *         properties (not necessarily in the list).
	 */
	public Map<String, String> getSortablePropertyMap() {
		return Collections.unmodifiableMap(doGetSortablePropertyMap());
	}

	@SuppressWarnings("unchecked")
	protected Map<String, String> doGetSortablePropertyMap() {
		if (parentList == null) {
			return sortablePropertyMap;
		} else {
			return union(sortablePropertyMap, parentList.doGetSortablePropertyMap());
		}
	}

	// /**
	// * Gets the set of all nested property names. These names also show up in
	// * {@link #getPropertyNames() } and {@link #getPersistentPropertyNames()
	// }.
	// *
	// * @return an unmodifiable set of property names (never null).
	// */
	// public Set<String> getNestedPropertyNames() {
	// return Collections.unmodifiableSet(doGetNestedPropertyNames());
	// }
	//
	// @SuppressWarnings("unchecked")
	// protected Set<String> doGetNestedPropertyNames() {
	// if (parentList == null) {
	// return nestedPropertyNames;
	// } else {
	// return union(nestedPropertyNames, parentList.doGetNestedPropertyNames());
	// }
	// }

	/**
	 * Gets the type of <code>propertyName</code>. Nested properties are
	 * supported. This method works with property names in the
	 * {@link #getAllAvailablePropertyNames() } set.
	 * 
	 * @param propertyName
	 *            the name of the property (must not be null).
	 * @return the type of the property (never null).
	 * @throws IllegalArgumentException
	 *             if <code>propertyName</code> is illegal.
	 */
	public Class<?> getPropertyType(String propertyName) throws IllegalArgumentException {
		assert propertyName != null : "propertyName must not be null";
		if (!getAllAvailablePropertyNames().contains(propertyName)) {
			throw new IllegalArgumentException("Illegal property name: " + propertyName);
		}
		PropertyDefinition<E, ?> property = getProperty(propertyName);
		if (property != null) {
			return property.getType();
		} else {
			throw new IllegalArgumentException("Illegal property name: " + propertyName);
		}
	}

	/**
	 * Checks if <code>propertyName</code> is writable. Nested properties are
	 * supported. This method works with property names in the
	 * {@link #getAllAvailablePropertyNames() } set.
	 * 
	 * @param propertyName
	 *            the name of the property (must not be null).
	 * @return true if the property is writable, false otherwise.
	 * @throws IllegalArgumentException
	 *             if <code>propertyName</code> is illegal.
	 */
	public boolean isPropertyWritable(String propertyName) throws IllegalArgumentException {
		if (allPropertyNames.contains(propertyName)) {
			return getProperty(propertyName).isWritable();
		} else {
			throw new IllegalArgumentException("Illegal property name: " + propertyName);
		}
	}

	/**
	 * This method can set as read only a property that is writeable, but can
	 * not set a read only property as writeable (untill it was writeable in the
	 * first place)
	 * 
	 * @param propertyName
	 * @param writable
	 */
	public void setPropertyWriteable(String propertyName, boolean writable) {
		assert propertyName != null : "propertyName must not be null";
		if (!getAllAvailablePropertyNames().contains(propertyName)) {
			throw new IllegalArgumentException("Illegal property name: " + propertyName);
		}

		if (isPropertyWritable(propertyName) != writable) {
			getProperty(propertyName).setWriteable(writable);
		}
	}

	/**
	 * Gets the value of <code>propertyName</code> from the instance
	 * <code>object</code>. The property name may be nested, but must be in the
	 * {@link #getAllAvailablePropertyNames() } set.
	 * <p>
	 * When using nested properties and one of the properties in the chain is
	 * null, this method will return null without throwing any exceptions.
	 * 
	 * @param object
	 *            the object that the property value is fetched from (must not
	 *            be null).
	 * @param propertyName
	 *            the property name (must not be null).
	 * @return the property value.
	 * @throws IllegalArgumentException
	 *             if the property name was illegal.
	 */
	public Object getPropertyValue(E object, String propertyName) throws IllegalArgumentException {
		assert propertyName != null : "propertyName must not be null";
		assert object != null : "object must not be null";
		if (!getAllAvailablePropertyNames().contains(propertyName)) {
			throw new IllegalArgumentException("Illegal property name: " + propertyName);
		}
		return getProperty(propertyName).getPropertyValue(object);
	}

	/**
	 * Sets the value of <code>propertyName</code> to <code>propertyValue</code>
	 * . The property name may be nested, but must be in the
	 * {@link #getAllAvailablePropertyNames() } set.
	 * 
	 * @param object
	 *            the object to which the property is set (must not be null).
	 * @param propertyName
	 *            the property name (must not be null).
	 * @param propertyValue
	 *            the property value to set.
	 * @throws IllegalArgumentException
	 *             if the property name was illegal.
	 * @throws IllegalStateException
	 *             if one of the properties in the chain of nested properties
	 *             was null.
	 */
	public void setPropertyValue(E object, String propertyName, Object propertyValue)
			throws IllegalArgumentException, IllegalStateException {
		assert propertyName != null : "propertyName must not be null";
		assert object != null : "object must not be null";
		if (!getAllAvailablePropertyNames().contains(propertyName)) {
			throw new IllegalArgumentException("Illegal property name: " + propertyName);
		}
		getProperty(propertyName).setPropertyValue(object, propertyValue);
	}

	public PropertyKind getPropertyKind(String propertyName) {
		assert propertyName != null : "propertyName must not be null";
		if (!getAllAvailablePropertyNames().contains(propertyName)) {
			throw new IllegalArgumentException("Illegal property name: " + propertyName);
		}
		PropertyDefinition<E, Object> property = getProperty(propertyName);
		return property.getKind();
	}

	/**
	 * Finds out whether a given property or any property in a nested "path" is
	 * lazy loaded.
	 * 
	 * @param propertyName
	 *            the name of the property to inspect
	 * @return true if the property is loaded lazily
	 */
	public boolean isPropertyLazyLoaded(String propertyName) {
		if (isNestedProperty(propertyName)) {
			int dotIx = propertyName.indexOf('.');
			if (isPropertyLazyLoaded(propertyName.substring(dotIx + 1))) {
				return true;
			}
			return getPropertyFetchType(propertyName.substring(0, dotIx)) == FetchType.LAZY;
		}

		return getPropertyFetchType(propertyName) == FetchType.LAZY;
	}

	/**
	 * Finds the fetch type for the given property.
	 * 
	 * @param propertyName
	 *            the name of the property
	 * @return the {@link FetchType} or null if not applicable (e.g. not a
	 *         reference to another table on the database level)
	 */
	private FetchType getPropertyFetchType(String propertyName) {
		PropertyMetadata pm = metadata.getProperty(propertyName);
		if (pm != null) {
			if (pm.getAnnotation(Basic.class) != null) {
				return pm.getAnnotation(Basic.class).fetch();
			} else if (pm.getAnnotation(ElementCollection.class) != null) {
				return pm.getAnnotation(ElementCollection.class).fetch();
			} else if (pm.getAnnotation(ManyToMany.class) != null) {
				return pm.getAnnotation(ManyToMany.class).fetch();
			} else if (pm.getAnnotation(OneToMany.class) != null) {
				return pm.getAnnotation(OneToMany.class).fetch();
			} else if (pm.getAnnotation(ManyToOne.class) != null) {
				return pm.getAnnotation(ManyToOne.class).fetch();
			} else if (pm.getAnnotation(OneToOne.class) != null) {
				return pm.getAnnotation(OneToOne.class).fetch();
			}
		}
		return null;
	}

	public Set<String> getNestedPropertyNames() {
		// TODO Auto-generated method stub
		return nestedPropertyNames;
	}
}
