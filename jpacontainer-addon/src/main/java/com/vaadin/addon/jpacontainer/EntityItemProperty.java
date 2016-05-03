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

import com.vaadin.data.Property;
import com.vaadin.data.util.converter.Converter.ConversionException;

/**
 * Interface defining the Properties that are contained in a {@link EntityItem}.
 * 
 * @author Petter Holmström (Vaadin Ltd)
 * @since 1.0
 */
public interface EntityItemProperty<E, T> extends Property<T>,
        Property.ValueChangeNotifier {

    /**
     * Gets the EntityItem that owns this property.
     * 
     * @return the item (never null).
     */
    public EntityItem<E> getItem();
    
    /**
     * Gets the property id of this property.
     * 
     * @return the identifier of the property
     */
    public String getPropertyId();

    /**
     * Fires value change event for this property
     */
    void fireValueChangeEvent();

	boolean isModified();

	/**
	 * <b>Note! This method assumes that write through is OFF!</b>
	 * <p>
	 * Sets the real value to the cached value. If read through is on, the
	 * listeners are also notified as the value will appear to have changed to
	 * them.
	 * <p>
	 * If the property is read only, nothing happens.
	 * 
	 * @throws ConversionException
	 *             if the real value could not be set for some reason.
	 */
	public void commit();

	/**
	 * <b>Note! This method assumes that write through is OFF!</b>
	 * <p>
	 * Replaces the cached value with the real value. If read through is off,
	 * the listeners are also notified as the value will appear to have changed
	 * to them.
	 */
	public void discard();

	void setWriteThrough(boolean writeThrough);
	
}
